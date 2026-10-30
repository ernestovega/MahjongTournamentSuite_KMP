package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeTelemetry(override val requiresConsent: Boolean = true, private var consent: Boolean = false) : Telemetry {
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
    override fun setConsent(granted: Boolean) {
        consent = granted
    }
}

@OptIn(ExperimentalTestApi::class)
class TelemetryUiTest {
    @Test
    fun theAppStaysClosedUntilTheUserAccepts() = runComposeUiTest {
        val telemetry = FakeTelemetry()
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertDoesNotExist()
        onNodeWithText("Anonymous analytics").assertExists()

        onNodeWithText("Decline").performClick()
        onNodeWithText("Inside the app").assertDoesNotExist()
        onNodeWithText("You cannot use the app without the anonymous analytics.", substring = true).assertExists()
        assertFalse(telemetry.hasConsent())

        onNodeWithText("Accept and continue").performClick()
        onNodeWithText("Inside the app").assertExists()
        assertTrue(telemetry.hasConsent())
    }

    @Test
    fun anAppThatSavedTheAnswerOpensAtOnce() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTelemetry provides FakeTelemetry(consent = true)) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertExists()
    }

    @Test
    fun aPlatformThatSendsNothingDoesNotAsk() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalTelemetry provides FakeTelemetry(requiresConsent = false)) { ConsentGate { Text("Inside the app") } }
        }

        onNodeWithText("Inside the app").assertExists()
        onNodeWithText("Anonymous analytics").assertDoesNotExist()
    }

    @Test
    fun withdrawingTheConsentClosesTheAppAgain() = runComposeUiTest {
        val telemetry = FakeTelemetry(consent = true)
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                ConsentGate {
                    val withdraw = LocalWithdrawConsent.current
                    Text("Inside the app")
                    androidx.compose.material3.TextButton(onClick = { withdraw?.invoke() }) { Text("Withdraw") }
                }
            }
        }

        onNodeWithText("Withdraw").performClick()
        onNodeWithText("Anonymous analytics").assertExists()
        assertFalse(telemetry.hasConsent())
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
