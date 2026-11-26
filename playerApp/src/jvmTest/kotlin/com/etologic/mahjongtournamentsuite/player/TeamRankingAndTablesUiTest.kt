package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ranking of the teams, with the players of each team, and the tables of one team. */
@OptIn(ExperimentalTestApi::class)
class TeamRankingAndTablesUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(original)

    private val table = PlayerTable(
        TableState(
            roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1", playerSouthId = "2", playerWestId = "3", playerNorthId = "4",
            playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
            isCompleted = false, useTotalsOnly = false,
        ),
        emptyList(),
    )

    private val view = PlayerTournamentView(
        tournament = Tournament(id = "t", name = "T", isTeams = true, numPlayers = 8, numRounds = 1),
        players = mapOf(
            1 to PlayerEntry(1, "Ana Lopez", country = "ESP", team = 1),
            2 to PlayerEntry(2, "Bea Ruiz", country = "ITA", team = 1),
            3 to PlayerEntry(3, "Carla Gil", country = "", team = 2),
            4 to PlayerEntry(4, "Dani Sol", country = "ESP", team = 2),
            5 to PlayerEntry(5, "Eva Mar", country = "PRT", team = 3),
        ),
        tables = listOf(table),
        rankings = TournamentRankings(
            players = listOf(
                PlayerRanking(position = 1, playerId = 3, teamId = 2, points = 3.0, score = 30),
                PlayerRanking(position = 2, playerId = 1, teamId = 1, points = 2.0, score = 20),
                PlayerRanking(position = 3, playerId = 4, teamId = 2, points = 1.0, score = 10),
                PlayerRanking(position = 4, playerId = 2, teamId = 1, points = 0.0, score = 0),
                PlayerRanking(position = 5, playerId = 5, teamId = 3, points = 0.0, score = 0),
            ),
            teams = listOf(TeamRanking(1, 2, 5.0, 40), TeamRanking(2, 1, 3.0, 20), TeamRanking(3, 3, 0.0, 0)),
            chickenHands = emptyList(),
            bestHands = emptyList(),
        ),
        mySlotId = 1,
        teamNames = mapOf(1 to "Dragons", 2 to "Winds"),
    )

    @Test
    fun theTeamRankingFoldsOutItsPlayersAndATapOpensOne() = runComposeUiTest {
        val opened = mutableListOf<Int>()
        setContent { MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { RankingTab(view) { opened += it } } } }
        onNode(hasClickAction() and hasText("Teams", ignoreCase = true)).performClick()
        waitForIdle()
        onNodeWithText("Winds").assertExists()
        // A team without a name has its number.
        onNodeWithText("Team 3").assertExists()
        onNodeWithText("Carla Gil").assertDoesNotExist()
        onNode(hasClickAction() and hasText("Winds", substring = true)).performClick()
        waitForIdle()
        onNodeWithText("Carla Gil").performClick()
        waitForIdle()
        assertEquals(listOf(3), opened)
        onNode(hasContentDescription("Hide players")).assertExists()
    }

    @Test
    fun aSearchWithoutTeamsSaysSo() = runComposeUiTest {
        setContent { MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { TeamsTab(view, null, 0, {}, {}, {}, {}, {}) } } }
        onNodeWithTag(TEAM_SEARCH_TAG).performTextInput("nobody here")
        waitForIdle()
        onNodeWithText("No team matches the search.").assertExists()
    }

    @Test
    fun theTablesOfATeamShowItsTablesOrThatThereAreNone() = runComposeUiTest {
        var shown by mutableStateOf(view)
        setContent { MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { TeamsTab(shown, 2, 1, {}, {}, {}, {}, {}) } } }
        waitForIdle()
        onNodeWithText("Carla Gil", substring = true).assertExists()
        shown = view.copy(tables = emptyList())
        waitForIdle()
        onNodeWithText("No tables yet").assertExists()
    }

    @Test
    fun theTeamOfAPlayerHasItsName() {
        assertEquals("Winds", view.teamNameOf(2))
        assertEquals(null, view.teamNameOf(null))
        assertEquals(listOf(table), view.tablesOfTeam(1))
        assertEquals(emptyList(), view.tablesOfTeam(3))
    }
}
