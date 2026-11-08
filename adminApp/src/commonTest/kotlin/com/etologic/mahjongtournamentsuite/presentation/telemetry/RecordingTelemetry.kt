package com.etologic.mahjongtournamentsuite.presentation.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry

/** A [Telemetry] that keeps what it gets, so a test can read it. */
internal class RecordingTelemetry : Telemetry {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun screen(name: String) = Unit
    override fun error(throwable: Throwable, area: String) = Unit
    override fun userProperty(name: String, value: String?) = Unit
    override val requiresConsent: Boolean = false
    override fun hasConsent(): Boolean = true
    override fun setConsent(granted: Boolean) = Unit
}
