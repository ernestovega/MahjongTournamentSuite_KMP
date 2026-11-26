package com.etologic.mahjongtournamentsuite.presentation

import com.etologic.mahjongtournamentsuite.presentation.language.LanguagePreferenceStorage
import com.etologic.mahjongtournamentsuite.presentation.language.applyPlatformLanguage
import com.etologic.mahjongtournamentsuite.presentation.language.systemLanguageTags
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreferenceStorage
import com.etologic.mahjongtournamentsuite.presentation.util.platformFormatDate
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The choices of the user in the defaults of the iOS app, and the dates in the language of the app. */
class IosPlatformStorageTest {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val systemTags = systemLanguageTags()

    @AfterTest
    fun cleanUp() {
        listOf("themePreference", "languagePreference", "aiTranslationNoticeSeen", "AppleLanguages")
            .forEach(defaults::removeObjectForKey)
    }

    @Test
    fun theThemeChoiceIsKept() {
        ThemePreferenceStorage.save("DARK")
        assertEquals("DARK", ThemePreferenceStorage.load())
        ThemePreferenceStorage.save("LIGHT")
        assertEquals("LIGHT", ThemePreferenceStorage.load())
    }

    @Test
    fun theLanguageChoiceAndTheAiNoticeAreKept() {
        LanguagePreferenceStorage.save("es")
        assertEquals("es", LanguagePreferenceStorage.load())
        defaults.removeObjectForKey("aiTranslationNoticeSeen")
        assertEquals(false, LanguagePreferenceStorage.loadAiNoticeSeen())
        LanguagePreferenceStorage.saveAiNoticeSeen()
        assertEquals(true, LanguagePreferenceStorage.loadAiNoticeSeen())
    }

    @Test
    fun choosingALanguageKeepsTheLanguagesOfTheDevice() {
        applyPlatformLanguage("es")
        assertEquals(listOf("es"), defaults.arrayForKey("AppleLanguages")?.map { it as String })
        assertEquals(systemTags, systemLanguageTags())
    }

    @Test
    fun datesFollowTheLanguageOfTheApp() {
        applyPlatformLanguage("en")
        val english = platformFormatDate(2027, 2, 13, withWeekday = true, withYear = true, fullNames = true)
        assertTrue("Saturday" in english && "February" in english && "13" in english && "2027" in english, english)

        applyPlatformLanguage("es")
        val spanish = platformFormatDate(2027, 2, 13, withWeekday = true, withYear = true, fullNames = true)
        assertTrue("sábado" in spanish && "febrero" in spanish && "13" in spanish && "2027" in spanish, spanish)

        val short = platformFormatDate(2027, 2, 13, withWeekday = false, withYear = false, fullNames = false)
        assertTrue("13" in short && "2027" !in short, short)
    }
}
