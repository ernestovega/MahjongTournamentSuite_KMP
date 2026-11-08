package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Analytics part of a platform. It keeps what it gets and answers with the consent of the test. */
private class FakeAnalytics : Telemetry {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val screens = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val properties = mutableListOf<Pair<String, String?>>()
    var consent = true

    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun screen(name: String) {
        screens += name
    }

    override fun error(throwable: Throwable, area: String) {
        errors += throwable
    }

    override fun userProperty(name: String, value: String?) {
        properties += name to value
    }

    override val requiresConsent: Boolean = true

    override fun hasConsent(): Boolean = consent

    override fun setConsent(granted: Boolean) {
        consent = granted
    }
}

private class FakeCrashStore : PendingCrashStore {
    var saved: String? = null

    override fun save(json: String) {
        saved = json
    }

    override fun take(): String? = saved.also { saved = null }
}

class ReportingTelemetryTest {
    private val requests = mutableListOf<String>()
    private val analytics = FakeAnalytics()
    private val store = FakeCrashStore()

    private val client = HttpClient(
        MockEngine { request ->
            requests += request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.NoContent)
        },
    )

    private val reporter = ErrorReporter(
        PlatformTelemetry(analytics, app = "admin", platform = "desktop", env = "prod", language = "en", pendingCrashes = store),
        client,
        ApiConfiguration("https://api.test/api"),
        CoroutineScope(Dispatchers.Unconfined),
    )

    private val telemetry = ReportingTelemetry(analytics, reporter)

    /** The send runs on another thread. The test waits a short time for it. */
    private suspend fun awaitRequests(count: Int) = withContext(Dispatchers.Default) {
        repeat(100) {
            if (requests.size >= count) return@withContext
            delay(20)
        }
    }

    private fun report(index: Int = 0) = Json.parseToJsonElement(requests[index]).jsonObject

    @Test
    fun eventsAndScreensGoStraightToAnalytics() {
        telemetry.event(TelemetryEvent.ADMIN_ACTION, mapOf("action" to "create_player"))
        telemetry.screen("Players")

        assertEquals(listOf(TelemetryEvent.ADMIN_ACTION), analytics.events.map { it.first })
        assertEquals(listOf("Players"), analytics.screens)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aHandledErrorGoesToTheErrorLogAndCountsInAnalyticsOnce() = runTest {
        telemetry.error(IllegalStateException("boom"), "Saving the player failed.")

        awaitRequests(1)
        assertEquals("handled", report()["kind"]?.jsonPrimitive?.content)
        assertEquals(1, analytics.events.count { it.first == TelemetryEvent.APP_ERROR })
        // The reporter sends the Analytics event itself. The plain error call of Analytics would count it twice.
        assertTrue(analytics.errors.isEmpty())
    }

    @Test
    fun theRoleGoesToAnalyticsAndIntoTheNextErrorReports() = runTest {
        telemetry.userProperty(TelemetryProperty.ROLE, "admin")
        telemetry.error(IllegalStateException("boom"), "Action failed.")

        awaitRequests(1)
        assertEquals(listOf<Pair<String, String?>>(TelemetryProperty.ROLE to "admin"), analytics.properties)
        assertEquals("admin", report()["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun removingTheRoleRemovesItFromTheReports() = runTest {
        telemetry.userProperty(TelemetryProperty.ROLE, "admin")
        telemetry.userProperty(TelemetryProperty.ROLE, null)
        telemetry.error(IllegalStateException("boom"), "Action failed.")

        awaitRequests(1)
        assertNull(report()["role"])
    }

    @Test
    fun otherPropertiesDoNotChangeTheRoleOfTheReports() = runTest {
        telemetry.userProperty(TelemetryProperty.LANGUAGE, "es")
        telemetry.error(IllegalStateException("boom"), "Action failed.")

        awaitRequests(1)
        assertEquals(listOf<Pair<String, String?>>(TelemetryProperty.LANGUAGE to "es"), analytics.properties)
        assertNull(report()["role"])
    }

    @Test
    fun acceptingSendsTheCrashOfTheLastRun() = runTest {
        reporter.recordCrash(IllegalArgumentException("fatal"))
        analytics.consent = false

        telemetry.setConsent(true)

        assertTrue(analytics.consent)
        awaitRequests(1)
        assertEquals("crash", report()["kind"]?.jsonPrimitive?.content)
        assertEquals(1, analytics.events.count { it.first == TelemetryEvent.APP_CRASH })
        assertNull(store.saved)
    }

    @Test
    fun refusingKeepsTheCrashAndSendsNothing() = runTest {
        reporter.recordCrash(IllegalArgumentException("fatal"))

        telemetry.setConsent(false)
        telemetry.error(IllegalStateException("boom"), "Action failed.")

        assertFalse(analytics.consent)
        assertTrue(requests.isEmpty())
        assertTrue(analytics.events.isEmpty())
        assertTrue(store.saved != null)
    }

    @Test
    fun oneRunSendsAtMostTwentyHandledErrors() = runTest {
        repeat(30) { telemetry.error(IllegalStateException("loop $it"), "Action failed.") }

        awaitRequests(20)
        // A short wait shows that no more reports follow.
        withContext(Dispatchers.Default) { delay(100) }
        assertEquals(20, requests.size)
        assertEquals(20, analytics.events.count { it.first == TelemetryEvent.APP_ERROR })
    }
}
