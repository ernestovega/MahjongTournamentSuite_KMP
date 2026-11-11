package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The place history marks which tables belong to which tournament. */
@OptIn(ExperimentalTestApi::class)
class PlaceHistoryTournamentsUiTest {
    private fun result(tournament: String, round: Int, position: Int) = TableResult(
        tournamentId = tournament, roundId = round, tableId = 1, seat = 0, position = position, score = 0, points = 0.0, hasHands = false,
    )

    private val results = listOf(
        result("a", 1, 1), result("a", 2, 3), result("a", 3, 2),
        result("b", 1, 4), result("b", 2, 2),
        result("c", 1, 1), result("c", 2, 1), result("c", 3, 3), result("c", 4, 2),
    )

    private val tournaments = listOf(
        Tournament(id = "a", name = "Madrid Open", shortName = "MAD 25", isTeams = false, numPlayers = 4, numRounds = 3, eventStartDate = "2025-03-01"),
        Tournament(id = "b", name = "Copa", shortName = "COPA", isTeams = false, numPlayers = 4, numRounds = 2, eventStartDate = "2025-06-01"),
        Tournament(id = "c", name = "Open de Italia", shortName = "", isTeams = false, numPlayers = 4, numRounds = 4, eventStartDate = "2026-01-10"),
    )

    @Test
    fun theTablesOfEachTournamentFormOneRun() {
        assertEquals(
            listOf(TournamentRun("a", 0, 2), TournamentRun("b", 3, 4), TournamentRun("c", 5, 8)),
            tournamentRuns(results),
        )
        assertEquals(listOf(TournamentRun("a", 0, 1)), tournamentRuns(results.take(2)))
        // A tournament that comes back after another one is a new run.
        assertEquals(3, tournamentRuns(listOf(result("a", 1, 1), result("b", 1, 1), result("a", 2, 1))).size)
    }

    @Test
    fun theChartDrawsTheTournamentBands() = runComposeUiTest {
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) {
                    PlayerStatisticsPanel(PlayerStatistics(1, results), tournaments = tournaments)
                }
            }
        }
        waitForIdle()
        System.getenv("MTS_SCREENSHOT_DIR")?.let { dir ->
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(dir, "place-history-tournaments.png"))
        }
        // The bands and names are drawn on the canvas. With MTS_SCREENSHOT_DIR set, the test saves the picture to check by eye.
        onAllNodesWithText("Place in the last 9 tables", substring = true, ignoreCase = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }
}
