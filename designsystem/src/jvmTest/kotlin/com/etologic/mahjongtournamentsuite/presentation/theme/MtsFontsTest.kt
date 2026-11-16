package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The decorative font has no Cyrillic letters and no å, and the web draws nothing for a missing letter. */
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
    fun russianAndSwedishTitlesUseTheTextFont() {
        // Cyrillic letters, and the å of Swedish (Omgång).
        for (tag in listOf("ru", "sv")) assertEquals(FontFamily.Default, familyIn(tag), tag)
    }

    @Test
    fun latinLanguagesKeepTheDecorativeFont() {
        for (tag in listOf("en", "es", "fr", "it", "de", "nl")) assertNotEquals(FontFamily.Default, familyIn(tag), tag)
    }
}
