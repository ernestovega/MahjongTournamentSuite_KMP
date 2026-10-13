package com.etologic.mahjongtournamentsuite.presentation.theme

import kotlinx.browser.window

internal actual object ThemePreferenceStorage {
    actual fun load(): String? = runCatching {
        window.localStorage.getItem(KEY)
    }.getOrNull()

    actual fun save(value: String) {
        runCatching { window.localStorage.setItem(KEY, value) }
    }

    private const val KEY = "themePreference"
}
