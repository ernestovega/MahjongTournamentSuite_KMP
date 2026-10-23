package com.etologic.mahjongtournamentsuite.player

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.allPluralStringResources
import mahjongtournamentsuite.playerapp.generated.resources.allStringResources
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

    @Test
    fun everyTextLoadsInEnglishAndSpanish() = runTest {
        val english = textsIn("en")
        val spanish = textsIn("es")
        assertTrue(english.isNotEmpty())
        assertEquals(english.keys, spanish.keys)
        english.forEach { (key, text) -> assertTrue(text.isNotBlank() || key in BLANK_ALLOWED, "English text $key is empty") }
        spanish.forEach { (key, text) -> assertTrue(text.isNotBlank() || key in BLANK_ALLOWED, "Spanish text $key is empty") }
    }

    @Test
    fun mostSpanishTextsDifferFromTheEnglishOnes() = runTest {
        val english = textsIn("en")
        val spanish = textsIn("es")
        val same = english.keys.count { english[it] == spanish[it] }
        assertTrue(same < english.size * 0.35, "Too many Spanish texts equal the English ones: $same of ${english.size}")
    }

    @Test
    fun aLanguageWithoutTextsUsesTheEnglishTexts() = runTest {
        assertEquals(textsIn("en"), textsIn("fr"))
    }

    private companion object {
        val BLANK_ALLOWED = emptySet<String>()
    }
}
