package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The contrast of the colors of the theme, light and dark (WCAG 2.1): text needs 4.5:1 on its background, and the
 * borders of fields 3:1. White text on a color (buttons, chips, the error red) is Ernesto's choice even under 4.5:1,
 * so it is not checked. The green of the brand (primary) is a fill: as text it is [MtsColors.link] (Ernesto, 25-nov).
 */
@OptIn(ExperimentalTestApi::class)
class MtsContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val light = max(a.luminance(), b.luminance())
        val dark = min(a.luminance(), b.luminance())
        return (light + 0.05) / (dark + 0.05)
    }

    private fun themeOf(dark: Boolean): Pair<ColorScheme, MtsColors> {
        var theme: Pair<ColorScheme, MtsColors>? = null
        runComposeUiTest { setContent { MtsTheme(useDarkTheme = dark) { theme = MaterialTheme.colorScheme to MtsTheme.colors } } }
        return theme!!
    }

    /** Name of the pair to its foreground, background and the ratio that it needs. */
    private fun pairs(s: ColorScheme, c: MtsColors) = mapOf(
        "onBackground on background" to Triple(s.onBackground, s.background, 4.5),
        "onSurface on surface" to Triple(s.onSurface, s.surface, 4.5),
        "onSurface on card" to Triple(s.onSurface, s.surfaceContainerHighest, 4.5),
        "onSurfaceVariant on surface" to Triple(s.onSurfaceVariant, s.surface, 4.5),
        "onSurfaceVariant on card" to Triple(s.onSurfaceVariant, s.surfaceContainerHighest, 4.5),
        "onSurfaceVariant on surfaceVariant" to Triple(s.onSurfaceVariant, s.surfaceVariant, 4.5),
        "onSecondaryContainer on secondaryContainer" to Triple(s.onSecondaryContainer, s.secondaryContainer, 4.5),
        "onTertiaryContainer on tertiaryContainer" to Triple(s.onTertiaryContainer, s.tertiaryContainer, 4.5),
        "inverseOnSurface on inverseSurface" to Triple(s.inverseOnSurface, s.inverseSurface, 4.5),
        "link on surface" to Triple(c.link, s.surface, 4.5),
        "link on card" to Triple(c.link, s.surfaceContainerHighest, 4.5),
        "gain on card" to Triple(c.gain, s.surfaceContainerHighest, 4.5),
        "error text on surface" to Triple(s.error, s.surface, 4.5),
        "error text on card" to Triple(s.error, s.surfaceContainerHighest, 4.5),
        "outline on surface" to Triple(s.outline, s.surface, 3.0),
        "outline on card" to Triple(s.outline, s.surfaceContainerHighest, 3.0),
    )

    private fun gaps(dark: Boolean): Map<String, Double> = themeOf(dark).let { (scheme, colors) -> pairs(scheme, colors) }
        .filter { (_, pair) -> ratio(pair.first, pair.second) < pair.third }
        .mapValues { (_, pair) -> (ratio(pair.first, pair.second) * 100).roundToInt() / 100.0 }

    @Test
    fun theLightThemeHasNoGaps() {
        assertEquals(emptyMap(), gaps(dark = false))
    }

    @Test
    fun theDarkThemeHasNoGaps() {
        assertEquals(emptyMap(), gaps(dark = true))
    }
}
