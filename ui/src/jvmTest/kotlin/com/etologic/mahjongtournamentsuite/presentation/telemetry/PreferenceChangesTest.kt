package com.etologic.mahjongtournamentsuite.presentation.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryPreference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferenceChangesTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val telemetry = object : Telemetry {
        override fun event(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun screen(name: String) = Unit
        override fun error(throwable: Throwable, area: String) = Unit
        override fun userProperty(name: String, value: String?) = Unit
        override val requiresConsent = false
        override fun hasConsent() = true
        override fun setConsent(granted: Boolean) = Unit
    }

    @AfterTest
    fun reset() = PreferenceChanges.reset()

    @Test
    fun theFirstValuesAreNotAChange() {
        PreferenceChanges.seen(telemetry, "system", "en")
        PreferenceChanges.seen(telemetry, "system", "en")
        assertEquals(emptyList(), events)
    }

    @Test
    fun aNewThemeOrLanguageCountsOnce() {
        PreferenceChanges.seen(telemetry, "system", "en")
        PreferenceChanges.seen(telemetry, "dark", "en")
        PreferenceChanges.seen(telemetry, "dark", "es")
        PreferenceChanges.seen(telemetry, "dark", "es")
        fun change(kind: String, value: String) = TelemetryEvent.PREFERENCE_CHANGED to mapOf<String, Any>(TelemetryParam.KIND to kind, TelemetryParam.ACTION to value)
        assertEquals(listOf(change(TelemetryPreference.THEME, "dark"), change(TelemetryPreference.LANGUAGE, "es")), events)
    }
}
