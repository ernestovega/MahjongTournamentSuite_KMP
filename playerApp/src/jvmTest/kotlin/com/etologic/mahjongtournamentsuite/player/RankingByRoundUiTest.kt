package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Ranking tab can show the ranking at the end of a past round, and share the ranking it shows as an image. */
@OptIn(ExperimentalTestApi::class)
class RankingByRoundUiTest {
    @get:Rule
    val rule = createComposeRule()

    /** Two tables of four, two rounds. Player 1 wins round 1; player 8 wins round 2 by much more and leads after it. */
    private fun view(): PlayerTournamentView {
        val tournament = Tournament(id = "t", name = "Open", isTeams = false, numPlayers = 8, numRounds = 2)
        val slots = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 1) }
        fun table(round: Int, id: Int, seats: List<Int>, scores: List<Int>) = TableState(
            roundId = round, tableId = id, playerIds = seats,
            playerEastId = "${seats[0]}", playerSouthId = "${seats[1]}", playerWestId = "${seats[2]}", playerNorthId = "${seats[3]}",
            playerEastScore = "${scores[0]}", playerSouthScore = "${scores[1]}", playerWestScore = "${scores[2]}", playerNorthScore = "${scores[3]}",
            isCompleted = true, useTotalsOnly = true,
        )
        val tables = listOf(
            table(1, 1, listOf(1, 2, 3, 4), listOf(30, -10, -10, -10)),
            table(1, 2, listOf(5, 6, 7, 8), listOf(-10, -10, -10, 30)),
            table(2, 1, listOf(1, 5, 2, 6), listOf(-40, 10, 20, 10)),
            table(2, 2, listOf(8, 3, 4, 7), listOf(90, -30, -30, -30)),
        )
        val now = CalculateTournamentRankingsUseCase()(slots, tables.map { RankingTable(it, emptyList()) }, includeTeams = false)
        return buildPlayerTournamentView(tournament, slots, emptyList(), tables.map { it to emptyList() }, now, myEmaId = null)
    }

    private fun show() = rule.setContent {
        MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxWidth()) { RankingTab(view()) {} } }
    }

    private fun top(name: String) = rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aPastRoundShowsTheRankingAsItWasThen() {
        show()
        rule.onNodeWithTag(RANKING_ROUND_BUTTON_TAG).assertTextContains("Now")
        assertTrue(top("Player 8") < top("Player 1"), "Now player 8 leads")
        rule.onNodeWithTag(RANKING_ROUND_BUTTON_TAG).performClick()
        rule.onNodeWithText("After round 1").performClick()
        rule.onNodeWithTag(RANKING_ROUND_BUTTON_TAG).assertTextContains("Round 1")
        assertTrue(top("Player 1") < top("Player 8"), "After round 1 player 1 leads")
    }

    @Test
    fun shareShowsThePreviewOfTheRankingThatTheTabShows() {
        show()
        rule.onNodeWithTag(RANKING_ROUND_BUTTON_TAG).performClick()
        rule.onNodeWithText("After round 1").performClick()
        rule.onNodeWithTag(RANKING_SHARE_BUTTON_TAG).performClick()
        rule.onNodeWithTag(RANKING_SHARE_DIALOG_TAG).assertIsDisplayed()
        rule.onNodeWithText("Ranking after round 1").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithTag(RANKING_SHARE_DIALOG_TAG).assertDoesNotExist()
    }
}
