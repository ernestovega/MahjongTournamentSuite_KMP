package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import mahjongtournamentsuite.composeapp.generated.resources.Res
import mahjongtournamentsuite.composeapp.generated.resources.flag_placeholder

@Composable
fun CountryFlag(
    code: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    contentDescription: String? = null,
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
                contentScale = ContentScale.Fit,
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
