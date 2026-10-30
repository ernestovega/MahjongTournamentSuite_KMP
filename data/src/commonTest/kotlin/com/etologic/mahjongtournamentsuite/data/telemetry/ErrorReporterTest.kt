package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryCrashStore : PendingCrashStore {
    var saved: String? = null
    override fun save(json: String) {
        saved = json
    }

    override fun take(): String? = saved.also { saved = null }
}

class ErrorReporterTest {
    private val requests = mutableListOf<Pair<String, String>>()
    private val analytics = RecordingTelemetry()
    private val store = MemoryCrashStore()
    private var consent = true

    private val client = HttpClient(
        MockEngine { request ->
            requests += request.url.toString() to request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.NoContent)
        },
    )

    /** The send runs on another thread. The test waits a short time for it. */
    private suspend fun awaitRequests(count: Int) = withContext(Dispatchers.Default) {
        repeat(100) {
            if (requests.size >= count) return@withContext
            delay(20)
        }
    }

    private fun reporter(): ErrorReporter {
        val platform = PlatformTelemetry(
            analytics = object : com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry by analytics {
                override fun hasConsent(): Boolean = consent
            },
            app = "player",
            platform = "android",
            env = "prod",
            language = "es",
            pendingCrashes = store,
        )
        return ErrorReporter(platform, client, ApiConfiguration("https://api.test/api"), CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun aHandledErrorGoesToTheLogAndToAnalyticsWithTheSameReportId() = runTest {
        reporter().apply { setRole("editor") }.reportHandled(BackendHttpException(HttpStatusCode.InternalServerError, "body"), "Saving the table failed.")

        awaitRequests(1)
        val (url, body) = requests.single()
        assertEquals("https://api.test/api/telemetry/errors", url)
        val report = Json.parseToJsonElement(body).jsonObject
        assertEquals("handled", report["kind"]?.jsonPrimitive?.content)
        assertEquals("player", report["app"]?.jsonPrimitive?.content)
        assertEquals("editor", report["role"]?.jsonPrimitive?.content)
        assertEquals("server_error", report["reason"]?.jsonPrimitive?.content)
        assertEquals("500", report["httpStatus"]?.jsonPrimitive?.content)
        assertTrue(report["stackTrace"]!!.jsonPrimitive.content.isNotBlank())
        val event = analytics.events.single { it.first == TelemetryEvent.APP_ERROR }.second
        assertEquals(report["reportId"]?.jsonPrimitive?.content, event[TelemetryParam.REPORT_ID])
    }

    @Test
    fun nothingLeavesBeforeTheUserAccepted() = runTest {
        consent = false
        val reporter = reporter()
        reporter.reportHandled(IllegalStateException("boom"), "Action failed.")
        reporter.recordCrash(IllegalStateException("boom"))

        assertTrue(requests.isEmpty())
        assertTrue(analytics.events.isEmpty())
        assertNull(store.saved)
    }

    @Test
    fun aCrashWaitsInTheStoreAndLeavesAtTheNextStartWithOneReportId() = runTest {
        reporter().recordCrash(IllegalArgumentException("fatal"))
        assertTrue(requests.isEmpty())
        val savedId = Json.parseToJsonElement(store.saved!!).jsonObject["reportId"]?.jsonPrimitive?.content

        reporter().flushPending()

        awaitRequests(1)
        val report = Json.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("crash", report["kind"]?.jsonPrimitive?.content)
        assertEquals(savedId, report["reportId"]?.jsonPrimitive?.content)
        assertEquals(savedId, analytics.events.single { it.first == TelemetryEvent.APP_CRASH }.second[TelemetryParam.REPORT_ID])
        assertNull(store.saved)
    }

    @Test
    fun aCancelledJobIsNotAnError() = runTest {
        reporter().reportHandled(kotlinx.coroutines.CancellationException("cancelled"), "Action failed.")
        assertTrue(requests.isEmpty())
    }
}
