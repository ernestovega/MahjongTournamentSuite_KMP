package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import java.io.File
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
 * Analytics of the desktop app. It sends nothing before the user accepted.
 */
internal class DesktopTelemetry(
    private val measurementId: String,
    private val apiSecret: String,
    private val env: String,
) : Telemetry {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val clientId = readClientId()
    private val sessionId = System.currentTimeMillis() / 1000
    private val properties = ConcurrentHashMap<String, String>()
    private val preferences = Preferences.userRoot().node("MahjongTournamentSuite")
    private var consent = runCatching { preferences.getBoolean(CONSENT_KEY, false) }.getOrDefault(false)

    init {
        properties[TelemetryProperty.PLATFORM] = "desktop"
        properties[TelemetryProperty.ENVIRONMENT] = env
        properties[TelemetryProperty.LANGUAGE] = Locale.getDefault().language
    }

    override val requiresConsent: Boolean = true

    override fun hasConsent(): Boolean = consent

    override fun setConsent(granted: Boolean) {
        consent = granted
        runCatching { preferences.putBoolean(CONSENT_KEY, granted) }
    }

    override fun event(name: String, params: Map<String, Any>) {
        send(name, params)
    }

    override fun screen(name: String) {
        event("screen_view", mapOf("screen_name" to name))
    }

    /** The error log of the API takes handled errors. The decorator sends them there and counts them as events. */
    override fun error(throwable: Throwable, area: String) = Unit

    override fun userProperty(name: String, value: String?) {
        if (value == null) properties.remove(name) else properties[name] = value
    }

    private fun send(name: String, params: Map<String, Any>) {
        if (!consent) return
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
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        }
    }

    /** A random id for this installation. It holds no data about the person. */
    private fun readClientId(): String = runCatching {
        val preferences = Preferences.userRoot().node("MahjongTournamentSuite")
        preferences.get(CLIENT_ID_KEY, null) ?: UUID.randomUUID().toString().also { preferences.put(CLIENT_ID_KEY, it) }
    }.getOrElse { UUID.randomUUID().toString() }

    private companion object {
        const val CLIENT_ID_KEY = "telemetryClientId"
        const val CONSENT_KEY = "telemetryConsent"
    }
}

/** A file in the home folder. A crash writes it, and the next start sends it. */
internal class FilePendingCrashStore(private val file: File) : PendingCrashStore {
    override fun save(json: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    override fun take(): String? = runCatching {
        if (file.exists()) file.readText().also { file.delete() } else null
    }.getOrNull()
}

actual fun providePlatformTelemetry(): PlatformTelemetry {
    // jpackage sets this property in an installed app. A run from the IDE or Gradle does not have it.
    val env = if (System.getProperty("jpackage.app-path") != null) "prod" else "dev"
    val analytics = if (DesktopTelemetryConfig.MEASUREMENT_ID.isBlank() || DesktopTelemetryConfig.API_SECRET.isBlank()) {
        NoTelemetry
    } else {
        DesktopTelemetry(DesktopTelemetryConfig.MEASUREMENT_ID, DesktopTelemetryConfig.API_SECRET, env)
    }
    val pending = FilePendingCrashStore(File(System.getProperty("user.home"), ".mahjongtournamentsuite/pending-crash.json"))
    return PlatformTelemetry(analytics, "admin", "desktop", env, Locale.getDefault().language, pending)
}

actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        onCrash(throwable)
        previous?.uncaughtException(thread, throwable)
    }
}
