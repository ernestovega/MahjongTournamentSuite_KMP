package com.etologic.mahjongtournamentsuite.presentation.language

import android.content.Context
import android.content.res.Resources
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import java.util.Locale

internal actual object LanguagePreferenceStorage {
    actual fun load(): String? = runCatching { preferences().getString(KEY, null) }.getOrNull()

    actual fun save(value: String) {
        runCatching { preferences().edit().putString(KEY, value).apply() }
    }

    actual fun loadAiNoticeSeen(): Boolean = runCatching { preferences().getBoolean(AI_NOTICE_KEY, false) }.getOrDefault(false)

    actual fun saveAiNoticeSeen() {
        runCatching { preferences().edit().putBoolean(AI_NOTICE_KEY, true).apply() }
    }

    private fun preferences() = AndroidPlatformContext.requireContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private const val FILE_NAME = "mahjong_tournament_suite_preferences"
    private const val KEY = "languagePreference"
    private const val AI_NOTICE_KEY = "aiTranslationNoticeSeen"
}

internal actual fun applyPlatformLanguage(tag: String) {
    Locale.setDefault(Locale.forLanguageTag(tag))
}

/** The system configuration keeps the languages of the device, which [Locale.setDefault] does not change. */
internal actual fun systemLanguageTags(): List<String> = runCatching {
    val locales = Resources.getSystem().configuration.locales
    (0 until locales.size()).map { locales[it].toLanguageTag() }
}.getOrDefault(emptyList())
