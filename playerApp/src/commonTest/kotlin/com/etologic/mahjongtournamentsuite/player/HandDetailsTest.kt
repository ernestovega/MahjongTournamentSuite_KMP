package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HandDetailsTest {
    private fun hand(
        winner: String = "",
        loser: String = "",
        score: String = "",
        east: String = "",
        south: String = "",
        west: String = "",
        north: String = "",
    ) = TableHand(1, winner, loser, score, false, true, east, south, west, north)

    private val seats = listOf(1, 2, 3, 4)

    @Test
    fun discardChangesTheSeatsWithoutPenalties() {
        assertEquals(listOf(34, -8, -8, -18), hand("1", "4", "10", south = "-5").resultChange(seats))
    }

    @Test
    fun selfDrawAndDrawChangeTheSeats() {
        assertEquals(listOf(54, -18, -18, -18), hand("1", "", "10").resultChange(seats))
        assertEquals(listOf(0, 0, 0, 0), hand(score = "0").resultChange(seats))
        assertNull(hand().resultChange(seats))
        assertNull(hand("9", "1", "10").resultChange(seats))
    }

    @Test
    fun penaltyRowsAreTheSemicolonSeparatedParts() {
        val rows = hand(east = "5;", south = "-15;", west = "5;-4", north = "5;").penaltyRows()
        assertEquals(listOf(listOf(5, -15, 5, 5), listOf(0, 0, -4, 0)), rows)
        assertEquals(emptyList(), hand().penaltyRows())
    }

    @Test
    fun sharedPenaltyGivesAThirdToEachOtherSeat() {
        assertEquals(listOf(10, -30, 10, 10), PenaltyEntry(seatIndex = 1, amount = 30, shared = true).seatValues())
        assertEquals(listOf(0, -30, 0, 0), PenaltyEntry(seatIndex = 1, amount = 30, shared = false).seatValues())
        assertEquals(false, PenaltyEntry(seatIndex = 1, amount = 10, shared = true).isValid)
    }
}
