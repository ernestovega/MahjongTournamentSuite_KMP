package com.etologic.mahjongtournamentsuite.domain.telemetry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GA4 drops or renames an event or a parameter that breaks its rules, and it does not tell the sender.
 * These tests check the names of the app against the rules, so a bad name fails here and not in the reports.
 * See https://support.google.com/analytics/answer/13316687 for the limits.
 */
class Ga4RulesTest {
    private val name = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val reservedPrefixes = listOf("firebase_", "ga_", "google_", "gtag.")

    private val reservedEvents = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query", "adunit_exposure", "app_clear_data",
        "app_exception", "app_install", "app_remove", "app_store_refund", "app_store_subscription_cancel",
        "app_store_subscription_convert", "app_store_subscription_renew", "app_update", "app_upgrade", "dynamic_link_app_open",
        "dynamic_link_app_update", "dynamic_link_first_open", "error", "first_open", "first_visit", "in_app_purchase",
        "notification_dismiss", "notification_foreground", "notification_open", "notification_receive", "os_update",
        "session_start", "session_start_with_rollout", "user_engagement",
    )
    private val reservedUserProperties = setOf("first_open_time", "last_deep_link_referrer", "user_id", "first_open_after_install")

    private fun assertName(kind: String, value: String, maxLength: Int) {
        assertTrue(value.length <= maxLength, "$kind '$value' has more than $maxLength characters")
        assertTrue(name.matches(value), "$kind '$value' must start with a letter and hold only letters, digits and underscores")
        reservedPrefixes.forEach { assertTrue(!value.startsWith(it), "$kind '$value' starts with the reserved prefix '$it'") }
    }

    @Test
    fun eventNamesFollowTheRules() {
        TelemetryEvent.all.forEach {
            assertName("event", it, maxLength = 40)
            assertTrue(it !in reservedEvents, "event '$it' is a reserved name of GA4")
        }
    }

    @Test
    fun parameterNamesFollowTheRules() {
        TelemetryParam.all.forEach { assertName("parameter", it, maxLength = 40) }
    }

    @Test
    fun userPropertyNamesFollowTheRules() {
        TelemetryProperty.all.forEach {
            assertName("user property", it, maxLength = 24)
            assertTrue(it !in reservedUserProperties, "user property '$it' is a reserved name of GA4")
        }
    }

    @Test
    fun actionAndOutcomeValuesFitTheLimitOfAParameterValue() {
        // A parameter value has at most 100 characters. A longer value is cut.
        (TelemetryAction.all + TelemetryOutcome.all).forEach { assertTrue(it.length <= 100, it) }
        TelemetryAction.all.forEach { assertTrue(name.matches(it), "action '$it' must be a lowercase word with underscores") }
    }

    @Test
    fun noNameIsUsedTwice() {
        assertEquals(TelemetryEvent.all.size, TelemetryEvent.all.toSet().size)
        assertEquals(TelemetryParam.all.size, TelemetryParam.all.toSet().size)
        assertEquals(TelemetryProperty.all.size, TelemetryProperty.all.toSet().size)
        assertEquals(TelemetryAction.all.size, TelemetryAction.all.toSet().size)
    }

    @Test
    fun theListsHoldEveryConstantOfTheFile() {
        // A new constant needs a place in 'all'. This test reads the source file and compares.
        val source = File("src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/domain/telemetry/TelemetryEvent.kt").readText()
        fun constants(objectName: String): Set<String> {
            val body = source.substringAfter("object $objectName {").substringBefore("\n}\n")
            return Regex("""const val \w+ = "([^"]+)"""").findAll(body).map { it.groupValues[1] }.toSet()
        }
        assertEquals(constants("TelemetryEvent"), TelemetryEvent.all.toSet())
        assertEquals(constants("TelemetryParam"), TelemetryParam.all.toSet())
        assertEquals(constants("TelemetryProperty"), TelemetryProperty.all.toSet())
        assertEquals(constants("TelemetryAction"), TelemetryAction.all.toSet())
        assertEquals(constants("TelemetryOutcome"), TelemetryOutcome.all.toSet())
        assertTrue(TelemetryEvent.all.size > 15, "the source scan found too few events")
    }
}
