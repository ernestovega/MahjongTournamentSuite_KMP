package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics

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
        onNodeWithText("Best table score").assertExists()
        onNodeWithText("Highest hand value").assertExists()
        onNodeWithText("Most hands won at one table").assertExists()
        onNodeWithText("Highest penalty got").assertExists()
        onNodeWithText("Best Rivals").assertExists()
        onNodeWithText("Most tables won in a row").assertExists()
        onNodeWithText("avg. place").assertExists()
        onNodeWithText("EMA 12345").assertExists()
        snapshot("statistics-player", onRoot().captureToImage())
    }

    @Test
    fun tournamentListShowsEveryTournamentWithItsSummaryAndFiltersByYear() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        // The list comes in play order, the oldest first: three tournaments in 2025 and five in 2026.
        val items = (1..8).map { number ->
            Tournament(
                id = "t$number", name = "Open $number", isTeams = false, numPlayers = 4, numRounds = 3,
                eventStartDate = if (number <= 3) "2025-0$number-10" else "2026-0$number-10",
            ) to statistics
        }
        val asked = mutableListOf<List<String>>()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) {
                    PlayerTournamentsSection(items, loadStandings = { ids ->
                        asked += ids
                        ids.associateWith { Standing(2, 8, 3.5, 40) }
                    })
                }
            }
        }
        waitForIdle()
        // Every tournament shows, the newest first, and the places of all of them load in one call.
        assertEquals(listOf((8 downTo 1).map { "t$it" }), asked)
        (1..8).forEach { onNodeWithText("Open $it").assertExists() }
        // Every card says the place under its name, folded or not.
        assertEquals(8, onAllNodesWithText("2 of 8").fetchSemanticsNodes().size)
        // Every card starts folded, the newest too: no card shows its round chips.
        onNodeWithText("Totals").assertDoesNotExist()
        listOf("All", "2026", "2025").forEach { onNodeWithText(it).assertExists() }

        // A year shows only its tournaments.
        onNodeWithText("2025").performClick()
        waitForIdle()
        (1..3).forEach { onNodeWithText("Open $it").assertExists() }
        (4..8).forEach { onNodeWithText("Open $it").assertDoesNotExist() }
        // Choosing a year does not open any card.
        onNodeWithText("Totals").assertDoesNotExist()
        // A tap on the chosen year shows all the years again.
        onNodeWithText("2025").performClick()
        waitForIdle()
        onNodeWithText("Open 8").assertExists()
        assertEquals(1, asked.size)
    }

    @Test
    fun theTournamentBarShowsOnlyTheChosenTournament() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        val items = (1..4).map { number ->
            Tournament(
                id = "t$number",
                name = "Open $number",
                isTeams = false,
                numPlayers = 4,
                numRounds = 3,
                eventStartDate = "202$number-05-10",
            ) to statistics
        }
        setContent {
            var chosen by remember { mutableStateOf<String?>(null) }
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) {
                    ChosenTournamentBar(items.map { it.first }, chosen, { chosen = it }, "Change", 720.dp)
                    PlayerTournamentsSection(items, tournamentId = chosen)
                }
            }
        }
        waitForIdle()
        // At the start the bar says "All tournaments" and every tournament shows, with the year chips.
        onNodeWithText("All tournaments").assertExists()
        (1..4).forEach { onNodeWithText("Open $it").assertExists() }
        onNodeWithText("2022").assertExists()

        onNodeWithText("Change").performClick()
        onNodeWithText("Tournament").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Open 2").fetchSemanticsNodes().size > 1 }
        onAllNodesWithText("Open 2").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        waitForIdle()
        // The bar shows the chosen tournament, and the list shows only that one, without the year chips.
        assertEquals(2, onAllNodesWithText("Open 2").fetchSemanticsNodes().size)
        listOf("Open 1", "Open 3", "Open 4", "2022").forEach { onNodeWithText(it).assertDoesNotExist() }
        // The chosen tournament starts folded too.
        onNodeWithText("Totals").assertDoesNotExist()
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
        onNodeWithText("Totals").assertDoesNotExist()
        onNodeWithText("Records").assertExists()
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
    fun anOpenOrTotalsOnlyTableAndAShortChartSaySo() = runComposeUiTest {
        val table = CalculateStatisticsUseCase()(players, tables()).tables.first()
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(520.dp).padding(16.dp)) {
                    TableStatisticsPanel(table.copy(isCompleted = false), names)
                    TableStatisticsPanel(table.copy(hasHands = false), emptyMap())
                    LineChart(listOf("Player 1" to listOf(3)))
                }
            }
        }
        onNodeWithText("This table is not completed yet.").assertExists()
        onNodeWithText("This table has no entered hands, so it has no hand statistics.").assertExists()
        onNodeWithText("Not enough data for a chart.").assertExists()
    }

    @Test
    fun theHeadersShowThePhotoAndTheLogoOrAnEmptyPlace() = runComposeUiTest {
        var chosen by mutableStateOf<Int?>(null)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column {
                    PlayerStatisticsHeader(PlayerProfile("Ana", emaId = "", country = "", photoUrl = "https://example.test/ana.png"))
                    PlayerStatisticsHeader(PlayerProfile("Guest", emaId = "", country = "", photoUrl = null), fillWidth = false)
                    PlayerCountryAndEma(country = "", emaId = "")
                    PlayerCountryAndEma(country = "ES", emaId = "01000001")
                    TournamentStatisticsHeader(Tournament(id = "t1", name = "Logo Open", isTeams = false, numPlayers = 4, numRounds = 1, associationLogoUrl = "https://example.test/logo.png"))
                    PlayerDropdownField(names, chosen, { chosen = it })
                }
            }
        }
        onNodeWithContentDescription("Photo of Ana").assertExists()
        onNodeWithContentDescription("No player photo").assertExists()
        onNodeWithContentDescription("Logo of Logo Open").assertExists()
        onAllNodesWithText("Player")[0].performClick()
        waitForIdle()
        onAllNodesWithText("Player 3").onLast().performClick()
        waitForIdle()
        assertEquals(3, chosen)
    }

    @Test
    fun searchableDropdownFiltersWhileTyping() = runComposeUiTest {
        var chosen: String? = null
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(600.dp).padding(16.dp)) {
                    SelectField(
                        "Player",
                        listOf("a" to "María José", "b" to "Pedro", "c" to "O'Brien") + (1..9).map { "n$it" to "Name $it" },
                        null, { chosen = it },
                    )
                }
            }
        }
        onNodeWithText("Player").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Pedro").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("select-search").performTextInput("jose")
        waitForIdle()
        onNodeWithText("María José").assertExists()
        onNodeWithText("Pedro").assertDoesNotExist()
        onNodeWithText("María José").performClick()
        waitForIdle()
        assertEquals("a", chosen)
    }

    @Test
    fun aRoundChipOfATournamentCardShowsOnlyThatRound() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        val items = listOf(Tournament(id = "t1", name = "Open 1", isTeams = false, numPlayers = 4, numRounds = 3, eventStartDate = "2026-01-10") to statistics)
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(900.dp).padding(16.dp)) {
                    PlayerTournamentsSection(items, loadStandings = { ids -> ids.associateWith { Standing(2, 8, 3.5, 66) } })
                }
            }
        }
        waitForIdle()
        // Even a single card starts folded. A tap on it opens it.
        onNodeWithText("Totals").assertDoesNotExist()
        onNodeWithText("Open 1").performClick()
        waitForIdle()
        // Totals shows the place and the score of the whole tournament.
        listOf("Totals", "R1", "R2", "R3").forEach { onNodeWithText(it).assertExists() }
        // The place and the score of the whole tournament show in the summary under the name.
        onNodeWithText("2 of 8").assertExists()
        onNodeWithText("Score 66").assertExists()
        // Round 2 shows only its own table: 22 points of score. The summary keeps the whole tournament.
        onNodeWithText("R2").performClick()
        waitForIdle()
        onNodeWithText("2 of 8").assertExists()
        assertTrue(onAllNodesWithText("22").fetchSemanticsNodes().isNotEmpty())
        // A tap on the chosen round goes back to the whole tournament.
        onNodeWithText("R2").performClick()
        waitForIdle()
        onNodeWithText("Score 66").assertExists()
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
        onNodeWithText("Open 1").assertExists()
    }

    @Test
    fun tablesWithoutHandsAreExplainedUnderThePanel() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        var shown by mutableStateOf(statistics.copy(tablesWithHands = 0))
        setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.width(520.dp).padding(16.dp)) {
                    TournamentStatisticsPanel(shown, names, tournamentCount = 2)
                }
            }
        }
        onNodeWithText("Hand statistics need tables with entered hands. No table here has them.").assertExists()

        shown = statistics.copy(tablesTotal = 4, tablesCompleted = 3, tablesWithHands = 1)
        waitForIdle()
        onNodeWithText("Hand values use 1 of 3 tables.", substring = true).assertExists()
        onNodeWithText("3 of 4").assertExists()
    }
}
