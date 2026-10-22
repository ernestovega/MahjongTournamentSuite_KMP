package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.flag_placeholder

/** A flag. Hovering it (or a long press) shows the name of the country. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CountryFlag(
    code: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val normalizedCode = normalizedFlagCountryCode(code)
    val countryName = normalizedCode?.let { countryNames[it] ?: it }
    if (countryName == null) {
        FlagBox(code, modifier, width, contentDescription)
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { AppPlainTooltip { Text(countryName) } },
        state = rememberTooltipState(),
    ) {
        FlagBox(code, modifier, width, contentDescription)
    }
}

@Composable
private fun FlagBox(
    code: String,
    modifier: Modifier,
    width: Dp,
    contentDescription: String?,
) {
    val normalizedCode = normalizedFlagCountryCode(code)
    Box(
        modifier = modifier.width(width).aspectRatio(4f / 3f),
        contentAlignment = Alignment.Center,
    ) {
        val resource = normalizedCode
            ?.takeUnless { it == "EU" }
            ?.let(::flagResourceFor)
        when {
            resource != null -> Image(
                painter = painterResource(resource),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // Twemoji flags are drawn in a square. The flag fills the width and the empty top and bottom are cut.
                contentScale = ContentScale.FillWidth,
            )
            normalizedCode != null && normalizedCode != "EU" -> Text(
                text = normalizedCode,
                modifier = Modifier.clearAndSetSemantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
            )
            else -> Image(
                painter = painterResource(Res.drawable.flag_placeholder),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * The flag of a player, in a box of fixed [width]. A player without country leaves the box empty,
 * so the names that follow it stay aligned.
 */
@Composable
fun PlayerFlag(country: String?, modifier: Modifier = Modifier, width: Dp = 24.dp) {
    Box(modifier.width(width), contentAlignment = Alignment.Center) {
        if (!country.isNullOrBlank()) CountryFlag(code = country, width = width - 4.dp, contentDescription = country)
    }
}

internal fun normalizedFlagCountryCode(code: String): String? {
    val normalized = code.trim().uppercase()
    if (normalized == "EU") return normalized

    if (normalized.length == 2 && normalized.all { character -> character in 'A'..'Z' }) {
        return normalized
    }

    return emaCountryCodeToIso2[normalized]
}

private val emaCountryCodeToIso2 = mapOf(
    "AUT" to "AT",
    "BEL" to "BE",
    "BLR" to "BY",
    "CHE" to "CH",
    "CZE" to "CZ",
    "DEU" to "DE",
    "DEN" to "DK",
    "DNK" to "DK",
    "ESP" to "ES",
    "FIN" to "FI",
    "FRA" to "FR",
    "GBR" to "GB",
    "GER" to "DE",
    "HUN" to "HU",
    "IRL" to "IE",
    "ITA" to "IT",
    "LAT" to "LV",
    "NED" to "NL",
    "NLD" to "NL",
    "NOR" to "NO",
    "POL" to "PL",
    "POR" to "PT",
    "PRT" to "PT",
    "ROU" to "RO",
    "RUS" to "RU",
    "SUI" to "CH",
    "SVK" to "SK",
    "SWE" to "SE",
    "UKR" to "UA",
)

/** English names of the countries that appear in the EMA ranking, by two letter code. */
private val countryNames = mapOf(
    "AD" to "Andorra", "AL" to "Albania", "AM" to "Armenia", "AR" to "Argentina", "AT" to "Austria",
    "AU" to "Australia", "AZ" to "Azerbaijan", "BA" to "Bosnia and Herzegovina", "BE" to "Belgium",
    "BG" to "Bulgaria", "BR" to "Brazil", "BY" to "Belarus", "CA" to "Canada", "CH" to "Switzerland",
    "CL" to "Chile", "CN" to "China", "CY" to "Cyprus", "CZ" to "Czechia", "DE" to "Germany",
    "DK" to "Denmark", "EE" to "Estonia", "ES" to "Spain", "EU" to "European Union", "FI" to "Finland",
    "FR" to "France", "GB" to "United Kingdom", "GE" to "Georgia", "GR" to "Greece", "HK" to "Hong Kong",
    "HR" to "Croatia", "HU" to "Hungary", "IE" to "Ireland", "IL" to "Israel", "IS" to "Iceland",
    "IT" to "Italy", "JP" to "Japan", "KR" to "South Korea", "KZ" to "Kazakhstan", "LI" to "Liechtenstein",
    "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia", "MA" to "Morocco", "MC" to "Monaco",
    "MD" to "Moldova", "ME" to "Montenegro", "MK" to "North Macedonia", "MT" to "Malta", "MX" to "Mexico",
    "NL" to "Netherlands", "NO" to "Norway", "NZ" to "New Zealand", "PL" to "Poland", "PT" to "Portugal",
    "RO" to "Romania", "RS" to "Serbia", "RU" to "Russia", "SE" to "Sweden", "SG" to "Singapore",
    "SI" to "Slovenia", "SK" to "Slovakia", "SM" to "San Marino", "TH" to "Thailand", "TR" to "Turkey",
    "TW" to "Taiwan", "UA" to "Ukraine", "US" to "United States", "UZ" to "Uzbekistan", "ZA" to "South Africa",
)
