package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun missingScoreGivesNoPoints() {
        assertNull(TablePoints.fromScores(listOf(30, null, 0, -30)))
    }
}
