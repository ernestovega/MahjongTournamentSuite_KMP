package com.etologic.mahjongtournamentsuite.presentation.theme

import platform.Foundation.NSUserDefaults

internal actual object ThemePreferenceStorage {
    actual fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(KEY)

    actual fun save(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, KEY)
    }

    private const val KEY = "themePreference"
}
