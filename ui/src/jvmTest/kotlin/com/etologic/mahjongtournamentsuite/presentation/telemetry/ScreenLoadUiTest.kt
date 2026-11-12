package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class LoadRecorder : Telemetry by NoTelemetry {
    val loads = mutableListOf<Map<String, Any>>()

    override fun event(name: String, params: Map<String, Any>) {
        if (name == TelemetryEvent.SCREEN_LOAD) loads += params
    }
}

/** A screen sends how long it took to show its content: the first load, and each later load such as a tab change. */
@OptIn(ExperimentalTestApi::class)
class ScreenLoadUiTest {
    @Test
    fun theFirstLoadCountsFromTheOpeningOfTheScreen() = runComposeUiTest {
        val telemetry = LoadRecorder()
        var loading by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                TrackedScreen("Tournament") {
                    TrackedTab("Ranking")
                    ReportScreenLoading(loading)
                }
            }
        }
        mainClock.advanceTimeBy(300)
        loading = false
        mainClock.advanceTimeBy(500)

        val load = telemetry.loads.single()
        assertEquals("open", load[TelemetryParam.KIND])
        assertEquals("Tournament", load[TelemetryParam.SCREEN])
        assertEquals("Ranking", load[TelemetryParam.TAB])
        assertTrue((load[TelemetryParam.MILLISECONDS] as Long) >= 0)
    }

    @Test
    fun aLoadThatStartsAgainAtOnceIsOneLoad() = runComposeUiTest {
        val telemetry = LoadRecorder()
        var loading by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                TrackedScreen("Tournament") { ReportScreenLoading(loading) }
            }
        }
        mainClock.advanceTimeBy(100)
        loading = false
        mainClock.advanceTimeBy(16)
        loading = true
        mainClock.advanceTimeBy(200)
        assertTrue(telemetry.loads.isEmpty(), "The screen is still loading")

        loading = false
        mainClock.advanceTimeBy(500)
        assertEquals(listOf("open"), telemetry.loads.map { it[TelemetryParam.KIND] })
    }

    @Test
    fun aLaterLoadIsATabLoad() = runComposeUiTest {
        val telemetry = LoadRecorder()
        var loading by mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) {
                TrackedScreen("Tournament") { ReportScreenLoading(loading) }
            }
        }
        mainClock.advanceTimeBy(500)
        loading = true
        mainClock.advanceTimeBy(200)
        loading = false
        mainClock.advanceTimeBy(500)

        assertEquals(listOf("open", "tab"), telemetry.loads.map { it[TelemetryParam.KIND] })
        assertEquals("Tournament", telemetry.loads.last()[TelemetryParam.SCREEN])
    }

    @Test
    fun aScreenWithoutTrackingSendsNothing() = runComposeUiTest {
        val telemetry = LoadRecorder()
        setContent {
            CompositionLocalProvider(LocalTelemetry provides telemetry) { ReportScreenLoading(false) }
        }
        waitForIdle()

        assertTrue(telemetry.loads.isEmpty())
    }
}
