package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlin.test.Test

/** The detail table of the hands of a table: results, penalties and the running totals of each seat. */
@OptIn(ExperimentalTestApi::class)
class HandDetailsTableUiTest {
    private fun hand(
        id: Int,
        winner: String = "",
        loser: String = "",
        score: String = "",
        chicken: Boolean = false,
        done: Boolean = true,
        east: String = "",
        north: String = "",
    ) = TableHand(id, winner, loser, score, chicken, done, east, "", "", north)

    private fun table(hands: List<TableHand>, seats: Boolean = true): PlayerTable {
        val ids = if (seats) listOf("1", "2", "3", "4") else listOf("1", "2", "", "")
        return PlayerTable(
            TableState(
                roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4),
                playerEastId = ids[0], playerSouthId = ids[1], playerWestId = ids[2], playerNorthId = ids[3],
                playerEastScore = "", playerSouthScore = "", playerWestScore = "", playerNorthScore = "",
                isCompleted = false, useTotalsOnly = false,
            ),
            hands,
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(table: PlayerTable) = setContent {
        MtsTheme(useDarkTheme = false) {
            HandDetailsTable(table) { seat -> SeatWind.entries.getOrNull(seat) }
        }
    }

    @Test
    fun eachHandShowsItsResultItsPenaltiesAndTheRunningTotals() = runComposeUiTest {
        show(
            table(
                listOf(
                    // East wins 10 from North: +34 -8 -8 -18.
                    hand(1, "1", "4", "10"),
                    // South wins 8 from the wall, and East has a penalty of 5.
                    hand(2, "2", "", "8", east = "-5"),
                    // A draw changes nothing.
                    hand(3, score = "0"),
                    // West wins a chicken hand of 8 from East.
                    hand(4, "3", "1", "8", chicken = true),
                    // A penalty of a hand in play shows, but does not count yet.
                    hand(5, done = false, north = "-6"),
                ),
            ),
        )

        listOf("#1", "#2", "#3", "#4").forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("#5").assertDoesNotExist()
        // A result line, then the subtotal under it.
        onAllNodesWithText("+34").assertCountEquals(2)
        onAllNodesWithText("+48").assertCountEquals(1)
        onAllNodesWithText("-5").assertCountEquals(1)
        // The subtotal after hand 2 counts the penalty: 34 - 16 - 5 = 13. The draw keeps it.
        onAllNodesWithText("+13").assertCountEquals(2)
        // The last subtotal: -3, +32, +8, -42.
        onAllNodesWithText("-3").assertCountEquals(1)
        onAllNodesWithText("-42").assertCountEquals(1)
        onAllNodesWithText("Pen").assertCountEquals(2)
        onAllNodesWithText("-6").assertCountEquals(1)
        // -6 is not in any subtotal: North ends with -42, not -48.
        onNodeWithText("-48").assertDoesNotExist()
    }

    @Test
    fun theHeaderNamesTheColumnsAndThePlayerOfEachSeat() = runComposeUiTest {
        show(table(listOf(hand(1, "1", "4", "10"))))

        listOf("Hand", "W", "L", "Pts").forEach { onNodeWithText(it).assertExists() }
        // The seats show the player numbers, and the winner and the loser too.
        onAllNodesWithText("#01").assertCountEquals(2)
        onAllNodesWithText("#04").assertCountEquals(2)
        onAllNodesWithText("#02").assertCountEquals(1)
    }

    @Test
    fun aTableWithoutSeatsSaysSo() = runComposeUiTest {
        show(table(listOf(hand(1, "1", "4", "10")), seats = false))

        onNodeWithText("No seats yet").assertExists()
        onNodeWithText("#1").assertDoesNotExist()
    }

    @Test
    fun aTableWithoutSavedHandsSaysSo() = runComposeUiTest {
        show(table(listOf(hand(1, done = false))))

        onNodeWithText("No hands yet").assertExists()
        onNodeWithText("The hands show here when the players save them.").assertExists()
    }

    @Test
    fun aHandThatDoesNotAddUpShowsNoChangeAndKeepsTheTotals() = runComposeUiTest {
        // A score below 8 is not a valid hand: the line shows the data typed, without a change of the seats.
        show(table(listOf(hand(1, "1", "4", "10"), hand(2, "2", "3", "5"))))

        onNodeWithText("#2").assertExists()
        onNodeWithText("5").assertExists()
        onAllNodesWithText("+34").assertCountEquals(3)
    }
}
