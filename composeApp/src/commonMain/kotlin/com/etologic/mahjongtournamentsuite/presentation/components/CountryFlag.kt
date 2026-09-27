package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
expect fun CountryFlag(
    code: String,
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    contentDescription: String? = null,
)

internal fun normalizedFlagCountryCode(code: String): String? {
    val normalized = code.trim().uppercase()
    return normalized.takeIf {
        it != "EU" && it.length == 2 && it.all { character -> character in 'A'..'Z' }
    }
}

@Composable
internal fun TextCountryFlag(
    code: String,
    modifier: Modifier,
    width: Dp,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    Text(
        text = normalizedFlagCountryCode(code)?.let(::countryFlagSymbol) ?: "🌐",
        modifier = modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        style = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Default,
            fontSize = with(density) { (width * 0.75f).toSp() },
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private fun countryFlagSymbol(code: String): String =
    code.map { regionalIndicator(it) }.joinToString("")

private fun regionalIndicator(letter: Char): String {
    val codePoint = 0x1F1E6 + (letter.code - 'A'.code)
    val offset = codePoint - 0x10000
    val high = ((offset / 0x400) + 0xD800).toChar()
    val low = ((offset % 0x400) + 0xDC00).toChar()
    return charArrayOf(high, low).concatToString()
}
