package com.etologic.mahjongtournamentsuite.presentation.language

import java.util.Locale
import java.util.prefs.Preferences

internal actual object LanguagePreferenceStorage {
    private val preferences: Preferences
        get() = Preferences.userNodeForPackage(LanguagePreferenceStorage::class.java)

    actual fun load(): String? = runCatching { preferences.get(KEY, null) }.getOrNull()

    actual fun save(value: String) {
        runCatching { preferences.put(KEY, value) }
    }

    actual fun loadAiNoticeSeen(): Boolean = runCatching { preferences.getBoolean(AI_NOTICE_KEY, false) }.getOrDefault(false)

    actual fun saveAiNoticeSeen() {
        runCatching { preferences.putBoolean(AI_NOTICE_KEY, true) }
    }

    private const val KEY = "languagePreference"
    private const val AI_NOTICE_KEY = "aiTranslationNoticeSeen"
}

internal actual fun applyPlatformLanguage(tag: String) {
    Locale.setDefault(Locale.forLanguageTag(tag))
}

/** The language of the system when the app started, before the app applies its own language. */
private val startLocale: Locale = Locale.getDefault()

internal actual fun systemLanguageTags(): List<String> = listOf(startLocale.toLanguageTag())
