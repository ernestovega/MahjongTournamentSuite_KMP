package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Players and Teams tabs of a tournament: the lists, the search, and the screen of one player or team. */
class PlayersTeamsTabsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val view = PlayerTournamentView(
        tournament = Tournament(id = "t", name = "T", isTeams = true, numPlayers = 4, numRounds = 1),
        players = mapOf(
            1 to PlayerEntry(1, "Ana Lopez", country = "ESP", team = 1),
            2 to PlayerEntry(2, "Bea Ruiz", country = "ITA", team = 1),
            3 to PlayerEntry(3, "Carla Gil", country = "FRA", team = 2),
            4 to PlayerEntry(4, "Dani Sol", country = "ESP", team = 2),
        ),
        tables = emptyList(),
        rankings = TournamentRankings(
            players = listOf(PlayerRanking(1, 3, 2, 3.0, 30), PlayerRanking(2, 1, 1, 2.0, 20), PlayerRanking(3, 2, 1, 1.0, 10), PlayerRanking(4, 4, 2, 0.0, 0)),
            teams = listOf(TeamRanking(1, 2, 5.0, 50), TeamRanking(2, 1, 3.0, 30)),
            chickenHands = emptyList(),
            bestHands = emptyList(),
        ),
        mySlotId = 1,
        teamNames = mapOf(1 to "Dragons", 2 to "Winds"),
    )

    @Test
    fun thePlayersShowByNameAndTheSearchFiltersThem() {
        rule.setContent { MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { PlayersTab(view) {} } } }
        listOf("Ana Lopez", "Bea Ruiz", "Carla Gil", "Dani Sol").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        // The team and the place in the ranking show on each line.
        rule.onNodeWithText("#2").assertIsDisplayed()

        rule.onNodeWithTag(PLAYER_SEARCH_TAG).performTextInput("winds")
        rule.onNodeWithText("Carla Gil").assertIsDisplayed()
        rule.onNodeWithText("Ana Lopez").assertDoesNotExist()
    }

    @Test
    fun aTapOnAPlayerAsksToOpenItsScreen() {
        var selected by mutableStateOf<Int?>(null)
        rule.setContent {
            MtsTheme(useDarkTheme = false) {
                Column(Modifier.fillMaxSize()) { PlayersTab(view) { selected = it } }
            }
        }
        rule.onNodeWithText("Bea Ruiz").performClick()
        rule.waitForIdle()
        assertEquals(2, selected)
        // The player opens on its own screen, so the list stays under it.
        rule.onNodeWithText("Ana Lopez").assertIsDisplayed()
    }

    @Test
    fun theTeamsShowByRankingWithTheirPlayersAndTheSearchLooksAtThePlayers() {
        rule.setContent { MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { TeamsTab(view, null, 0, {}, {}, {}, {}, {}) } } }
        rule.onNodeWithText("Dragons").assertIsDisplayed()
        rule.onNodeWithText("Carla Gil, Dani Sol").assertIsDisplayed()
        val winds = rule.onNodeWithText("Winds").fetchSemanticsNode().boundsInRoot.top
        val dragons = rule.onNodeWithText("Dragons").fetchSemanticsNode().boundsInRoot.top
        assert(winds < dragons) { "The first team of the ranking goes first" }

        rule.onNodeWithTag(TEAM_SEARCH_TAG).performTextInput("bea")
        rule.onNodeWithText("Dragons").assertIsDisplayed()
        rule.onNodeWithText("Winds").assertDoesNotExist()
    }

    @Test
    fun aTeamShowsItsPlayersAndATapOpensThePlayer() {
        var opened: Int? = null
        rule.setContent {
            MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { TeamsTab(view, 1, 0, {}, {}, { opened = it }, {}, {}) } }
        }
        listOf("Players", "Tables", "Stats", "Ana Lopez", "Bea Ruiz").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Carla Gil").assertDoesNotExist()
        rule.onNodeWithText("Ana Lopez").performClick()
        assertEquals(1, opened)
    }
}
