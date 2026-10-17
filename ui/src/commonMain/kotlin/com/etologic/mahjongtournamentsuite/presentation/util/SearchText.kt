package com.etologic.mahjongtournamentsuite.presentation.util

/** Pairs of base letters and the accented letters that fold to them. */
private val AccentFolds: Map<Char, String> = buildMap {
    mapOf(
        'a' to "àáâãäåāăą",
        'c' to "çćĉċč",
        'd' to "ďđð",
        'e' to "èéêëēĕėęě",
        'g' to "ĝğġģ",
        'h' to "ĥħ",
        'i' to "ìíîïĩīĭįı",
        'j' to "ĵ",
        'k' to "ķ",
        'l' to "ĺļľŀł",
        'n' to "ñńņň",
        'o' to "òóôõöøōŏő",
        'r' to "ŕŗř",
        's' to "śŝşš",
        't' to "ţťŧþ",
        'u' to "ùúûüũūŭůűų",
        'w' to "ŵ",
        'y' to "ýÿŷ",
        'z' to "źżž",
    ).forEach { (base, accented) -> accented.forEach { put(it, base.toString()) } }
    put('æ', "ae")
    put('œ', "oe")
    put('ß', "ss")
}

/**
 * The search form of a text: lower case, accents folded to their base letters,
 * and without spaces and punctuation. Two texts match a search when the form of the query is inside the form of the text.
 */
fun normalizeSearchText(value: String): String = buildString(value.length) {
    value.lowercase().forEach { character ->
        val folded = AccentFolds[character]
        when {
            folded != null -> append(folded)
            character.isLetterOrDigit() -> append(character)
        }
    }
}

/** True when this text contains [query], whatever the case, the accents, the spaces and the punctuation. A blank query matches all. */
fun String.matchesSearch(query: String): Boolean = normalizeSearchText(this).contains(normalizeSearchText(query))
