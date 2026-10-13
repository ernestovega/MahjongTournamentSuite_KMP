package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableDiffsTest {
    @Test
    fun neededScoresFollowTheRulesOfTheGame() {
        assertEquals(8, 40.neededHandScoreBySelfPick())
        assertEquals(8, 63.neededHandScoreBySelfPick())
        assertEquals(10, 68.neededHandScoreBySelfPick())
        assertEquals(9, 66.neededHandScoreBySelfPick())
        assertEquals(8, 47.neededHandScoreByDirectHu())
        assertEquals(9, 49.neededHandScoreByDirectHu())
        assertEquals(10, 50.neededHandScoreByDirectHu())
        assertEquals(8, 39.neededHandScoreByIndirectHu())
        assertEquals(17, 48.neededHandScoreByIndirectHu())
    }

    @Test
    fun firstPlaceNeedsNothingAndOthersNeedTheGap() {
        val diffs = calculateTableDiffs(listOf(128, 32, -32, -128))

        assertNull(diffs[0].pointsToBeFirst)
        assertEquals(96.neededHandScoreByIndirectHu(), diffs[1].pointsToBeFirst?.byIndirectHu)
        assertEquals(160.neededHandScoreBySelfPick(), diffs[2].pointsToBeFirst?.bySelfPick)
        assertEquals(64.neededHandScoreByDirectHu(), diffs[2].pointsToBeSecond?.byDirectHu)
    }

    @Test
    fun aTieForFirstNeedsTheMinimumScore() {
        val diffs = calculateTableDiffs(listOf(64, 64, 0, -128))

        assertEquals(8, diffs[0].pointsToBeFirst?.bySelfPick)
        assertEquals(8, diffs[1].pointsToBeFirst?.byDirectHu)
    }

    @Test
    fun keepsTheOrderOfTheSeats() {
        val diffs = calculateTableDiffs(listOf(-10, 30, 0, -20))

        assertEquals(listOf(0, 1, 2, 3), diffs.map { it.seatIndex })
        assertEquals(listOf(-10, 30, 0, -20), diffs.map { it.points })
    }
}
