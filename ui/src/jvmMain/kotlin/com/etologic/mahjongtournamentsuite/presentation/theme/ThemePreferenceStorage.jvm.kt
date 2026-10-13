package com.etologic.mahjongtournamentsuite.presentation.theme

import java.util.prefs.Preferences

internal actual object ThemePreferenceStorage {
    private val preferences: Preferences
        get() = Preferences.userNodeForPackage(ThemePreferenceStorage::class.java)

    actual fun load(): String? = runCatching {
        preferences.get(KEY, null)
    }.getOrNull()

    actual fun save(value: String) {
        runCatching { preferences.put(KEY, value) }
    }

    private const val KEY = "themePreference"
}
