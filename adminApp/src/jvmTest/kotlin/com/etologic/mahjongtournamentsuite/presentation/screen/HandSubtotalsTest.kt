package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The running total (Σ) under each hand shows only for a hand that counts, and goes on past the gaps. */
class HandSubtotalsTest {
    private val empty = SeatTextValues.EMPTY

    @Test
    fun aHandThatDoesNotCountHasNoSubtotalAndTheNextOneCarriesOn() {
        val editor = editor(
            hand(1, winner = "11", loser = "12", score = "10"),
            // Empty hand between two filled ones.
            hand(2, done = false),
            // Half filled: a winner and no score.
            hand(3, winner = "13", done = false),
            // Filled but not done.
            hand(4, winner = "13", loser = "14", score = "10", done = false),
            // A draw counts, and its Σ repeats the last total.
            hand(5, score = "0"),
            hand(6, winner = "12", loser = "11", score = "10"),
        )

        assertEquals(
            listOf(
                values(34, -18, -8, -8),
                empty,
                empty,
                empty,
                values(34, -18, -8, -8),
                values(16, 16, -16, -16),
            ),
            editor.cumulativeHandScoreSubtotals,
        )
    }

    @Test
    fun theFirstHandsHaveNoSubtotalUntilOneCounts() {
        val editor = editor(hand(1, done = false), hand(2, winner = "11", loser = "12", score = "10"))

        assertEquals(listOf(empty, values(34, -18, -8, -8)), editor.cumulativeHandScoreSubtotals)
    }

    @Test
    fun aCompletedTableCountsEveryFilledHandButNotTheEmptyOnes() {
        val editor = editor(
            hand(1, winner = "11", loser = "12", score = "10", done = false),
            hand(2, done = false),
            completed = true,
        )

        assertEquals(listOf(values(34, -18, -8, -8), empty), editor.cumulativeHandScoreSubtotals)
    }

    @Test
    fun aDoneHandWithOnlyAPenaltyAddsThePenaltyToTheRunningTotal() {
        val editor = editor(
            hand(1, winner = "11", loser = "12", score = "10"),
            hand(2, penalties = listOf("", "-10", "", "")),
            // Not done: its penalty does not count yet.
            hand(3, penalties = listOf("-20", "", "", ""), done = false),
        )

        assertEquals(values(0, -10, 0, 0), editor.handResultScores[1])
        assertEquals(listOf(values(34, -18, -8, -8), values(34, -28, -8, -8), empty), editor.cumulativeHandScoreSubtotals)
    }

    private fun values(east: Int, south: Int, west: Int, north: Int) = SeatTextValues(east.toString(), south.toString(), west.toString(), north.toString())

    private fun editor(vararg hands: TableHand, completed: Boolean = false) = TableManagerEditorState.from(
        TableState(
            roundId = 1,
            tableId = 1,
            playerIds = listOf(11, 12, 13, 14),
            playerEastId = "11",
            playerSouthId = "12",
            playerWestId = "13",
            playerNorthId = "14",
            playerEastScore = "",
            playerSouthScore = "",
            playerWestScore = "",
            playerNorthScore = "",
            manualPlayerEastScore = "",
            manualPlayerSouthScore = "",
            manualPlayerWestScore = "",
            manualPlayerNorthScore = "",
            isCompleted = completed,
            useTotalsOnly = false,
            forcedZeroPlayerId = "",
        ),
        hands.toList(),
    )

    private fun hand(
        id: Int,
        winner: String = "",
        loser: String = "",
        score: String = "",
        done: Boolean = true,
        penalties: List<String> = listOf("", "", "", ""),
    ) = TableHand(
        handId = id,
        playerWinnerId = winner,
        playerLooserId = loser,
        handScore = score,
        isChickenHand = false,
        isDone = done,
        playerEastPenalty = penalties[0],
        playerSouthPenalty = penalties[1],
        playerWestPenalty = penalties[2],
        playerNorthPenalty = penalties[3],
    )
}
