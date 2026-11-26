package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TablePointsTest {
    @Test
    fun placesGiveFourTwoOneZero() {
        assertEquals(listOf("4", "2", "1", "0"), TablePoints.fromScores(listOf(30, 10, 0, -40)))
        assertEquals(listOf("0", "1", "2", "4"), TablePoints.fromScores(listOf(-40, 0, 10, 30)))
    }

    @Test
    fun tiesShareThePointsOfTheirPlaces() {
        assertEquals(listOf("3", "3", "1", "0"), TablePoints.fromScores(listOf(20, 20, 0, -40)))
        assertEquals(listOf("4", "1,5", "1,5", "0"), TablePoints.fromScores(listOf(30, 0, 0, -30)))
        assertEquals(listOf("1,75", "1,75", "1,75", "1,75"), TablePoints.fromScores(listOf(5, 5, 5, 5)))
    }

    @Test
    fun aSubstituteWithForcedZeroLeavesFourTwoOneToTheOthers() {
        assertEquals(listOf("4", "0", "2", "1"), TablePoints.fromScores(listOf(50, 30, 10, -90), forcedZeroSeat = 1))
        assertEquals(listOf("0", "4", "2", "1"), TablePoints.fromScores(listOf(50, 30, 10, -90), forcedZeroSeat = 0))
        assertEquals(listOf("3", "3", "1", "0"), TablePoints.fromScores(listOf(20, 20, 0, -40), forcedZeroSeat = 3))
        assertEquals(listOf("4", "1,5", "1,5", "0"), TablePoints.fromScores(listOf(20, 0, 0, -40), forcedZeroSeat = 3))
        assertEquals(listOf("2,33", "2,33", "2,33", "0"), TablePoints.fromScores(listOf(5, 5, 5, -15), forcedZeroSeat = 3))
        assertNull(TablePoints.fromScores(listOf(1, null, 2, 3), forcedZeroSeat = 0))
    }

    @Test
    fun missingScoreGivesNoPoints() {
        assertNull(TablePoints.fromScores(listOf(30, null, 0, -30)))
    }

    @Test
    fun everyTieHasItsShare() {
        assertEquals(listOf("2,33", "2,33", "2,33", "0"), TablePoints.fromScores(listOf(10, 10, 10, -30)))
        assertEquals(listOf("4", "1", "1", "1"), TablePoints.fromScores(listOf(30, -10, -10, -10)))
        assertEquals(listOf("3", "3", "0,5", "0,5"), TablePoints.fromScores(listOf(20, 20, -20, -20)))
        assertEquals(listOf("4", "2", "0,5", "0,5"), TablePoints.fromScores(listOf(30, 10, -20, -20)))
    }

    @Test
    fun aTableHasFourSeats() {
        assertFailsWith<IllegalArgumentException> { TablePoints.fromScores(listOf(1, 2, 3)) }
    }

    @Test
    fun aTableWithManualTotalsUsesTheManualScoresWhereTheyAreTyped() {
        val table = table(useTotalsOnly = true).copy(manualPlayerEastScore = "40", manualPlayerWestScore = " ")

        assertEquals(listOf("40", "10", "0", "-40"), table.seatScores)
        assertEquals(listOf("10", "10", "0", "-40"), table(useTotalsOnly = false).copy(manualPlayerEastScore = "40").seatScores)
    }

    @Test
    fun theSubstituteSeatGetsZeroAndAnUnknownSubstituteIsNoOne() {
        assertEquals(listOf("4", "0", "2", "1"), table().copy(forcedZeroPlayerId = " 7 ").seatPoints)
        assertNull(table().copy(forcedZeroPlayerId = "99").forcedZeroSeat)
        assertNull(table().forcedZeroSeat)
        assertEquals(List(4) { "" }, table().copy(playerSouthScore = "ten").seatPoints, "a score that is not a number gives no points")
    }

    private fun table(useTotalsOnly: Boolean = false) = TableState(
        roundId = 1,
        tableId = 1,
        playerIds = listOf(5, 7, 8, 9),
        playerEastId = "5",
        playerSouthId = "7",
        playerWestId = "8",
        playerNorthId = "9",
        playerEastScore = "10",
        playerSouthScore = "10",
        playerWestScore = "0",
        playerNorthScore = "-40",
        isCompleted = false,
        useTotalsOnly = useTotalsOnly,
    )
}
