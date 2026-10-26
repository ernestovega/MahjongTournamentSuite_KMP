package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import com.etologic.mahjongtournamentsuite.domain.model.BestHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.ChickenHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.hideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberHideOnScrollState
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.junit.Rule
import kotlin.test.Test

/** The ranking tab has one row of tabs, and the "Me" button and the search scroll with the list. */
@OptIn(ExperimentalTestApi::class)
class RankingTabsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val count = 40

    private fun view(teams: Boolean, chicken: Boolean, best: Boolean): PlayerTournamentView {
        val tournament = Tournament(
            id = "t", name = "T", isTeams = teams, numPlayers = count, numRounds = 1,
            countChickenHands = chicken, countBestHands = best,
        )
        val slots = (1..count).map { TournamentPlayer(id = it, name = "Player $it", team = (it - 1) % 2 + 1) }
        val rankings = TournamentRankings(
            players = (1..count).map { PlayerRanking(it, it, (it - 1) % 2 + 1, 100.0 - it, 1000 - it) },
            teams = if (teams) listOf(TeamRanking(1, 1, 50.0, 500), TeamRanking(2, 2, 40.0, 400)) else emptyList(),
            chickenHands = if (chicken) listOf(ChickenHandRanking(1, 1, 3, 90.0, 900)) else emptyList(),
            bestHands = if (best) listOf(BestHandRanking(1, 1, 80, 90.0, 900)) else emptyList(),
        )
        return buildPlayerTournamentView(tournament, slots, emptyList(), emptyList(), rankings, myEmaId = null)
            .copy(mySlotId = 30)
    }

    private fun show(view: PlayerTournamentView, hideOnScroll: Boolean = false) = rule.setContent {
        MtsTheme(useDarkTheme = false) {
            if (hideOnScroll) {
                val state = rememberHideOnScrollState()
                CompositionLocalProvider(LocalHideOnScroll provides state) {
                    Column(Modifier.fillMaxSize().hideOnScroll(state).testTag("screen")) { RankingTab(view) {} }
                }
            } else {
                Column(Modifier.fillMaxWidth()) { RankingTab(view) {} }
            }
        }
    }

    @Test
    fun theTabsShowOnlyTheRankingsThatTheTournamentHas() {
        show(view(teams = true, chicken = true, best = false))
        listOf("Players", "Teams", "Chicken").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Best").assertDoesNotExist()
    }

    @Test
    fun noTabsShowWhenOnlyThePlayersRankingExists() {
        show(view(teams = false, chicken = false, best = false))
        rule.onNodeWithText("Players").assertDoesNotExist()
        rule.onNode(hasContentDescription("Search")).assertIsDisplayed()
    }

    @Test
    fun aTabChangesTheRanking() {
        show(view(teams = true, chicken = true, best = true))
        rule.onNodeWithText("Player 1").assertIsDisplayed()
        rule.onNodeWithText("Best").performClick()
        rule.onNodeWithText("80").assertIsDisplayed()
        rule.onNodeWithText("Chicken").performClick()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun theMeButtonAndTheSearchAreAboveTheFirstLine() {
        show(view(teams = false, chicken = false, best = false))
        rule.onNode(hasContentDescription("Search")).assertIsDisplayed()
        rule.onNodeWithText("Me").assertIsDisplayed()
        val search = rule.onNode(hasContentDescription("Search")).fetchSemanticsNode().boundsInRoot.top
        val first = rule.onNodeWithText("Player 1").fetchSemanticsNode().boundsInRoot.top
        assert(search < first) { "search $search, first line $first" }
    }

    @Test
    fun theMeButtonScrollsAwayWithTheList() {
        show(view(teams = false, chicken = false, best = false))
        rule.onNodeWithText("Me").assertIsDisplayed()
        rule.onNodeWithText("Player 1").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText("Me").assertDoesNotExist()
    }

    @Test
    fun theMeButtonScrollsToTheOwnLine() {
        show(view(teams = false, chicken = false, best = false))
        rule.onNodeWithText("Player 30").assertDoesNotExist()
        rule.onNode(hasContentDescription("Jump to me")).performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Player 30").assertIsDisplayed() }.isSuccess }
    }

    @Test
    fun theTabsHideWhenTheListScrollsDown() {
        show(view(teams = true, chicken = false, best = false), hideOnScroll = true)
        rule.onNodeWithText("Teams").assertIsDisplayed()
        rule.onNodeWithText("Player 1").performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Teams").assertDoesNotExist() }.isSuccess }
    }
}
