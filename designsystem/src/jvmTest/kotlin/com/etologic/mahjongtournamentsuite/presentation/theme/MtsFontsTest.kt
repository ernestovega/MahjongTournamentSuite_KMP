package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The decorative font has no Cyrillic letters, and the web draws nothing for a missing letter. */
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
    fun russianTitlesUseTheTextFont() = assertEquals(FontFamily.Default, familyIn("ru"))

    @Test
    fun latinLanguagesKeepTheDecorativeFont() {
        for (tag in listOf("en", "es", "de", "sv", "nl")) assertNotEquals(FontFamily.Default, familyIn(tag), tag)
    }
}
