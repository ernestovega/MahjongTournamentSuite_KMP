package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateStatisticsUseCase
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** The "By round" cards: their views, the note of the cumulative skills, and the rounds that are not played yet. */
@OptIn(ExperimentalTestApi::class)
class RoundsCardsUiTest {
    private val players = (1..4).map { TournamentPlayer(id = it, name = "Player $it", team = 1) }
    private val names = players.associate { it.id to "Player ${it.id}" }

    /** Three played rounds of five, one table each. */
    private val rounds = (1..5).toList()

    private fun tables(): List<RankingTable> = (1..3).map { round ->
        val table = TableState(
            roundId = round,
            tableId = 1,
            playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "${20 * round - 30}", playerSouthScore = "10", playerWestScore = "-10", playerNorthScore = "${30 - 20 * round}",
            isCompleted = true,
            useTotalsOnly = false,
        )
        val hands = (1..(8 + round)).map { id ->
            TableHand(
                handId = id,
                playerWinnerId = if (id % 5 == 0) "" else ((id % 4) + 1).toString(),
                playerLooserId = if (id % 3 == 0) "" else (((id + 1) % 4) + 1).toString(),
                handScore = if (id % 5 == 0) "" else (8 + id * round).toString(),
                isChickenHand = false,
                isDone = true,
                playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
            )
        }
        RankingTable(table, hands)
    }

    private fun snapshot(name: String, image: ImageBitmap) {
        val dir = System.getenv("MTS_SCREENSHOT_DIR") ?: return
        ImageIO.write(image.toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.show(dark: Boolean = false, content: @Composable () -> Unit) = setContent {
        MtsTheme(useDarkTheme = dark) {
            Surface {
                Column(Modifier.width(400.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
            }
        }
    }

    @Test
    fun playerCardShowsEveryViewWithoutChips() = runDesktopComposeUiTest(width = 432, height = 2000) {
        val player = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        show { PlayerRoundsCard(player.byRound(rounds), places = mapOf(1 to 4, 2 to 2, 3 to 1), playerCount = 4) }
        onNodeWithText("By round").assertExists()
        // Every view is there at the same time, and the last played round is selected in all of them.
        listOf(RoundView.PLACE, RoundView.POINTS, RoundView.SKILLS, RoundView.HANDS).forEach { onNodeWithTag(roundViewTag(it)).assertExists() }
        onNodeWithContentDescription("Round 3 · Place 1 of 4 · 4 table points").assertExists()
        onNodeWithContentDescription("Round 3 · Table 1 · Place 1 · 4 points · Score 30").assertExists()
        onNodeWithText("Lines: everything up to each round. Dots: that round alone.").assertExists()
        onNodeWithText("By discard (deal-in)").assertExists()
        onNodeWithText("Round 3 · 9 hands").assertExists()
        snapshot("rounds-player", onRoot().captureToImage())
        // A tap on a round without tables selects it everywhere.
        onNodeWithText("R5").performClick()
        onNodeWithText("Round 5 · No finished table yet").assertExists()
        assertEquals(2, onAllNodesWithContentDescription("Round 5 · No finished table yet").fetchSemanticsNodes().size)
    }

    @Test
    fun playerCardWithoutPlacesHasNoPlaceView() = runComposeUiTest {
        val player = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 2 }
        show { PlayerRoundsCard(player.byRound(rounds), places = null, playerCount = 4) }
        onNodeWithTag(roundViewTag(RoundView.PLACE)).assertDoesNotExist()
        onNodeWithTag(roundViewTag(RoundView.POINTS)).assertExists()
    }

    /** Four tables in each round. The team has one player at each table: slots 1, 5, 9 and 13. */
    private fun teamTables(): List<RankingTable> = (1..3).flatMap { round ->
        (1..4).map { tableId ->
            val ids = (1..4).map { 4 * (tableId - 1) + it }
            // Each round the player of the team ends in a different place at each table.
            val scores = listOf(30, 10, -10, -30).let { base -> List(4) { base[(it + round + tableId) % 4] + round } }
            val table = TableState(
                roundId = round,
                tableId = tableId,
                playerIds = ids,
                playerEastId = "${ids[0]}", playerSouthId = "${ids[1]}", playerWestId = "${ids[2]}", playerNorthId = "${ids[3]}",
                playerEastScore = "${scores[0]}", playerSouthScore = "${scores[1]}", playerWestScore = "${scores[2]}", playerNorthScore = "${scores[3] - 4 * round}",
                isCompleted = true,
                useTotalsOnly = false,
            )
            val hands = (1..(10 + round + tableId)).map { id ->
                val winner = ids[(id + tableId) % 4]
                TableHand(
                    handId = id,
                    playerWinnerId = if ((id + round) % 6 == 0) "" else "$winner",
                    playerLooserId = if (id % 3 == 0) "" else "${ids[(id + tableId + 1 + round % 3) % 4]}",
                    handScore = if ((id + round) % 6 == 0) "" else (8 + (id * (round + tableId)) % 30).toString(),
                    isChickenHand = false,
                    isDone = true,
                    playerEastPenalty = "", playerSouthPenalty = "", playerWestPenalty = "", playerNorthPenalty = "",
                )
            }
            RankingTable(table, hands)
        }
    }

    private fun teamCard(dark: Boolean, name: String) = runDesktopComposeUiTest(width = 432, height = 1950) {
        val slots = (1..16).map { TournamentPlayer(id = it, name = "Player $it", team = (it - 1) % 4 + 1) }
        val statistics = CalculateStatisticsUseCase()(slots, teamTables())
        val memberNames = mapOf(1 to "Ana López", 5 to "Bea Ruiz", 9 to "Carla Gil", 13 to "Diego Sanz")
        val members = statistics.players.filter { it.playerId in memberNames }.sortedBy { it.playerId }
        val combined = com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics(-1, members.flatMap { it.results })
        show(dark) {
            TeamRoundsCard(
                rounds = combined.byRound(rounds),
                members = members.map { memberNames.getValue(it.playerId) to it.byRound(rounds) },
                places = mapOf(1 to 3, 2 to 2, 3 to 1),
                teamCount = 4,
            )
        }
        // The tooltips add up the team and give the share of each player.
        onNodeWithContentDescription("Round 3\nPlace 1 of 4\n", substring = true).assertExists()
        onNodeWithContentDescription("Ana López: 4 points", substring = true).assertExists()
        onNodeWithContentDescription("Ana López: 33 (#1, 4 pts)", substring = true).assertExists()
        // Every player is in the tooltips of the place, the score and the four skills.
        assertEquals(6, onAllNodesWithContentDescription("Diego Sanz: ", substring = true).fetchSemanticsNodes().size)
        memberNames.values.forEach { onNodeWithText(it).assertExists() }
        // The ring of the team adds up the hands of its four tables.
        onNodeWithText("Round 3 · ${combined.results.filter { it.roundId == 3 }.sumOf { it.handsPlayed }} hands").assertExists()
        snapshot(name, onRoot().captureToImage())
    }

    @Test
    fun teamCardOfFourPlayersShowsTheirPointsAndTheirHandsTogether() = teamCard(dark = false, name = "rounds-team")

    @Test
    fun teamCardInTheDarkTheme() = teamCard(dark = true, name = "rounds-team-dark")

    @Test
    fun tournamentCardShowsHandsSkillsAndRecords() = runDesktopComposeUiTest(width = 432, height = 1700) {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        show { TournamentRoundsCard(statistics.byRound(rounds), names) }
        listOf(RoundView.HANDS, RoundView.SKILLS, RoundView.RECORDS).forEach { onNodeWithTag(roundViewTag(it)).assertExists() }
        onNodeWithContentDescription("Round 3 · 1 of 1 tables", substring = true).assertExists()
        onNodeWithText("Lines: everything up to each round. Dots: that round alone.").assertDoesNotExist()
        onNodeWithText("Round 1").assertExists()
        snapshot("rounds-tournament", onRoot().captureToImage())
    }
}
