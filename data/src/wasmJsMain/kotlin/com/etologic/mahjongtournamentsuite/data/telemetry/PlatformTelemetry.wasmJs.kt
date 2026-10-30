package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** `telemetry.js` of the web app defines this function. Without it, or without its settings, the call does nothing. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(kind, name, json) => { if (typeof window.mtsTelemetry === 'function') window.mtsTelemetry(kind, name, json); }")
private external fun sendTelemetry(kind: String, name: String, json: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof window.mtsTelemetryHasConsent === 'function') && window.mtsTelemetryHasConsent()")
private external fun readConsent(): Boolean

/** True when the settings of Analytics are filled in at the top of `telemetry.js`. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof window.mtsTelemetryConfigured === 'function') && window.mtsTelemetryConfigured()")
private external fun readConfigured(): Boolean

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => window.mtsApp || 'admin'")
private external fun readApp(): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => (navigator.language || 'en').slice(0, 2)")
private external fun readLanguage(): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => location.hostname === 'localhost' ? 'dev' : 'prod'")
private external fun readEnvironment(): String

/**
 * Analytics of the web app through `telemetry.js`. The same file sends the crashes of the page to the error log,
 * because a page that dies cannot wait for Kotlin. It sends nothing before the user accepted.
 */
private object WebTelemetry : Telemetry {
    init {
        // The crash reports of the page need the version of the app.
        sendTelemetry("context", "", JsonObject(mapOf("appVersion" to JsonPrimitive(AppVersion.name))).toString())
    }

    override val requiresConsent: Boolean get() = readConfigured()

    override fun hasConsent(): Boolean = readConsent()

    override fun setConsent(granted: Boolean) =
        sendTelemetry("consent", "", JsonObject(mapOf("value" to JsonPrimitive(granted))).toString())

    override fun event(name: String, params: Map<String, Any>) =
        sendTelemetry("event", name, params.toJsonObject().toString())

    override fun screen(name: String) = sendTelemetry("screen", name, "{}")

    /** The error log of the API takes handled errors. The decorator sends them there and counts them as events. */
    override fun error(throwable: Throwable, area: String) = Unit

    override fun userProperty(name: String, value: String?) =
        sendTelemetry("property", name, JsonObject(mapOf("value" to JsonPrimitive(value))).toString())
}

actual fun providePlatformTelemetry(): PlatformTelemetry =
    PlatformTelemetry(WebTelemetry, readApp(), "web", readEnvironment(), readLanguage(), pendingCrashes = null)

/** The web has no thread handler. `telemetry.js` listens to the errors of the page. */
actual fun installCrashHandler(onCrash: (Throwable) -> Unit) = Unit
