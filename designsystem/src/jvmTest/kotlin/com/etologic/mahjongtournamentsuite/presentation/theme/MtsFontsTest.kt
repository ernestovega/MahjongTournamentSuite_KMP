package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Gang of Three has no Cyrillic letters, and the web draws nothing for a missing letter: Cyrillic languages use
 * MTS Cyrillic Caps, which looks like it, and languages with letters that neither font has use the text font.
 */
@OptIn(ExperimentalTestApi::class)
class MtsFontsTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private fun familyIn(tag: String): FontFamily? {
        Locale.setDefault(Locale.forLanguageTag(tag))
        var family: FontFamily? = null
        runComposeUiTest {
            setContent { family = GangOfThreeFontFamily() }
            waitForIdle()
        }
        return family
    }

    @Test
    fun eachLanguageGetsAFontWithItsLetters() {
        for (tag in listOf("en", "es", "fr", "it", "de", "sv", "nl")) assertEquals(TitleFont.Decorative, titleFontFor(tag), tag)
        assertEquals(TitleFont.CyrillicCaps, titleFontFor("ru"))
        assertEquals(TitleFont.Text, titleFontFor("ja"))
    }

    @Test
    fun russianTitlesUseTheCyrillicFontAndOtherLanguagesTheirOwn() {
        val russian = familyIn("ru")
        val english = familyIn("en")
        assertNotEquals(FontFamily.Default, russian)
        assertNotEquals(english, russian)
        assertEquals(FontFamily.Default, familyIn("ja"))
    }

    /** The font is a modified Russo One: the OFL needs its license next to it. */
    @Test
    fun theLicenseOfTheCyrillicFontIsInTheRepository() {
        assertTrue(File("licenses/RussoOne-OFL.txt").readText().contains("SIL Open Font License"))
    }

    @Test
    fun onlyTheBigTitlesUseTheDecorativeFont() {
        Locale.setDefault(Locale.ENGLISH)
        var typography: androidx.compose.material3.Typography? = null
        var title: FontFamily? = null
        var scoreLarge: FontFamily? = null
        runComposeUiTest {
            setContent {
                MtsTheme {
                    typography = androidx.compose.material3.MaterialTheme.typography
                    title = GangOfThreeFontFamily()
                    scoreLarge = MtsTheme.typography.scoreLarge.fontFamily
                }
            }
            waitForIdle()
        }
        val t = typography!!
        // Titles of screens, dialogs and sections (Ernesto, 2026-11-25).
        for (style in listOf(t.displayLarge, t.headlineSmall, t.titleLarge, t.titleMedium)) assertEquals(title, style.fontFamily)
        // Content and actions: small titles, buttons, chips, tabs and fields.
        for (style in listOf(t.titleSmall, t.labelLarge, t.labelMedium, t.labelSmall, t.bodyLarge, t.bodyMedium)) assertNotEquals(title, style.fontFamily)
        assertEquals(FontFamily.Default, scoreLarge)
    }
}
