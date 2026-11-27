package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Timer and the projected ranking take the color of the organizer: any color must stay readable. */
@OptIn(ExperimentalTestApi::class)
class MtsBrandThemeTest {
    private val brands = listOf(
        Color(0xFFF9A825), // yellow
        Color(0xFFFFFFFF), // white
        Color(0xFF000000), // black
        Color(0xFF1565C0), // blue
        Color(0xFF02B16B), // the green of the suite
        Color(0xFFFA6868), // light red
        Color(0xFF6A1B9A), // dark purple
    )

    private fun themeOf(dark: Boolean): Pair<ColorScheme, MtsColors> {
        var theme: Pair<ColorScheme, MtsColors>? = null
        runComposeUiTest { setContent { MtsTheme(useDarkTheme = dark) { theme = MaterialTheme.colorScheme to MtsTheme.colors } } }
        return theme!!
    }

    @Test
    fun theBrandAsTextReadsOnThePageAndTheCards() {
        for (dark in listOf(false, true)) {
            val (scheme, colors) = themeOf(dark)
            for (brand in brands) {
                val (branded, brandedColors) = brandedColors(scheme, colors, brand)
                for (background in listOf(branded.background, branded.surface, branded.surfaceContainerHighest)) {
                    val ratio = contrast(brandedColors.link, background)
                    assertTrue(ratio >= 4.5, "link of $brand on $background (dark=$dark): $ratio")
                }
            }
        }
    }

    @Test
    fun theTextOnTheBrandIsTheMoreReadableOfWhiteAndBlack() {
        val (scheme, colors) = themeOf(false)
        for (brand in brands) {
            val (branded, _) = brandedColors(scheme, colors, brand)
            // White or the ink always reach 4.5:1 on one of them: the better one is chosen.
            assertTrue(contrast(branded.onPrimary, branded.primary) >= 4.5, "text on $brand")
        }
        assertEquals(MtsFixedColors.inkOnLight, textOn(Color(0xFFF9A825)))
        assertEquals(Color.White, textOn(Color(0xFF1565C0)))
    }
}
