package com.etologic.mahjongtournamentsuite.presentation

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.allPluralStringResources
import mahjongtournamentsuite.ui.generated.resources.allStringResources
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Every text of the module loads in English and in Spanish, and none of them is empty. */
class AllTextsResolveTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private suspend fun textsIn(language: String): Map<String, String> {
        Locale.setDefault(Locale.forLanguageTag(language))
        val texts = Res.allStringResources.mapValues { getString(it.value) }.toMutableMap()
        Res.allPluralStringResources.forEach { (name, resource) ->
            texts["$name#1"] = getPluralString(resource, 1, 1, 1, 1, 1, 1, 1)
            texts["$name#5"] = getPluralString(resource, 5, 5, 5, 5, 5, 5, 5)
        }
        return texts
    }

    /** The languages with their own texts. Each one has the same keys as English. */
    private val translated = listOf("es", "fr", "it")

    @Test
    fun everyTextLoadsInEveryLanguage() = runTest {
        val english = textsIn("en")
        assertTrue(english.isNotEmpty())
        english.forEach { (key, text) -> assertTrue(text.isNotBlank() || key in BLANK_ALLOWED, "English text $key is empty") }
        for (language in translated) {
            val texts = textsIn(language)
            assertEquals(english.keys, texts.keys, language)
            texts.forEach { (key, text) -> assertTrue(text.isNotBlank() || key in BLANK_ALLOWED, "$language text $key is empty") }
        }
    }

    @Test
    fun mostTextsDifferFromTheEnglishOnes() = runTest {
        val english = textsIn("en")
        for (language in translated) {
            val texts = textsIn(language)
            val same = english.keys.count { english[it] == texts[it] }
            assertTrue(same < english.size * 0.35, "Too many $language texts equal the English ones: $same of ${english.size}")
        }
    }

    @Test
    fun aLanguageWithoutTextsUsesTheEnglishTexts() = runTest {
        assertEquals(textsIn("en"), textsIn("sw"))
    }

    private companion object {
        val BLANK_ALLOWED = emptySet<String>()
    }
}
