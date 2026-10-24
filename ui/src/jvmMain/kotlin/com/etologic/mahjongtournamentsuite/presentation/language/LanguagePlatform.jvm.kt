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

    private const val KEY = "languagePreference"
}

internal actual fun applyPlatformLanguage(tag: String) {
    Locale.setDefault(Locale.forLanguageTag(tag))
}
