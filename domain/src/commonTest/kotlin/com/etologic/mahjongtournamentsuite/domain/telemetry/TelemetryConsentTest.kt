package com.etologic.mahjongtournamentsuite.domain.telemetry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryConsentTest {
    private class OnlyConsent(private var consent: Boolean) : Telemetry {
        override fun event(name: String, params: Map<String, Any>) = Unit
        override fun screen(name: String) = Unit
        override fun error(throwable: Throwable, area: String) = Unit
        override fun userProperty(name: String, value: String?) = Unit
        override val requiresConsent: Boolean = true
        override fun hasConsent(): Boolean = consent
        override fun setConsent(granted: Boolean) {
            consent = granted
        }
    }

    @Test
    fun aTelemetryThatKeepsOnlyTheConsentCountsAnAcceptAsTheAnswer() {
        assertFalse(OnlyConsent(consent = false).hasAnswered())
        assertTrue(OnlyConsent(consent = true).hasAnswered())
    }

    @Test
    fun noTelemetryNeverAsksAndNeverSends() {
        assertFalse(NoTelemetry.requiresConsent)
        assertFalse(NoTelemetry.hasAnswered())
        NoTelemetry.setConsent(true)
        assertFalse(NoTelemetry.hasConsent())
    }
}
