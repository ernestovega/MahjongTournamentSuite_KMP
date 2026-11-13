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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
    fun playerCardOpensOnThePlaceAndShowsEveryView() = runComposeUiTest {
        val player = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 1 }
        show { PlayerRoundsCard(player.byRound(rounds), places = mapOf(1 to 4, 2 to 2, 3 to 1), playerCount = 4) }
        onNodeWithText("By round").assertExists()
        // The last played round is selected, and the place view comes first.
        onNodeWithText("Round 3: place 1 of 4").assertExists()
        snapshot("rounds-player-place", onRoot().captureToImage())

        onNodeWithTag(roundViewTag(RoundView.POINTS)).performClick()
        onNodeWithText("Round 3 · Table 1 · place 1 · 4 points · score 30", substring = true).assertExists()
        snapshot("rounds-player-points", onRoot().captureToImage())

        onNodeWithTag(roundViewTag(RoundView.SKILLS)).performClick()
        onNodeWithText("Lines: everything up to each round. Dots: that round alone.").assertExists()
        snapshot("rounds-player-skills", onRoot().captureToImage())

        onNodeWithTag(roundViewTag(RoundView.TOTALS)).performClick()
        onNodeWithText("Round 5").assertExists()
        snapshot("rounds-player-totals", onRoot().captureToImage())
    }

    @Test
    fun playerCardWithoutPlacesHasNoPlaceView() = runComposeUiTest {
        val player = CalculateStatisticsUseCase()(players, tables()).players.first { it.playerId == 2 }
        show { PlayerRoundsCard(player.byRound(rounds), places = null, playerCount = 4) }
        onNodeWithTag(roundViewTag(RoundView.PLACE)).assertDoesNotExist()
        onNodeWithTag(roundViewTag(RoundView.POINTS)).assertExists()
    }

    @Test
    fun teamCardShowsThePointsOfEachPlayer() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        val members = statistics.players.filter { it.playerId in 1..2 }
        val combined = com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics(-1, members.flatMap { it.results })
        show(dark = true) {
            TeamRoundsCard(
                rounds = combined.byRound(rounds),
                members = members.map { names.getValue(it.playerId) to it.byRound(rounds) },
                places = mapOf(1 to 2, 2 to 1, 3 to 1),
                teamCount = 2,
            )
        }
        onNodeWithText("Round 3: place 1 of 2").assertExists()
        onNodeWithTag(roundViewTag(RoundView.POINTS)).performClick()
        onNodeWithText("Player 1").assertExists()
        onNodeWithText("Player 2").assertExists()
        snapshot("rounds-team-points-dark", onRoot().captureToImage())
    }

    @Test
    fun tournamentCardShowsHandsSkillsAndRecords() = runComposeUiTest {
        val statistics = CalculateStatisticsUseCase()(players, tables())
        show { TournamentRoundsCard(statistics.byRound(rounds), names) }
        onNodeWithText("Round 3 · 1 of 1 tables", substring = true).assertExists()
        snapshot("rounds-tournament-hands", onRoot().captureToImage())

        onNodeWithTag(roundViewTag(RoundView.SKILLS)).performClick()
        onNodeWithText("Lines: everything up to each round. Dots: that round alone.").assertExists()
        snapshot("rounds-tournament-skills", onRoot().captureToImage())

        onNodeWithTag(roundViewTag(RoundView.RECORDS)).performClick()
        onNodeWithText("Round 1").assertExists()
        snapshot("rounds-tournament-records", onRoot().captureToImage())
    }
}
