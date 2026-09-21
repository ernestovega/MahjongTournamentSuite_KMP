package com.etologic.mahjongtournamentsuite.presentation.theme

import android.content.Context
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

internal actual object ThemePreferenceStorage {
    actual fun load(): String? = runCatching {
        preferences().getString(KEY, null)
    }.getOrNull()

    actual fun save(value: String) {
        runCatching { preferences().edit().putString(KEY, value).apply() }
    }

    private fun preferences() = AndroidPlatformContext.requireContext().getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    private const val FILE_NAME = "mahjong_tournament_suite_preferences"
    private const val KEY = "themePreference"
}
