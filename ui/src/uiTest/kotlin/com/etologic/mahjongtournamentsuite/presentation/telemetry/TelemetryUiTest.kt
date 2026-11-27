package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTelemetry(
    override val requiresConsent: Boolean = true,
    private var consent: Boolean = false,
    private var answered: Boolean = consent,
) : Telemetry {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val screens = mutableListOf<String>()

    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun screen(name: String) {
        screens += name
    }

    override fun error(throwable: Throwable, area: String) = Unit
    override fun userProperty(name: String, value: String?) = Unit
    override fun hasConsent(): Boolean = consent
    override fun hasAnswered(): Boolean = answered
    override fun setConsent(granted: Boolean) {
        consent = granted
        answered = true
    }
}

@OptIn(ExperimentalTestApi::class)
class TelemetryUiTest {
    @Test
    fun acceptingOpensTheAppAndTurnsTheAnalyticsOn() = runComposeUiTest {
        val telemetry = FakeTelemetry()
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertDoesNotExist()
        onNodeWithText("Anonymous analytics").assertExists()

        onNodeWithText("Accept").performClick()
        onNodeWithText("Inside the app").assertExists()
        assertTrue(telemetry.hasConsent())
        assertTrue(telemetry.hasAnswered())
    }

    @Test
    fun decliningOpensTheAppTooAndSendsNothing() = runComposeUiTest {
        val telemetry = FakeTelemetry()
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Decline").performClick()
        onNodeWithText("Inside the app").assertExists()
        onNodeWithText("Anonymous analytics").assertDoesNotExist()
        assertFalse(telemetry.hasConsent())
        assertTrue(telemetry.hasAnswered())
    }

    @Test
    fun theDetailsAreFoldedUntilTheUserAsks() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTelemetry provides FakeTelemetry()) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("It is optional", substring = true).assertExists()
        onNodeWithText("Google Analytics for Firebase", substring = true).assertDoesNotExist()

        onNodeWithTag(CONSENT_DETAILS_TAG).performClick()
        onNodeWithText("Why").assertExists()
        onNodeWithText("Google Analytics for Firebase", substring = true).assertExists()
        onNodeWithText("Never your name", substring = true).assertExists()
        onNodeWithText("In your profile, at any time.").assertExists()

        // The details fold away with an animation.
        onNodeWithTag(CONSENT_DETAILS_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Google Analytics for Firebase", substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun anAppThatSavedTheAnswerOpensAtOnce() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTelemetry provides FakeTelemetry(consent = true)) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertExists()
    }

    @Test
    fun anAppThatSavedADeclineOpensAtOnceAndDoesNotAskAgain() = runComposeUiTest {
        val telemetry = FakeTelemetry(consent = false, answered = true)
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertExists()
        onNodeWithText("Anonymous analytics").assertDoesNotExist()
        assertFalse(telemetry.hasConsent())
    }

    @Test
    fun aPlatformThatSendsNothingDoesNotAsk() = runComposeUiTest {
        var consent: AnalyticsConsent? = null
        setContent {
            CompositionLocalProvider(LocalTelemetry provides FakeTelemetry(requiresConsent = false)) {
                ConsentGate {
                    consent = LocalAnalyticsConsent.current
                    Text("Inside the app")
                }
            }
        }

        onNodeWithText("Inside the app").assertExists()
        onNodeWithText("Anonymous analytics").assertDoesNotExist()
        // Without data there is nothing to switch, so the profile shows no switch.
        assertNull(consent)
    }

    @Test
    fun theAnswerCanChangeInsideTheAppWithoutClosingIt() = runComposeUiTest {
        val telemetry = FakeTelemetry(consent = true)
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                ConsentGate {
                    val consent = LocalAnalyticsConsent.current!!
                    Text(if (consent.enabled) "Analytics on" else "Analytics off")
                    androidx.compose.material3.TextButton(onClick = { consent.onChange(!consent.enabled) }) { Text("Switch") }
                }
            }
        }

        onNodeWithText("Analytics on").assertExists()
        onNodeWithText("Switch").performClick()
        onNodeWithText("Analytics off").assertExists()
        onNodeWithText("Anonymous analytics").assertDoesNotExist()
        assertFalse(telemetry.hasConsent())

        onNodeWithText("Switch").performClick()
        onNodeWithText("Analytics on").assertExists()
        assertTrue(telemetry.hasConsent())
    }

    @Test
    fun screensDialogsAndTabsSendTheirEventsAndTheirParent() = runComposeUiTest {
        val telemetry = FakeTelemetry(consent = true)
        var showDialog by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                TrackedScreen("Statistics") {
                    TrackedTab("Overview")
                    if (showDialog) TrackedDialog("Export") { Text("Export dialog") }
                }
            }
        }
        showDialog = true
        waitForIdle()

        assertEquals(listOf("Statistics"), telemetry.screens)
        val tab = telemetry.events.single { it.first == TelemetryEvent.TAB_VIEW }.second
        assertEquals("Overview", tab[TelemetryParam.TAB])
        assertEquals("Statistics", tab[TelemetryParam.SCREEN])
        val dialog = telemetry.events.single { it.first == TelemetryEvent.DIALOG_VIEW }.second
        assertEquals("Export", dialog[TelemetryParam.DIALOG])
        assertEquals("Statistics", dialog[TelemetryParam.SCREEN])
    }
}
