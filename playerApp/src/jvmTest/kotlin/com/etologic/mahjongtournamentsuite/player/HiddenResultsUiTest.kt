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
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A tournament that hides its results until the prize giving: the ranking stays as it was before the hidden rounds. */
@OptIn(ExperimentalTestApi::class)
class HiddenResultsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = (1..8).map { TournamentPlayer(id = it, name = "Player $it", team = 1) }

    private fun table(round: Int, id: Int, seats: List<Int>, scores: List<Int>, hidden: Boolean = false) = TableState(
        roundId = round, tableId = id, playerIds = seats,
        playerEastId = "${seats[0]}", playerSouthId = "${seats[1]}", playerWestId = "${seats[2]}", playerNorthId = "${seats[3]}",
        playerEastScore = if (hidden) "" else "${scores[0]}", playerSouthScore = if (hidden) "" else "${scores[1]}",
        playerWestScore = if (hidden) "" else "${scores[2]}", playerNorthScore = if (hidden) "" else "${scores[3]}",
        isCompleted = true, useTotalsOnly = !hidden, resultsHidden = hidden,
    )

    /**
     * Round 2 is hidden. Player 8 is signed in and sees the own table of round 2 with its results (a big win); the
     * other table of round 2 comes without results. Player 1 led after round 1.
     */
    private fun view(hiddenFrom: Int): PlayerTournamentView {
        val tournament = Tournament(id = "t", name = "Open", isTeams = false, numPlayers = 8, numRounds = 2, resultsHiddenFromRound = hiddenFrom)
        val tables = listOf(
            table(1, 1, listOf(1, 2, 3, 4), listOf(30, -10, -10, -10), hidden = hiddenFrom <= 1),
            table(1, 2, listOf(5, 6, 7, 8), listOf(-10, -10, -10, 30), hidden = hiddenFrom <= 1),
            table(2, 1, listOf(1, 5, 2, 6), listOf(-40, 10, 20, 10), hidden = true),
            table(2, 2, listOf(8, 3, 4, 7), listOf(90, -30, -30, -30)),
        )
        // As the presenter does: the ranking leaves out the hidden rounds.
        val now = CalculateTournamentRankingsUseCase()(
            slots,
            tables.filter { tournament.showsResultsOf(it.roundId) }.map { RankingTable(it, emptyList()) },
            includeTeams = false,
        )
        return buildPlayerTournamentView(tournament, slots, emptyList(), tables.map { it to emptyList() }, now, myEmaId = null)
    }

    private fun show(view: PlayerTournamentView) = rule.setContent {
        MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxWidth()) { RankingTab(view) {} } }
    }

    private fun top(name: String) = rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun theRankingStaysAsItWasAfterTheLastVisibleRound() {
        show(view(hiddenFrom = 2))
        rule.onNodeWithTag("ranking_frozen_notice").assertIsDisplayed()
        rule.onNodeWithText("Ranking frozen after round 1", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(RANKING_ROUND_BUTTON_TAG).assertTextContains("Round 1")
        // The own table of round 2 would put player 8 first. It does not count yet.
        assertTrue(top("Player 1") < top("Player 8"), "Player 1 still leads")
    }

    @Test
    fun aTournamentHiddenFromTheFirstRoundShowsNoRanking() {
        show(view(hiddenFrom = 1))
        rule.onNodeWithText("Ranking hidden until the prize giving").assertIsDisplayed()
        rule.onNodeWithTag("ranking_frozen_notice").assertDoesNotExist()
    }

    @Test
    fun theRoundsOfTheRankingLeaveOutTheHiddenOnes() {
        val view = view(hiddenFrom = 2)
        assertEquals(setOf(1), view.rankingsByRound().keys)
        assertTrue(view.tournament.showsResultsOf(1))
        assertFalse(view.tournament.showsResultsOf(2))
        assertEquals(listOf(1), view.tournament.visibleRoundIds())
        assertEquals(listOf(1, 2), view.tournament.copy(resultsHiddenFromRound = null).visibleRoundIds())
    }
}
