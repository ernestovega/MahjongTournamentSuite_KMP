package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.domain.model.Country

/** What a Google Maps link tells about a place. A value is null when the link does not hold it. */
data class MapLinkPlace(
    val name: String?,
    val address: String?,
    val city: String?,
    /** Two-letter country code from the country list. */
    val countryCode: String?,
)

private val ShortLinkHosts = listOf("goo.gl", "maps.app.goo.gl", "g.co")
private val CoordinatesPattern = Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$")
private val PlacePathPattern = Regex("^/maps/(?:place|search)/([^/]+)")

/** Country names in the language of the country, for links that Google wrote in that language. */
private val CountryAliases = mapOf(
    "españa" to "ES", "spanien" to "ES", "espagne" to "ES", "spagna" to "ES", "spain" to "ES",
    "deutschland" to "DE", "alemania" to "DE", "allemagne" to "DE", "germania" to "DE",
    "italia" to "IT", "italien" to "IT", "italie" to "IT",
    "france" to "FR", "francia" to "FR", "frankreich" to "FR",
    "österreich" to "AT", "austria" to "AT", "autriche" to "AT",
    "belgique" to "BE", "belgië" to "BE", "belgien" to "BE", "bélgica" to "BE", "belgio" to "BE",
    "polska" to "PL", "polonia" to "PL", "polen" to "PL", "pologne" to "PL",
    "portugal" to "PT", "portogallo" to "PT",
    "schweiz" to "CH", "suisse" to "CH", "svizzera" to "CH", "suiza" to "CH", "switzerland" to "CH",
    "nederland" to "NL", "países bajos" to "NL", "niederlande" to "NL", "pays-bas" to "NL",
    "sverige" to "SE", "suecia" to "SE", "schweden" to "SE",
    "norge" to "NO", "noruega" to "NO", "norwegen" to "NO",
    "danmark" to "DK", "dinamarca" to "DK", "dänemark" to "DK",
    "suomi" to "FI", "finlandia" to "FI", "finnland" to "FI",
    "česko" to "CZ", "česká republika" to "CZ", "chequia" to "CZ", "tschechien" to "CZ", "czech republic" to "CZ",
    "magyarország" to "HU", "hungría" to "HU", "ungarn" to "HU",
    "românia" to "RO", "rumanía" to "RO", "rumänien" to "RO",
    "éire" to "IE", "irlanda" to "IE", "irland" to "IE",
    "reino unido" to "GB", "vereinigtes königreich" to "GB", "royaume-uni" to "GB", "uk" to "GB",
    "россия" to "RU", "україна" to "UA", "беларусь" to "BY",
)

private fun hostOf(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    return trimmed.substring("https://".length).substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        .takeIf { it.isNotEmpty() }
}

/** True for a short link such as maps.app.goo.gl. The server must follow it to get the full link. */
fun isShortMapLink(url: String): Boolean {
    val host = hostOf(url) ?: return false
    return ShortLinkHosts.any { host == it || host.endsWith(".$it") }
}

/** True when the text is an https link that looks like a Google Maps link, short or full. */
fun isGoogleMapsLink(url: String): Boolean {
    val host = hostOf(url) ?: return false
    if (isShortMapLink(url)) return true
    val isGoogleHost = host.matches(Regex("^(www\\.|maps\\.)?google\\.[a-z.]+$"))
    if (!isGoogleHost) return false
    return host.startsWith("maps.") || url.substringAfter("://").substringAfter('/', "").startsWith("maps")
}

/**
 * Reads the place from a full Google Maps link. The link holds a text such as
 * "Hotel+Name,+Street+1,+28001+Madrid,+Spain". Returns null when the link has no text about a place.
 */
fun parseMapLink(url: String, countries: List<Country>): MapLinkPlace? {
    if (!isGoogleMapsLink(url) || isShortMapLink(url)) return null
    val afterHost = url.trim().substring("https://".length).substringAfter('/', "")
    val pathAndQuery = "/$afterHost"
    val path = pathAndQuery.substringBefore('?').substringBefore('#')
    val query = pathAndQuery.substringAfter('?', "").substringBefore('#')

    val rawText = PlacePathPattern.find(path)?.groupValues?.get(1)
        ?: query.split('&').firstNotNullOfOrNull { parameter ->
            val key = parameter.substringBefore('=')
            if (key == "query" || key == "q") parameter.substringAfter('=', "") else null
        }
        ?: return null
    val text = decodeLinkText(rawText).trim()
    if (text.isEmpty() || text.startsWith("@") || CoordinatesPattern.matches(text)) return null

    var parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null

    val countryCode = parts.last().let { last -> countryCodeOf(last, countries) }
    if (countryCode != null && parts.size > 1) parts = parts.dropLast(1)

    // A place link starts with the name. A link of a plain address starts with the street, which has a number.
    val firstHasNumber = parts.first().any(Char::isDigit)
    val name = parts.first().takeIf { !firstHasNumber }
    val addressParts = if (name != null) parts.drop(1) else parts
    val address = addressParts.joinToString(", ").takeIf { it.isNotEmpty() }
    val city = addressParts.lastOrNull()?.let(::cityOf)

    return MapLinkPlace(name = name, address = address, city = city, countryCode = countryCode)
}

private fun countryCodeOf(text: String, countries: List<Country>): String? {
    val normalized = text.trim().lowercase()
    val byList = countries.firstOrNull { it.name.trim().lowercase() == normalized }?.code
    val code = byList ?: CountryAliases[normalized]
    return code?.uppercase()?.takeIf { found -> countries.isEmpty() || countries.any { it.code.uppercase() == found } }
}

/** The city is the last address part without the words that hold digits, which are postal codes. */
private fun cityOf(part: String): String? =
    part.split(' ').filter { word -> word.isNotBlank() && word.none(Char::isDigit) }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }

private fun decodeLinkText(raw: String): String {
    val bytes = ArrayList<Byte>(raw.length)
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        val hex = if (char == '%' && index + 2 < raw.length) {
            raw.substring(index + 1, index + 3).takeIf { pair -> pair.all(::isHexDigit) }?.toInt(16)
        } else {
            null
        }
        when {
            hex != null -> {
                bytes.add(hex.toByte())
                index += 3
            }

            char == '+' -> {
                bytes.add(' '.code.toByte())
                index += 1
            }

            else -> {
                bytes.addAll(char.toString().encodeToByteArray().toList())
                index += 1
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
