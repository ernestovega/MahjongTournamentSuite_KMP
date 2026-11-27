package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * The colors of the current [MtsTheme] with the green of the suite replaced by [brand], the color of the main organizer
 * of a tournament. The screens that the venue projects, the Timer and the standalone ranking, use it (Ernesto,
 * 2026-11-27). Null keeps the theme as it is.
 *
 * Any color can come from an organization, so the roles keep their contrast: the text on a fill is white or almost
 * black, whichever reads better, and the brand as text (links, text buttons) is darkened in the light theme and lightened
 * in the dark theme until it reads at 4.5:1 on the page and the cards. The pink focus ring, the error red and the grey
 * containers stay.
 */
@Composable
fun MtsBrandTheme(brand: Color?, content: @Composable () -> Unit) {
    if (brand == null) {
        content()
        return
    }
    val scheme = MaterialTheme.colorScheme
    val colors = MtsTheme.colors
    val branded = remember(scheme, colors, brand) { brandedColors(scheme, colors, brand) }
    MaterialTheme(colorScheme = branded.first, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalMtsColors provides branded.second, content = content)
    }
}

/** The color scheme and the domain colors of [brandedColors]. Internal so the tests can check the contrast. */
internal fun brandedColors(scheme: ColorScheme, colors: MtsColors, brand: Color): Pair<ColorScheme, MtsColors> {
    val dark = scheme.background.luminance() < 0.5f
    val opaque = brand.copy(alpha = 1f)
    val onBrand = textOn(opaque)
    // The brand as text: it must read on the page and on the cards.
    val text = readableOn(opaque, listOf(scheme.background, scheme.surface, scheme.surfaceContainerHighest), dark)
    val branded = scheme.copy(
        primary = opaque,
        onPrimary = onBrand,
        primaryContainer = opaque,
        onPrimaryContainer = onBrand,
        secondary = text,
        onSecondary = textOn(text),
        inversePrimary = readableOn(opaque, listOf(scheme.inverseSurface), !dark),
        surfaceTint = opaque,
    )
    val brandedColors = colors.copy(
        link = text,
        header = opaque,
        onHeader = onBrand,
        onHeaderMuted = onBrand.copy(alpha = 0.85f),
        scoreOk = opaque,
        selfPick = opaque,
    )
    return branded to brandedColors
}

/** White or almost black, whichever has more contrast on [fill]. */
internal fun textOn(fill: Color): Color =
    if (contrast(Color.White, fill) >= contrast(MtsFixedColors.inkOnLight, fill)) Color.White else MtsFixedColors.inkOnLight

/** [color], darkened (light theme) or lightened (dark theme) step by step until it reads at 4.5:1 on all [backgrounds]. */
internal fun readableOn(color: Color, backgrounds: List<Color>, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    var step = 0
    var candidate = color
    while (backgrounds.any { contrast(candidate, it) < MIN_TEXT_CONTRAST } && step < 20) {
        step++
        candidate = lerp(color, target, step / 20f)
    }
    return candidate
}

/** The WCAG contrast ratio of two opaque colors. */
internal fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private const val MIN_TEXT_CONTRAST = 4.5
