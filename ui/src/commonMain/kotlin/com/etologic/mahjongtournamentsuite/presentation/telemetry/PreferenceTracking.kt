package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryPreference
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController

/**
 * Counts `preference_changed` when the user changes the theme or the language, wherever the app offers it (profile
 * dialog, top bar). The first value after the start is not a change. Each app calls it once, below its theme and
 * language. A change of the language draws the app again, so the last values are kept outside the composition.
 */
@Composable
fun TrackPreferenceChanges() {
    val telemetry = LocalTelemetry.current
    val theme = LocalThemeController.current.preference.name.lowercase()
    val language = LocalLanguageController.current.language.tag
    SideEffect { PreferenceChanges.seen(telemetry, theme, language) }
}

internal object PreferenceChanges {
    private var lastTheme: String? = null
    private var lastLanguage: String? = null

    fun seen(telemetry: Telemetry, theme: String, language: String) {
        if (lastTheme != null && lastTheme != theme) telemetry.changed(TelemetryPreference.THEME, theme)
        if (lastLanguage != null && lastLanguage != language) telemetry.changed(TelemetryPreference.LANGUAGE, language)
        lastTheme = theme
        lastLanguage = language
    }

    /** For the tests: the next value counts as the first one again. */
    fun reset() {
        lastTheme = null
        lastLanguage = null
    }

    private fun Telemetry.changed(kind: String, value: String) = event(TelemetryEvent.PREFERENCE_CHANGED, mapOf(TelemetryParam.KIND to kind, TelemetryParam.ACTION to value))
}
