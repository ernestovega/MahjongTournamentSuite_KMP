package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.presentation.components.DRAGGABLE_SCROLLBAR_TAG
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The table list of a big tournament: a scrollbar to move fast and filters to find a table. */
@OptIn(ExperimentalTestApi::class)
class TableListUiTest {
    private val tablesPerRound = 10
    private val rounds = 6

    private val view = run {
        val players = tablesPerRound * 4
        val tournament = Tournament(id = "t", name = "T", isTeams = false, numPlayers = players, numRounds = rounds)
        val slots = (1..players).map { TournamentPlayer(id = it, name = "", team = 0, assignedEmaId = if (it == 7) "10000007" else "") }
        val registry = listOf(Player(emaId = "10000007", firstName = "Virginia", lastName = "Gómez", country = "ESP"))
        val tables = (1..rounds).flatMap { round ->
            (1..tablesPerRound).map { table ->
                // Slot 7 sits at table 2 in round 1 and moves one table each round.
                val seats = (0 until 4).map { seat -> ((table - 1 + (round - 1) * (tablesPerRound - 1)) % tablesPerRound) * 4 + seat + 1 }
                TableState(
                    roundId = round, tableId = table, playerIds = seats,
                    playerEastId = seats[0].toString(), playerSouthId = seats[1].toString(),
                    playerWestId = seats[2].toString(), playerNorthId = seats[3].toString(),
                    playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
                    isCompleted = false, useTotalsOnly = false,
                ) to emptyList<TableHand>()
            }
        }
        buildPlayerTournamentView(tournament, slots, registry, tables, TournamentRankings(emptyList(), emptyList(), emptyList(), emptyList()), myEmaId = null)
    }

    private fun ComposeUiTest.show() = setContent {
        MtsTheme(useDarkTheme = false) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                TableList(view, view.tables, "empty", onOpen = {}, filterName = "all")
            }
        }
    }

    @Test
    fun aLongListHasAScrollbar() = runComposeUiTest {
        show()
        onNodeWithTag(DRAGGABLE_SCROLLBAR_TAG).assertIsDisplayed()
    }

    @Test
    fun aPlayerNameFindsTheTablesOfThePlayer() = runComposeUiTest {
        show()
        onNodeWithTag(TABLE_SEARCH_TAG).performTextInput("virginia")
        waitForIdle()
        // One table in each round, from table 2 in round 1 to table 7 in round 6. Table 1 never has her.
        onNodeWithText("Round 1").assertIsDisplayed()
        onNodeWithText("Table 2").assertIsDisplayed()
        onNodeWithText("Table 1").assertDoesNotExist()
        onAllNodesWithText("VIRGINIA", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun aNumberFindsTheTable() = runComposeUiTest {
        show()
        onNodeWithTag(TABLE_SEARCH_TAG).performTextInput("7")
        waitForIdle()
        // Every round has a table 7, and only that one.
        onAllNodesWithText("Table 7").onFirst().assertIsDisplayed()
        onNodeWithText("Table 1").assertDoesNotExist()
        onNodeWithText("Table 6").assertDoesNotExist()
    }
}
