package com.etologic.mahjongtournamentsuite.domain.telemetry

/** A [Telemetry] that keeps what it gets, so a test can read it. */
class RecordingTelemetry : Telemetry {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val screens = mutableListOf<String>()
    val errors = mutableListOf<Pair<Throwable, String>>()

    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun screen(name: String) {
        screens += name
    }

    override fun error(throwable: Throwable, area: String) {
        errors += throwable to area
    }

    override fun userProperty(name: String, value: String?) = Unit
}
