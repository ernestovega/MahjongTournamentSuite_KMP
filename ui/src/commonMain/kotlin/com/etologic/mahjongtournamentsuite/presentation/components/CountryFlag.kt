package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.flag_placeholder
import mahjongtournamentsuite.ui.generated.resources.icon_globe
import mahjongtournamentsuite.ui.generated.resources.no_country
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A flag. Hovering it (or a long press) shows the name of the country. A player without country (an empty code, or the
 * EMA pseudo-country EU) is a guest: the flag is a globe 🌐 and its name is "No country".
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CountryFlag(
    code: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val normalizedCode = normalizedFlagCountryCode(code)
    val countryName = when {
        isGuestCountryCode(code) -> stringResource(Res.string.no_country)
        normalizedCode != null -> countryDisplayName(normalizedCode, fallback = "").ifEmpty { null }
        else -> null
    }
    if (countryName == null) {
        FlagBox(code, modifier, width, contentDescription)
        return
    }
    AppTooltipBox(countryName) {
        FlagBox(code, modifier, width, contentDescription ?: countryName)
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
        val resource = normalizedCode?.let(::flagResourceFor)
        when {
            isGuestCountryCode(code) -> Image(
                painter = painterResource(Res.drawable.icon_globe),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // The globe is round: it fits the height of the box and is not cut.
                contentScale = ContentScale.Fit,
            )
            resource != null -> Image(
                painter = painterResource(resource),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // Twemoji flags are drawn in a square. The flag fills the width and the empty top and bottom are cut.
                contentScale = ContentScale.FillWidth,
            )
            normalizedCode != null -> Text(
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
 * The flag of a player, in a box of fixed [width], so the names that follow it stay aligned. A player without country
 * shows the globe of the guests. Only a null [country] (nobody) leaves the box empty.
 */
@Composable
fun PlayerFlag(country: String?, modifier: Modifier = Modifier, width: Dp = 24.dp) {
    Box(modifier.width(width), contentAlignment = Alignment.Center) {
        if (country != null) CountryFlag(code = country, width = width - 4.dp)
    }
}

/**
 * The flag of a player with the number of the player in the tournament on its right, as "#11". The number goes beside
 * the flag and not before the name, so the name keeps its space.
 */
@Composable
fun PlayerFlagAndNumber(
    country: String?,
    number: Int?,
    modifier: Modifier = Modifier,
    flagWidth: Dp = 22.dp,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PlayerFlag(country, width = flagWidth)
        if (number != null) {
            Text(
                "#$number",
                style = style,
                fontWeight = FontWeight.Bold,
                // The color of the text around it, a little softer, so it reads on a card and on a colored choice.
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

/** True for a player without country: an empty code, or the EMA pseudo-country of Europe (EU, EUR). */
fun isGuestCountryCode(code: String?): Boolean {
    val normalized = code?.trim()?.uppercase().orEmpty()
    return normalized.isEmpty() || normalized == "EU" || normalized == "EUR"
}

/** The two-letter code of a country from its two-letter or three-letter (EMA, IOC or ISO) code. */
fun normalizedFlagCountryCode(code: String): String? {
    val normalized = code.trim().uppercase()
    if (normalized.length == 2 && normalized.all { character -> character in 'A'..'Z' }) {
        return normalized
    }
    return threeLetterCountryCodeToIso2[normalized]
}

/** Names of the countries by two letter code. */
private val countryNames: Map<String, StringResource> =
    WorldCountries.associate { it.code to it.name } + ("EU" to EuropeanUnionName)

/** The name of a country in the language of the app. It gives [fallback] for a code without a name. */
@Composable
fun countryDisplayName(code: String, fallback: String = code): String = normalizedFlagCountryCode(code)?.let(countryNames::get)?.let { stringResource(it) } ?: fallback

/** A function that gives the name of a country by its two letter code. It uses [fallback] for a code without a name. */
@Composable
fun rememberCountryNames(): (code: String, fallback: String) -> String {
    val resolved = countryNames.mapValues { stringResource(it.value) }
    return remember(resolved) { { code, fallback -> normalizedFlagCountryCode(code)?.let(resolved::get) ?: fallback } }
}
