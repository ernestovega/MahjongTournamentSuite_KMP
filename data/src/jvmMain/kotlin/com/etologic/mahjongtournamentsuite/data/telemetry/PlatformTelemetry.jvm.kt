package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The settings of Analytics for the desktop app. The desktop has no Firebase SDK, so it sends the events to the
 * Measurement Protocol of GA4. These two values are public. Take them from the data stream of the admin web app:
 * the measurement id (`G-...`) and an API secret made in "Measurement Protocol API secrets". See docs/telemetry.md.
 * Without them the desktop app sends nothing.
 */
internal object DesktopTelemetryConfig {
    const val MEASUREMENT_ID = ""
    const val API_SECRET = ""
}

/**
 * Analytics of the desktop app. A handled error goes to Analytics as `app_error`, and an error that nobody handled
 * goes as `app_crash`.
 */
internal class DesktopTelemetry(
    private val measurementId: String,
    private val apiSecret: String,
) : Telemetry {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val clientId = readClientId()
    private val sessionId = System.currentTimeMillis() / 1000
    private val properties = ConcurrentHashMap<String, String>()

    init {
        // jpackage sets this property in an installed app. A run from the IDE or Gradle does not have it.
        val installed = System.getProperty("jpackage.app-path") != null
        properties[TelemetryProperty.PLATFORM] = "desktop"
        properties[TelemetryProperty.ENVIRONMENT] = if (installed) "prod" else "dev"
        properties[TelemetryProperty.LANGUAGE] = Locale.getDefault().language
        installCrashHandler()
    }

    override fun event(name: String, params: Map<String, Any>) {
        send(name, params, wait = false)
    }

    override fun screen(name: String) {
        event("screen_view", mapOf("screen_name" to name))
    }

    override fun error(throwable: Throwable, area: String) {
        event(TelemetryEvent.APP_ERROR, errorParams(throwable, area))
    }

    override fun userProperty(name: String, value: String?) {
        if (value == null) properties.remove(name) else properties[name] = value
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                // The app may close right after this handler, so the call waits for the answer.
                send(TelemetryEvent.APP_CRASH, mapOf(TelemetryParam.KIND to (throwable::class.simpleName ?: "Error")), wait = true)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun send(name: String, params: Map<String, Any>, wait: Boolean) {
        runCatching {
            val body = JsonObject(
                mapOf(
                    "client_id" to JsonPrimitive(clientId),
                    "user_properties" to JsonObject(
                        properties.mapValues { JsonObject(mapOf("value" to JsonPrimitive(it.value))) },
                    ),
                    "events" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(name),
                                    "params" to JsonObject(
                                        params.toJsonObject() + mapOf(
                                            // Without these two values GA4 does not count the event in the standard reports.
                                            "session_id" to JsonPrimitive(sessionId),
                                            "engagement_time_msec" to JsonPrimitive(1),
                                            "app_version" to JsonPrimitive(AppVersion.name),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ).toString()
            val request = HttpRequest.newBuilder(
                URI("https://www.google-analytics.com/mp/collect?measurement_id=$measurementId&api_secret=$apiSecret"),
            ).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body)).build()
            val response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            if (wait) response.get(2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    /** A random id for this installation. It holds no data about the person. */
    private fun readClientId(): String = runCatching {
        val preferences = Preferences.userRoot().node("MahjongTournamentSuite")
        preferences.get(CLIENT_ID_KEY, null) ?: UUID.randomUUID().toString().also { preferences.put(CLIENT_ID_KEY, it) }
    }.getOrElse { UUID.randomUUID().toString() }

    private companion object {
        const val CLIENT_ID_KEY = "telemetryClientId"
    }
}

actual fun providePlatformTelemetry(): Telemetry =
    if (DesktopTelemetryConfig.MEASUREMENT_ID.isBlank() || DesktopTelemetryConfig.API_SECRET.isBlank()) {
        NoTelemetry
    } else {
        DesktopTelemetry(DesktopTelemetryConfig.MEASUREMENT_ID, DesktopTelemetryConfig.API_SECRET)
    }
