package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private val draw = PointsDiff(8, 8, 8)
    private val gap100 = PointsDiff(bySelfPick = 18, byDirectHu = 35, byIndirectHu = 69)
    private val gap50 = PointsDiff(bySelfPick = 8, byDirectHu = 10, byIndirectHu = 19)

    private fun needs(points: List<Int>) = calculateTableDiffs(points).map { listOf(it.pointsToBeFirst, it.pointsToBeSecond, it.pointsToBeThird) }

    @Test
    fun oddGapsRoundUp() {
        assertEquals(9, 65.neededHandScoreBySelfPick())
        assertEquals(9, 49.neededHandScoreByDirectHu())
    }

    @Test
    fun aTableNeedsFourSeats() {
        assertFailsWith<IllegalArgumentException> { calculateTableDiffs(listOf(1, 2, 3)) }
    }

    @Test
    fun everyTieHasItsNeeds() {
        assertEquals(List(4) { listOf(draw, null, null) }, needs(listOf(10, 10, 10, 10)), "all four tied")
        assertEquals(
            listOf(listOf(draw, null, null), listOf(draw, null, null), listOf(draw, null, null), listOf(gap100, null, null)),
            needs(listOf(100, 100, 100, 0)),
            "three tied first",
        )
        assertEquals(
            listOf(listOf(null, null, null), listOf(gap100, draw, null), listOf(gap100, draw, null), listOf(gap100, draw, null)),
            needs(listOf(100, 0, 0, 0)),
            "three tied last",
        )
        assertEquals(
            listOf(listOf(draw, null, null), listOf(draw, null, null), listOf(gap100, null, draw), listOf(gap100, null, draw)),
            needs(listOf(100, 100, 0, 0)),
            "two pairs",
        )
        assertEquals(
            listOf(listOf(draw, null, null), listOf(draw, null, null), listOf(gap50, null, null), listOf(gap100, null, gap50)),
            needs(listOf(100, 100, 50, 0)),
            "two tied first",
        )
        assertEquals(
            listOf(listOf(null, null, null), listOf(gap50, draw, null), listOf(gap50, draw, null), listOf(gap100, gap50, null)),
            needs(listOf(100, 50, 50, 0)),
            "two tied second",
        )
        val gap40 = PointsDiff(8, 8, 9)
        val gap60 = PointsDiff(8, 15, 29)
        assertEquals(
            listOf(listOf(null, null, null), listOf(gap40, null, null), listOf(gap100, gap60, draw), listOf(gap100, gap60, draw)),
            needs(listOf(100, 60, 0, 0)),
            "two tied last",
        )
    }

    @Test
    fun aPlaceThatNeedsTheSameAsThePlaceAboveShowsOnlyThatOne() {
        assertEquals(
            listOf(listOf(null, null, null), listOf(draw, null, null), listOf(draw, null, null), listOf(draw, null, null)),
            needs(listOf(30, 20, 10, 0)),
            "small gaps all need the minimum",
        )
        assertEquals(listOf(gap100, draw, null), needs(listOf(100, 10, 0, 0))[2], "the third place needs the minimum, like the second")
        assertEquals(listOf(draw, null, null), needs(listOf(30, 0, 0, -100))[1])
    }
}
