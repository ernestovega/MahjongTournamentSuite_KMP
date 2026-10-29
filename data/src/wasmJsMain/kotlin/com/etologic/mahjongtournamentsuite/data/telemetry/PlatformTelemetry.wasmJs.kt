package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** `telemetry.js` of the web app defines this function. Without it, or without its settings, the call does nothing. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(kind, name, json) => { if (typeof window.mtsTelemetry === 'function') window.mtsTelemetry(kind, name, json); }")
private external fun sendTelemetry(kind: String, name: String, json: String)

/**
 * Analytics of the web app through `telemetry.js`. A handled error goes to Analytics
 * as the event `app_error`. A crash goes there as `app_crash`, sent by the error handlers of `telemetry.js`.
 */
private object WebTelemetry : Telemetry {
    override fun event(name: String, params: Map<String, Any>) =
        sendTelemetry("event", name, params.toJsonObject().toString())

    override fun screen(name: String) = sendTelemetry("screen", name, "{}")

    override fun error(throwable: Throwable, area: String) =
        event(TelemetryEvent.APP_ERROR, errorParams(throwable, area))

    override fun userProperty(name: String, value: String?) =
        sendTelemetry("property", name, JsonObject(mapOf("value" to JsonPrimitive(value))).toString())
}

actual fun providePlatformTelemetry(): Telemetry = WebTelemetry
