package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.country_ad
import mahjongtournamentsuite.ui.generated.resources.country_al
import mahjongtournamentsuite.ui.generated.resources.country_am
import mahjongtournamentsuite.ui.generated.resources.country_ar
import mahjongtournamentsuite.ui.generated.resources.country_at
import mahjongtournamentsuite.ui.generated.resources.country_au
import mahjongtournamentsuite.ui.generated.resources.country_az
import mahjongtournamentsuite.ui.generated.resources.country_ba
import mahjongtournamentsuite.ui.generated.resources.country_be
import mahjongtournamentsuite.ui.generated.resources.country_bg
import mahjongtournamentsuite.ui.generated.resources.country_br
import mahjongtournamentsuite.ui.generated.resources.country_by
import mahjongtournamentsuite.ui.generated.resources.country_ca
import mahjongtournamentsuite.ui.generated.resources.country_ch
import mahjongtournamentsuite.ui.generated.resources.country_cl
import mahjongtournamentsuite.ui.generated.resources.country_cn
import mahjongtournamentsuite.ui.generated.resources.country_cy
import mahjongtournamentsuite.ui.generated.resources.country_cz
import mahjongtournamentsuite.ui.generated.resources.country_de
import mahjongtournamentsuite.ui.generated.resources.country_dk
import mahjongtournamentsuite.ui.generated.resources.country_ee
import mahjongtournamentsuite.ui.generated.resources.country_es
import mahjongtournamentsuite.ui.generated.resources.country_eu
import mahjongtournamentsuite.ui.generated.resources.country_fi
import mahjongtournamentsuite.ui.generated.resources.country_fr
import mahjongtournamentsuite.ui.generated.resources.country_gb
import mahjongtournamentsuite.ui.generated.resources.country_ge
import mahjongtournamentsuite.ui.generated.resources.country_gr
import mahjongtournamentsuite.ui.generated.resources.country_hk
import mahjongtournamentsuite.ui.generated.resources.country_hr
import mahjongtournamentsuite.ui.generated.resources.country_hu
import mahjongtournamentsuite.ui.generated.resources.country_ie
import mahjongtournamentsuite.ui.generated.resources.country_il
import mahjongtournamentsuite.ui.generated.resources.country_is
import mahjongtournamentsuite.ui.generated.resources.country_it
import mahjongtournamentsuite.ui.generated.resources.country_jp
import mahjongtournamentsuite.ui.generated.resources.country_kr
import mahjongtournamentsuite.ui.generated.resources.country_kz
import mahjongtournamentsuite.ui.generated.resources.country_li
import mahjongtournamentsuite.ui.generated.resources.country_lt
import mahjongtournamentsuite.ui.generated.resources.country_lu
import mahjongtournamentsuite.ui.generated.resources.country_lv
import mahjongtournamentsuite.ui.generated.resources.country_ma
import mahjongtournamentsuite.ui.generated.resources.country_mc
import mahjongtournamentsuite.ui.generated.resources.country_md
import mahjongtournamentsuite.ui.generated.resources.country_me
import mahjongtournamentsuite.ui.generated.resources.country_mk
import mahjongtournamentsuite.ui.generated.resources.country_mt
import mahjongtournamentsuite.ui.generated.resources.country_mx
import mahjongtournamentsuite.ui.generated.resources.country_nl
import mahjongtournamentsuite.ui.generated.resources.country_no
import mahjongtournamentsuite.ui.generated.resources.country_nz
import mahjongtournamentsuite.ui.generated.resources.country_pl
import mahjongtournamentsuite.ui.generated.resources.country_pt
import mahjongtournamentsuite.ui.generated.resources.country_ro
import mahjongtournamentsuite.ui.generated.resources.country_rs
import mahjongtournamentsuite.ui.generated.resources.country_ru
import mahjongtournamentsuite.ui.generated.resources.country_se
import mahjongtournamentsuite.ui.generated.resources.country_sg
import mahjongtournamentsuite.ui.generated.resources.country_si
import mahjongtournamentsuite.ui.generated.resources.country_sk
import mahjongtournamentsuite.ui.generated.resources.country_sm
import mahjongtournamentsuite.ui.generated.resources.country_th
import mahjongtournamentsuite.ui.generated.resources.country_tr
import mahjongtournamentsuite.ui.generated.resources.country_tw
import mahjongtournamentsuite.ui.generated.resources.country_ua
import mahjongtournamentsuite.ui.generated.resources.country_us
import mahjongtournamentsuite.ui.generated.resources.country_uz
import mahjongtournamentsuite.ui.generated.resources.country_za
import mahjongtournamentsuite.ui.generated.resources.flag_placeholder
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val countryName = normalizedCode?.let { countryDisplayName(it) }
    if (countryName == null) {
        FlagBox(code, modifier, width, contentDescription)
        return
    }
    TooltipBox(
        positionProvider = rememberAppTooltipPositionProvider(),
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

/** Names of the countries that appear in the EMA ranking, by two letter code. */
private val countryNames: Map<String, StringResource> = mapOf(
    "AD" to Res.string.country_ad,
    "AL" to Res.string.country_al,
    "AM" to Res.string.country_am,
    "AR" to Res.string.country_ar,
    "AT" to Res.string.country_at,
    "AU" to Res.string.country_au,
    "AZ" to Res.string.country_az,
    "BA" to Res.string.country_ba,
    "BE" to Res.string.country_be,
    "BG" to Res.string.country_bg,
    "BR" to Res.string.country_br,
    "BY" to Res.string.country_by,
    "CA" to Res.string.country_ca,
    "CH" to Res.string.country_ch,
    "CL" to Res.string.country_cl,
    "CN" to Res.string.country_cn,
    "CY" to Res.string.country_cy,
    "CZ" to Res.string.country_cz,
    "DE" to Res.string.country_de,
    "DK" to Res.string.country_dk,
    "EE" to Res.string.country_ee,
    "ES" to Res.string.country_es,
    "EU" to Res.string.country_eu,
    "FI" to Res.string.country_fi,
    "FR" to Res.string.country_fr,
    "GB" to Res.string.country_gb,
    "GE" to Res.string.country_ge,
    "GR" to Res.string.country_gr,
    "HK" to Res.string.country_hk,
    "HR" to Res.string.country_hr,
    "HU" to Res.string.country_hu,
    "IE" to Res.string.country_ie,
    "IL" to Res.string.country_il,
    "IS" to Res.string.country_is,
    "IT" to Res.string.country_it,
    "JP" to Res.string.country_jp,
    "KR" to Res.string.country_kr,
    "KZ" to Res.string.country_kz,
    "LI" to Res.string.country_li,
    "LT" to Res.string.country_lt,
    "LU" to Res.string.country_lu,
    "LV" to Res.string.country_lv,
    "MA" to Res.string.country_ma,
    "MC" to Res.string.country_mc,
    "MD" to Res.string.country_md,
    "ME" to Res.string.country_me,
    "MK" to Res.string.country_mk,
    "MT" to Res.string.country_mt,
    "MX" to Res.string.country_mx,
    "NL" to Res.string.country_nl,
    "NO" to Res.string.country_no,
    "NZ" to Res.string.country_nz,
    "PL" to Res.string.country_pl,
    "PT" to Res.string.country_pt,
    "RO" to Res.string.country_ro,
    "RS" to Res.string.country_rs,
    "RU" to Res.string.country_ru,
    "SE" to Res.string.country_se,
    "SG" to Res.string.country_sg,
    "SI" to Res.string.country_si,
    "SK" to Res.string.country_sk,
    "SM" to Res.string.country_sm,
    "TH" to Res.string.country_th,
    "TR" to Res.string.country_tr,
    "TW" to Res.string.country_tw,
    "UA" to Res.string.country_ua,
    "US" to Res.string.country_us,
    "UZ" to Res.string.country_uz,
    "ZA" to Res.string.country_za,
)

/** The name of a country in the language of the app. It gives [fallback] for a code without a name. */
@Composable
fun countryDisplayName(code: String, fallback: String = code): String =
    countryNames[code.trim().uppercase()]?.let { stringResource(it) } ?: fallback

/** A function that gives the name of a country by its two letter code. It uses [fallback] for a code without a name. */
@Composable
fun rememberCountryNames(): (code: String, fallback: String) -> String {
    val resolved = countryNames.mapValues { stringResource(it.value) }
    return remember(resolved) { { code, fallback -> resolved[code.trim().uppercase()] ?: fallback } }
}
