package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop app has no Firebase SDK. It posts to the Measurement Protocol of GA4. These tests start a local server
 * in place of Google and read what the app sends: the address, the consent, the body and the properties.
 */
class DesktopTelemetryTest {
    private class Hit(val query: String, val body: JsonObject)

    private val hits = LinkedBlockingQueue<Hit>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/mp/collect") { exchange ->
            hits.add(Hit(exchange.requestURI.rawQuery.orEmpty(), Json.parseToJsonElement(exchange.requestBody.readBytes().decodeToString()).jsonObject))
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        start()
    }
    private val node = "MahjongTournamentSuiteTest-${UUID.randomUUID()}"

    private fun telemetry(measurementId: String = "G-TEST", apiSecret: String = "secret", env: String = "prod") = DesktopTelemetry(measurementId, apiSecret, env, endpoint = "http://127.0.0.1:${server.address.port}/mp/collect", preferencesNode = node)

    private fun nextHit(): Hit = hits.poll(5, TimeUnit.SECONDS) ?: error("GA4 got no request")

    private fun noMoreHits() = assertNull(hits.poll(300, TimeUnit.MILLISECONDS), "GA4 got a request that nobody expected")

    private fun Hit.event() = body.getValue("events").jsonArray.single().jsonObject

    @AfterTest
    fun stop() {
        server.stop(0)
        runCatching { Preferences.userRoot().node(node).removeNode() }
    }

    @Test
    fun sendsNothingBeforeTheUserAccepts() {
        val telemetry = telemetry()
        assertTrue(telemetry.requiresConsent)
        assertEquals(false, telemetry.hasConsent())

        telemetry.event("login")
        telemetry.screen("Tournaments")

        noMoreHits()
    }

    @Test
    fun sendsTheEventToTheCollectAddressWithTheMeasurementIdAndTheSecret() {
        val telemetry = telemetry(measurementId = "G-ABC123", apiSecret = "s3cret")
        telemetry.setConsent(true)

        telemetry.event("login")

        assertEquals("measurement_id=G-ABC123&api_secret=s3cret", nextHit().query)
    }

    @Test
    fun theBodyHasTheClientTheEventTheParametersAndTheValuesThatGa4NeedsToCountIt() {
        val telemetry = telemetry()
        telemetry.setConsent(true)

        telemetry.event("table_saved", mapOf("hands" to 8, "kind" to "full", "is_teams" to true))

        val hit = nextHit()
        val event = hit.event()
        assertEquals("table_saved", event.getValue("name").jsonPrimitive.content)
        val params = event.getValue("params").jsonObject
        assertEquals("8", params.getValue("hands").jsonPrimitive.content)
        assertEquals("full", params.getValue("kind").jsonPrimitive.content)
        assertEquals("true", params.getValue("is_teams").jsonPrimitive.content)
        // Without these two values GA4 does not count the event in the standard reports.
        assertTrue(params.getValue("session_id").jsonPrimitive.content.toLong() > 0)
        assertEquals("1", params.getValue("engagement_time_msec").jsonPrimitive.content)
        assertEquals(AppVersion.name, params.getValue("app_version").jsonPrimitive.content)
        assertTrue(hit.body.getValue("client_id").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun aScreenIsAScreenViewEvent() {
        val telemetry = telemetry()
        telemetry.setConsent(true)

        telemetry.screen("Tournaments")

        val event = nextHit().event()
        assertEquals("screen_view", event.getValue("name").jsonPrimitive.content)
        assertEquals("Tournaments", event.getValue("params").jsonObject.getValue("screen_name").jsonPrimitive.content)
    }

    @Test
    fun everyEventCarriesTheUserProperties() {
        val telemetry = telemetry(env = "dev")
        telemetry.setConsent(true)
        telemetry.userProperty("role", "admin")
        telemetry.userProperty("app", "admin")

        telemetry.event("login")

        val properties = nextHit().body.getValue("user_properties").jsonObject
        assertEquals("desktop", properties.value("platform"))
        assertEquals("dev", properties.value("env"))
        assertEquals("admin", properties.value("role"))
        assertEquals("admin", properties.value("app"))
        assertTrue(properties.value("language").isNotBlank())
    }

    @Test
    fun aNullValueRemovesAProperty() {
        val telemetry = telemetry()
        telemetry.setConsent(true)
        telemetry.userProperty("role", "admin")
        telemetry.userProperty("role", null)

        telemetry.event("login")

        assertNull(nextHit().body.getValue("user_properties").jsonObject["role"])
    }

    @Test
    fun withdrawingTheConsentStopsTheDataAtOnce() {
        val telemetry = telemetry()
        telemetry.setConsent(true)
        telemetry.setConsent(false)

        telemetry.event("login")

        noMoreHits()
        assertEquals(false, telemetry.hasConsent())
    }

    @Test
    fun theConsentAndTheClientIdSurviveARestart() {
        val first = telemetry()
        first.setConsent(true)
        first.event("login")
        val clientId = nextHit().body.getValue("client_id").jsonPrimitive.content

        val second = telemetry()
        assertTrue(second.hasConsent())
        second.event("login")

        assertEquals(clientId, nextHit().body.getValue("client_id").jsonPrimitive.content)
    }

    @Test
    fun anotherInstallationHasAnotherClientId() {
        val first = telemetry()
        first.setConsent(true)
        first.event("login")
        val clientId = nextHit().body.getValue("client_id").jsonPrimitive.content

        val other = DesktopTelemetry(
            "G-TEST",
            "secret",
            "prod",
            endpoint = "http://127.0.0.1:${server.address.port}/mp/collect",
            preferencesNode = "$node-other",
        )
        try {
            other.setConsent(true)
            other.event("login")
            assertNotEquals(clientId, nextHit().body.getValue("client_id").jsonPrimitive.content)
        } finally {
            runCatching { Preferences.userRoot().node("$node-other").removeNode() }
        }
    }

    @Test
    fun aHandledErrorIsNotSentByThisClass() {
        // The reporter of the data module sends the error to the log of the API and counts it as an event.
        val telemetry = telemetry()
        telemetry.setConsent(true)

        telemetry.error(IllegalStateException("boom"), "Saving")

        noMoreHits()
    }

    @Test
    fun anUnreachableServerNeverThrows() {
        val telemetry = DesktopTelemetry("G-TEST", "secret", "prod", endpoint = "http://127.0.0.1:1/mp/collect", preferencesNode = node)
        telemetry.setConsent(true)

        telemetry.event("login")
        telemetry.screen("Tournaments")
    }

    @Test
    fun withoutAMeasurementIdOrASecretTheAppUsesNoAnalytics() {
        assertEquals(NoTelemetry, desktopAnalytics("", "secret", "prod"))
        assertEquals(NoTelemetry, desktopAnalytics("G-TEST", "", "prod"))
        assertEquals(NoTelemetry, desktopAnalytics("  ", "  ", "prod"))
        assertIs<DesktopTelemetry>(desktopAnalytics("G-TEST", "secret", "prod"))
    }

    private fun JsonObject.value(name: String) = getValue(name).jsonObject.getValue("value").jsonPrimitive.content
}
