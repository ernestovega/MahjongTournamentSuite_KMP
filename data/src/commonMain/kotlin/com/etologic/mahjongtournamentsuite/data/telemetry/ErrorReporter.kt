package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Sends the errors with their stack trace to the error log of the API, `POST /telemetry/errors`.
 * Each report has an id. The same id goes in the Analytics event of the error, so the two records join.
 * Nothing is sent before the user accepted the analytics. A failed send is dropped without a log line,
 * because a log line would report an error again.
 */
class ErrorReporter(
    private val platform: PlatformTelemetry,
    private val httpClient: HttpClient,
    private val apiConfiguration: ApiConfiguration,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val analytics = platform.analytics
    private val sessionId = newId()
    private var role: String? = null
    private var sent = 0

    fun setRole(value: String?) {
        role = value
    }

    /** An error that the app caught and that points to a bug. */
    fun reportHandled(throwable: Throwable, area: String) {
        if (!analytics.hasConsent() || sent >= MAX_REPORTS_PER_RUN) return
        val failure = throwable.classify() ?: return
        sent += 1
        val reportId = newId()
        val status = (throwable as? BackendHttpException)?.status?.value
        analytics.event(
            TelemetryEvent.APP_ERROR,
            mapOf(
                TelemetryParam.REPORT_ID to reportId,
                TelemetryParam.ACTION to area.take(MAX_SHORT),
                TelemetryParam.KIND to (throwable::class.simpleName ?: "Error"),
                TelemetryParam.REASON to failure.reason,
            ),
        )
        post(payload(KIND_HANDLED, reportId, throwable, area, failure.reason, status))
    }

    /**
     * An error that stops the app. The app cannot send it now, so it goes to the store and leaves at the next start.
     * This runs while the app dies, so it only writes a small string.
     */
    fun recordCrash(throwable: Throwable) {
        if (!analytics.hasConsent()) return
        runCatching {
            platform.pendingCrashes?.save(payload(KIND_CRASH, newId(), throwable, "", "", null).toString())
        }
    }

    /** Sends the crash of the last run, if there is one, and counts it in Analytics with the same report id. */
    fun flushPending() {
        if (!analytics.hasConsent()) return
        val saved = runCatching { platform.pendingCrashes?.take() }.getOrNull() ?: return
        val report = runCatching { Json.parseToJsonElement(saved).jsonObject }.getOrNull() ?: return
        analytics.event(
            TelemetryEvent.APP_CRASH,
            mapOf(
                TelemetryParam.REPORT_ID to report["reportId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                TelemetryParam.KIND to report["errorType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ),
        )
        post(report)
    }

    private fun payload(
        kind: String,
        reportId: String,
        throwable: Throwable,
        area: String,
        reason: String,
        status: Int?,
    ): JsonObject {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "app" to JsonPrimitive(platform.app),
            "platform" to JsonPrimitive(platform.platform),
            "env" to JsonPrimitive(platform.env),
            "language" to JsonPrimitive(platform.language),
            "appVersion" to JsonPrimitive(AppVersion.name),
            "sessionId" to JsonPrimitive(sessionId),
            "kind" to JsonPrimitive(kind),
            "reportId" to JsonPrimitive(reportId),
            "errorType" to JsonPrimitive(throwable::class.simpleName ?: "Error"),
            "area" to JsonPrimitive(area),
            "message" to JsonPrimitive(throwable.message.orEmpty().take(MAX_MESSAGE)),
            "stackTrace" to JsonPrimitive(throwable.stackTraceToString().take(MAX_STACK)),
            "reason" to JsonPrimitive(reason),
        )
        role?.let { fields["role"] = JsonPrimitive(it) }
        status?.let { fields["httpStatus"] = JsonPrimitive(it) }
        return JsonObject(fields)
    }

    private fun post(body: JsonObject) {
        scope.launch {
            runCatching {
                httpClient.post(apiConfiguration.baseUrl.trimEnd('/') + "/telemetry/errors") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        }
    }

    private companion object {
        const val KIND_HANDLED = "handled"
        const val KIND_CRASH = "crash"
        const val MAX_REPORTS_PER_RUN = 20
        const val MAX_SHORT = 100
        const val MAX_MESSAGE = 500
        const val MAX_STACK = 8000

        fun newId(): String = Random.nextBytes(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
