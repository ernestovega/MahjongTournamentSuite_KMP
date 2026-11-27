package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.player.screenshots.PlayerFrame
import com.etologic.mahjongtournamentsuite.player.screenshots.playerModules
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotData
import com.etologic.mahjongtournamentsuite.screenshots.fake.ScreenshotTournamentRepository
import kotlin.test.Test

/** A tournament on screen keeps its data when the checks fail, and after three failed checks in a row it says how old the data is. */
@OptIn(ExperimentalTestApi::class)
class StaleDataUiTest {
    private class FailingLater : ScreenshotTournamentRepository() {
        var failing = false
        override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = if (failing) AppResult.Failure(AppError.Network) else super.listTournaments(refreshMode)
    }

    @Test
    fun afterThreeFailedChecksTheScreenSaysHowOldTheDataIs() = runComposeUiTest {
        val server = FailingLater()
        setContent {
            PlayerFrame(modules = { playerModules(tournaments = server) }, dark = false) {
                val (view, message) = rememberTournamentView(ScreenshotData.ONGOING)
                Text("view ${view?.tournament?.id} ${message?.asString().orEmpty()}")
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("view ${ScreenshotData.ONGOING}", substring = true).fetchSemanticsNodes().isNotEmpty() }
        server.failing = true

        // The checks of an ongoing tournament come at most every few minutes.
        repeat(3) { mainClock.advanceTimeBy(10 * 60_000L) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("The data has not updated for", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aFirstLoadThatFailsSaysWhy() = runComposeUiTest {
        val server = FailingLater().apply { failing = true }
        setContent {
            PlayerFrame(modules = { playerModules(tournaments = server) }, dark = false) {
                val (_, message) = rememberTournamentView(ScreenshotData.ONGOING)
                Text("message ${message?.asString().orEmpty()}")
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Network", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
