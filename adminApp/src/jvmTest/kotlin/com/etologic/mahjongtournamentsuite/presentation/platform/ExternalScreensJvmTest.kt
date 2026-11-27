package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import com.etologic.mahjongtournamentsuite.StandaloneWindow
import com.etologic.mahjongtournamentsuite.StandaloneWindows
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** On the desktop the timer, the ranking and the QR of the tables open in windows of their own, not in the main one. */
@OptIn(ExperimentalTestApi::class)
class ExternalScreensJvmTest {
    @Test
    fun theTimerTheRankingAndTheQrOpenWindowsOfTheirOwn() = runComposeUiTest {
        var opened = false
        setContent {
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                openTimer(navController, tournamentId = "t1", initialRound = 2)
                openRankings(navController, tournamentId = "t1")
                openTableQrWindow("Open", "https://example.test/t1", logoUrl = null, organizerName = "Club", organizerColor = "#123456")
                opened = true
            }
        }
        waitUntil(timeoutMillis = 5_000) { opened }
        assertTrue(StandaloneWindows.hasOpenWindows)
    }

    @Test
    fun eachWindowHasItsOwnId() {
        assertNotEquals(StandaloneWindow.Timer("t1", 1).id, StandaloneWindow.Timer("t1", 1).id)
        assertNotEquals(StandaloneWindow.Rankings("t1").id, StandaloneWindow.Rankings("t1").id)
        assertNotEquals(StandaloneWindow.TableQr("Open", "l", null, "Club", "#000").id, StandaloneWindow.TableQr("Open", "l", null, "Club", "#000").id)
    }
}
