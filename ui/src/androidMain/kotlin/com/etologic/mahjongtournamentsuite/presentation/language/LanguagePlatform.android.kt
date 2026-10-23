package com.etologic.mahjongtournamentsuite.presentation.language

import android.content.Context
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import java.util.Locale

internal actual object LanguagePreferenceStorage {
    actual fun load(): String? = runCatching { preferences().getString(KEY, null) }.getOrNull()

    actual fun save(value: String) {
        runCatching { preferences().edit().putString(KEY, value).apply() }
    }

    private fun preferences() = AndroidPlatformContext.requireContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private const val FILE_NAME = "mahjong_tournament_suite_preferences"
    private const val KEY = "languagePreference"
}

/** The language of the device when the app started. */
private val deviceLocale: Locale = Locale.getDefault()

internal actual fun applyPlatformLanguage(tag: String?) {
    Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: deviceLocale)
}
