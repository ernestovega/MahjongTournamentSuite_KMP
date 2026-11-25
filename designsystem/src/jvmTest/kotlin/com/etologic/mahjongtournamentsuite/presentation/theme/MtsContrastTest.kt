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
 * so it is not checked. The known gaps are listed below: the test fails when a new one appears, or when one is fixed
 * and the list should lose it.
 */
@OptIn(ExperimentalTestApi::class)
class MtsContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val light = max(a.luminance(), b.luminance())
        val dark = min(a.luminance(), b.luminance())
        return (light + 0.05) / (dark + 0.05)
    }

    private fun schemeOf(dark: Boolean): ColorScheme {
        var scheme: ColorScheme? = null
        runComposeUiTest { setContent { MtsTheme(useDarkTheme = dark) { scheme = MaterialTheme.colorScheme } } }
        return scheme!!
    }

    /** Name of the pair to its foreground, background and the ratio that it needs. */
    private fun pairs(s: ColorScheme) = mapOf(
        "onBackground on background" to Triple(s.onBackground, s.background, 4.5),
        "onSurface on surface" to Triple(s.onSurface, s.surface, 4.5),
        "onSurface on card" to Triple(s.onSurface, s.surfaceContainerHighest, 4.5),
        "onSurfaceVariant on surface" to Triple(s.onSurfaceVariant, s.surface, 4.5),
        "onSurfaceVariant on card" to Triple(s.onSurfaceVariant, s.surfaceContainerHighest, 4.5),
        "onSurfaceVariant on surfaceVariant" to Triple(s.onSurfaceVariant, s.surfaceVariant, 4.5),
        "onSecondaryContainer on secondaryContainer" to Triple(s.onSecondaryContainer, s.secondaryContainer, 4.5),
        "onTertiaryContainer on tertiaryContainer" to Triple(s.onTertiaryContainer, s.tertiaryContainer, 4.5),
        "inverseOnSurface on inverseSurface" to Triple(s.inverseOnSurface, s.inverseSurface, 4.5),
        "primary text on card" to Triple(s.primary, s.surfaceContainerHighest, 4.5),
        "error text on card" to Triple(s.error, s.surfaceContainerHighest, 4.5),
        "outline on surface" to Triple(s.outline, s.surface, 3.0),
        "outline on card" to Triple(s.outline, s.surfaceContainerHighest, 3.0),
    )

    private fun gaps(dark: Boolean): Map<String, Double> = pairs(schemeOf(dark))
        .filter { (_, pair) -> ratio(pair.first, pair.second) < pair.third }
        .mapValues { (_, pair) -> (ratio(pair.first, pair.second) * 100).roundToInt() / 100.0 }

    @Test
    fun theLightThemeHasOnlyTheKnownGaps() {
        // The green of the brand and the red of errors as text on white (text buttons, links, error texts).
        assertEquals(setOf("primary text on card", "error text on card"), gaps(dark = false).keys, gaps(dark = false).toString())
    }

    @Test
    fun theDarkThemeHasOnlyTheKnownGaps() {
        // The borders of fields in dark mode, and the green and the red as text on the dark cards.
        assertEquals(
            setOf("primary text on card", "error text on card", "outline on surface", "outline on card"),
            gaps(dark = true).keys,
            gaps(dark = true).toString(),
        )
    }
}
