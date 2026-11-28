package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.platform.IosAnalytics
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Firebase Analytics on iOS: nothing leaves the device before the user accepts, and the answer is kept. */
class IosTelemetryTest {
    private class FakeAnalytics : IosAnalytics {
        val collection = mutableListOf<Boolean>()
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val properties = mutableMapOf<String, String?>()

        override fun setCollectionEnabled(enabled: Boolean) {
            collection += enabled
        }

        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }

        override fun setUserProperty(name: String, value: String?) {
            properties[name] = value
        }
    }

    private val defaults = NSUserDefaults.standardUserDefaults
    private val analytics = FakeAnalytics()

    @BeforeTest
    @AfterTest
    fun forgetTheAnswer() {
        defaults.removeObjectForKey("mts.telemetry.consent")
        defaults.removeObjectForKey("mts.telemetry.pendingCrash")
        IosPlatform.analytics = null
        IosPlatform.currentApp = null
    }

    private fun telemetry() = IosTelemetry(analytics, "ios", "prod", "es")

    @Test
    fun beforeTheAnswerNothingIsCollectedOrSent() {
        val telemetry = telemetry()
        assertTrue(telemetry.requiresConsent)
        assertFalse(telemetry.hasAnswered())
        assertFalse(telemetry.hasConsent())
        assertEquals(listOf(false), analytics.collection)
        telemetry.event("tournament_opened", mapOf("players" to 16))
        telemetry.screen("Tournaments")
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun theFactsOfTheDeviceGoAsUserProperties() {
        telemetry()
        assertEquals("ios", analytics.properties[TelemetryProperty.PLATFORM])
        assertEquals("prod", analytics.properties[TelemetryProperty.ENVIRONMENT])
        assertEquals("es", analytics.properties[TelemetryProperty.LANGUAGE])
    }

    @Test
    fun afterAcceptingEventsAndScreensAreSent() {
        val telemetry = telemetry()
        telemetry.setConsent(true)
        assertEquals(listOf(false, true), analytics.collection)
        telemetry.event("hand_saved", mapOf("players" to 4, "points" to 25L, "table" to "T1"))
        telemetry.screen("Ranking")
        assertEquals(
            listOf(
                "hand_saved" to mapOf<String, Any>("players" to 4L, "points" to 25L, "table" to "T1"),
                "screen_view" to mapOf<String, Any>("screen_name" to "Ranking", "screen_class" to "Ranking"),
            ),
            analytics.events,
        )
    }

    @Test
    fun aLongTextIsCutToWhatAnalyticsTakes() {
        val telemetry = telemetry()
        telemetry.setConsent(true)
        telemetry.event("error_shown", mapOf("message" to "x".repeat(150), "ok" to true))
        val params = analytics.events.single().second
        assertEquals("x".repeat(100), params["message"])
        assertEquals("true", params["ok"])
    }

    @Test
    fun theAnswerIsKeptForTheNextStart() {
        telemetry().setConsent(true)
        val next = IosTelemetry(FakeAnalytics(), "ios", "prod", "es")
        assertTrue(next.hasAnswered())
        assertTrue(next.hasConsent())
    }

    @Test
    fun withdrawingStopsTheCollection() {
        val telemetry = telemetry()
        telemetry.setConsent(true)
        telemetry.setConsent(false)
        telemetry.event("hand_saved", emptyMap())
        assertEquals(listOf(false, true, false), analytics.collection)
        assertTrue(analytics.events.isEmpty())
        assertTrue(telemetry.hasAnswered())
        assertFalse(IosTelemetry(FakeAnalytics(), "ios", "prod", "es").hasConsent())
    }

    @Test
    fun aCrashIsKeptUntilTheNextStartAndSentOnce() {
        assertNull(IosPendingCrashStore.take())
        IosPendingCrashStore.save("""{"message":"boom"}""")
        assertEquals("""{"message":"boom"}""", IosPendingCrashStore.take())
        assertNull(IosPendingCrashStore.take())
    }

    @Test
    fun withoutFirebaseTheAppSendsNoAnalytics() {
        val platform = providePlatformTelemetry()
        assertSame(NoTelemetry, platform.analytics)
        assertEquals("admin", platform.app)
        assertEquals("ios", platform.platform)
        assertEquals("prod", platform.env)
        assertEquals(AppVersion.admin, platform.appVersion)
        assertSame(IosPendingCrashStore, platform.pendingCrashes)
    }

    @Test
    fun withFirebaseThePartOnTheScreenAndItsAnalyticsGoIn() {
        IosPlatform.analytics = analytics
        IosPlatform.currentApp = "player"
        val platform = providePlatformTelemetry()
        assertEquals("player", platform.app)
        assertTrue(platform.analytics is IosTelemetry)
        assertEquals("ios", analytics.properties[TelemetryProperty.PLATFORM])
    }
}
