package com.etologic.mahjongtournamentsuite.domain.telemetry

/**
 * Usage and error tracing. Each platform sends the data to the place it supports: Firebase Analytics and Crashlytics
 * on Android, Analytics on the web and on the desktop. A platform without settings uses [NoTelemetry].
 *
 * Rules for every caller:
 * - Never send personal data: no email, no name, no EMA id, no tournament id. Send only counts and fixed words.
 * - Event names and parameter names come from [TelemetryEvent] and [TelemetryParam].
 */
interface Telemetry {
    /** Counts one event. A parameter value is a [String] or a number. */
    fun event(name: String, params: Map<String, Any> = emptyMap())

    /** Counts one visit of a screen. [name] is a fixed name, never an id. */
    fun screen(name: String)

    /** Reports an error that the app handled. [area] tells which action failed. A fatal crash needs no call. */
    fun error(throwable: Throwable, area: String)

    /** Sets a property that GA4 adds to every later event of this user. A null [value] removes it. */
    fun userProperty(name: String, value: String?)
}

object NoTelemetry : Telemetry {
    override fun event(name: String, params: Map<String, Any>) = Unit
    override fun screen(name: String) = Unit
    override fun error(throwable: Throwable, area: String) = Unit
    override fun userProperty(name: String, value: String?) = Unit
}
