package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StatisticsViewsUiTest {
    private val players = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 1) }
    private val names = players.associate { it.id to "Player ${it.id}" }

    private fun tables(): List<RankingTable> = (1..3).map { round ->
        val table = TableState(
            roundId = round,
            tableId = 1,
            playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "${20 + round}", playerSouthScore = "10", playerWestScore = "-10", playerNorthScore = "${-20 - round}",
            isCompleted = true,
            useTotalsOnly = false,
        )
        val hands = (1..10).map { id ->
            TableHand(
                handId = id,
                playerWinnerId = ((id % 4) + 1).toString(),
                playerLooserId = if (id % 3 == 0) "" else (((id + 1) % 4) + 1).toString(),
                handScore = (8 + id).toString(),
                isChickenHand = false,
                isDone = true,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        RankingTable(table, hands)
    }

    private fun snapshot(name: String, image: androidx.compose.ui.graphics.ImageBitmap) {
        val dir = System.getenv("MTS_SCREENSHOT_DIR") ?: return
        ImageIO.write(image.toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun playerPanelShowsPlainRows() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        val player = statistics.players.first { it.playerId == 1 }
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(1000.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    PlayerStatisticsPanel(
                        player,
                        header = { PlayerStatisticsHeader(PlayerProfile("ANA GARCIA", "12345", "ES", null)) },
                    )
                }
            }
        }
        onNodeWithText("Totals").assertExists()
        onNodeWithText("Win rate").assertExists()
        onNodeWithText("Records").assertExists()
        onNodeWithText("Highest table score").assertExists()
        onNodeWithText("Highest hand score").assertExists()
        onNodeWithText("Hands won on a table").assertExists()
        onNodeWithText("Highest penalty got").assertExists()
        onNodeWithText("Best Rivals").assertExists()
        onNodeWithText("Tables won in a row").assertExists()
        onNodeWithText("avg. place").assertExists()
        onNodeWithText("EMA 12345").assertExists()
        snapshot("statistics-player", onRoot().captureToImage())
    }

    @Test
    fun tournamentListShowsTheFiveNewestFirstAndLoadsMoreOnRequest() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        // The list comes in play order, the oldest first.
        val items = (1..8).map { number ->
            Tournament(
                id = "t$number", name = "Open $number", isTeams = false, numPlayers = 4, numRounds = 3,
                eventStartDate = "2026-0$number-10",
            ) to statistics
        }
        val asked = mutableListOf<List<String>>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) {
                    PlayerTournamentsSection(items, loadStandings = { ids ->
                        asked += ids
                        ids.associateWith { Standing(2, 8) }
                    })
                }
            }
        }
        waitForIdle()
        // The section asks for the places of the five newest tournaments only.
        assertEquals(listOf(listOf("t8", "t7", "t6", "t5", "t4")), asked)
        assertEquals(5, onAllNodesWithText("2 of 8").fetchSemanticsNodes().size)
        listOf("Open 8", "Open 7", "Open 6", "Open 5", "Open 4").forEach { onNodeWithText(it).assertExists() }
        listOf("Open 3", "Open 2", "Open 1").forEach { onNodeWithText(it).assertDoesNotExist() }
        onNodeWithText("Showing 5 of 8 tournaments. The newest tournaments come first.").assertExists()

        // The button is below the test window, so the test calls its click action and sends no touch.
        onNodeWithText("Show 5 more").performSemanticsAction(SemanticsActions.OnClick)
        listOf("Open 3", "Open 2", "Open 1").forEach { onNodeWithText(it).assertExists() }
        waitForIdle()
        // A new page asks for its own tournaments, not for the first five again.
        assertEquals(listOf(listOf("t8", "t7", "t6", "t5", "t4"), listOf("t3", "t2", "t1")), asked)
    }

    @Test
    fun tournamentAndTablePanelsShowRankingsAndResult() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        val tournament = Tournament(
            id = "t", name = "Madrid Open", isTeams = false, numPlayers = 4, numRounds = 3,
            eventStartDate = "2026-10-10", eventEndDate = "2026-10-12",
        )
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(520.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    TournamentStatisticsHeader(tournament)
                    TournamentStatisticsPanel(statistics, names)
                }
            }
        }
        onNodeWithText("Totals").assertExists()
        onNodeWithText("Madrid Open").assertExists()
        snapshot("statistics-tournament", onRoot().captureToImage())
    }

    @Test
    fun tablePanelShowsOneRowForEachPlayer() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(520.dp).padding(16.dp)) {
                    TableStatisticsPanel(statistics.tables.first(), names)
                }
            }
        }
        onNodeWithText("Result").assertIsDisplayed()
        onNodeWithText("Deal-ins").assertIsDisplayed()
        snapshot("statistics-table", onRoot().captureToImage())
    }

    @Test
    fun searchableDropdownFiltersWhileTyping() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(600.dp).padding(16.dp)) {
                    SearchableDropdownField(
                        "Player", listOf("a" to "María José", "b" to "Pedro", "c" to "O'Brien"), null, { chosen = it },
                    )
                }
            }
        }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("jose")
        waitForIdle()
        onNodeWithText("María José").assertExists()
        onNodeWithText("Pedro").assertDoesNotExist()
        onNodeWithText("María José").performClick()
        waitForIdle()
        assertEquals("a", chosen)
    }

    @Test
    fun tournamentListBarWorksWithTheDropdownAndTheDates() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        val items = (1..5).map { number ->
            Tournament(
                id = "t$number", name = "Open $number", isTeams = false, numPlayers = 4, numRounds = 3,
                eventStartDate = "2026-0$number-10",
            ) to statistics
        }
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) { PlayerTournamentsSection(items) }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction() and hasText("")).performClick()
        waitForIdle()
        onAllNodesWithText("Open 1")[onAllNodesWithText("Open 1").fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
        // The dropdown shows the name and the unfolded card shows it too.
        assertEquals(2, onAllNodesWithText("Open 1").fetchSemanticsNodes().size)
    }

    @Test
    fun scrollingScreenWithTheTournamentListAndTheWidePanelDoesNotCrash() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        val items = (1..12).map { number ->
            Tournament(
                id = "t$number", name = "Open $number", isTeams = false, numPlayers = 4, numRounds = 3,
                eventStartDate = "2026-0${number % 9 + 1}-10",
            ) to statistics
        }
        setContent {
            MtsTheme(useDarkTheme = false) {
                ScreenColumn(maxWidth = Dp.Infinity, scrollable = true) {
                    PlayerStatisticsPanel(statistics, header = { PlayerStatisticsHeader(PlayerProfile("ANA GARCIA", "12345", "ES", null)) })
                    PlayerTournamentsSection(items)
                }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction() and hasText("")).performClick()
        waitForIdle()
    }
}
