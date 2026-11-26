package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The edges of the statistics: hands that hold zeros, tables with hands next to tables with totals, and people without results. */
class StatisticsEdgeBranchesTest {
    private fun result(score: Int, hands: Boolean, handsWon: Int = 0, bestHand: Int = 0, maxPenalty: Int = 0, position: Int = 1) = TableResult(
        roundId = 1, tableId = 1, seat = 0, position = position, score = score, points = 1.0,
        hasHands = hands, handsPlayed = if (hands) 10 else 0, handsWon = handsWon, bestHand = bestHand, maxPenalty = maxPenalty,
    )

    @Test
    fun handsWithZeroBestHandAndNoPenaltyGiveNullValues() {
        val player = PlayerStatistics(1, listOf(result(score = 10, hands = true)))

        assertNull(player.bestHand)
        assertNull(player.highestPenalty)
        assertEquals(0, player.mostHandsWonAtATable)
        assertEquals(0.0, player.winRate)
        assertEquals(0.0, player.dealInRate)
    }

    @Test
    fun positiveValuesAreKept() {
        val player = PlayerStatistics(1, listOf(result(score = 10, hands = true, handsWon = 3, bestHand = 40, maxPenalty = 10)))

        assertEquals(40, player.bestHand)
        assertEquals(10, player.highestPenalty)
        assertEquals(0.3, player.winRate)
    }

    @Test
    fun aPersonWithoutTournamentsHasEmptyRecords() {
        val person = groupPersonOf("1", emptyList())

        assertEquals(0, person.tournaments)
        assertEquals(0, person.bestHand)
        assertNull(person.bestTableScore)
        assertNull(person.mostHandsWonAtATable)
        assertEquals(0, person.maxPenalty)
        assertEquals(0, person.tableWinRun)
        assertNull(person.handWinRun)
        assertFalse(person.toPlayerStatistics(1).results.single().hasHands)
    }

    @Test
    fun aPersonWithHandsAndTotalsCountsOnlyTheHands() {
        val person = groupPersonOf(
            "1",
            listOf(listOf(result(score = 20, hands = true, handsWon = 2, bestHand = 30, maxPenalty = 5)), emptyList(), listOf(result(score = 50, hands = false))),
        )

        assertEquals(2, person.tournaments)
        assertEquals(1, person.tablesWithHands)
        assertEquals(30, person.bestHand)
        assertEquals(50, person.bestTableScore)
        assertEquals(2, person.mostHandsWonAtATable)
        assertEquals(5, person.maxPenalty)
        assertEquals(1, person.tableWinRun)
        assertTrue(person.toPlayerStatistics(1).results.single().hasHands)
    }

    @Test
    fun aTournamentWithHandsHasRatesAndLeaders() {
        val many = TableResult(roundId = 1, tableId = 1, seat = 0, position = 1, score = 1, points = 1.0, hasHands = true, handsPlayed = 10, handsWon = 4, selfDraws = 2, dealIns = 1)
        val few = TableResult(roundId = 1, tableId = 1, seat = 1, position = 2, score = 0, points = 0.0, hasHands = true, handsPlayed = 2, handsWon = 1, dealIns = 1)
        val best = PlayerStatistics(1, listOf(many))
        val other = PlayerStatistics(2, listOf(many.copy(handsWon = 2, selfDraws = 0, dealIns = 3)))
        val short = PlayerStatistics(3, listOf(few))
        val statistics = TournamentStatistics(
            tablesTotal = 1, tablesCompleted = 1, tablesWithHands = 2, players = listOf(other, best, short),
            handsPlayed = 10, drawnHands = 2, selfDraws = 2, averageHandScore = null, highestHand = null, chickenHands = 0, penaltyCount = 0, tables = emptyList(),
        )

        assertEquals(0.2, statistics.drawRate)
        assertEquals(5.0, statistics.averageHandsPerTable)
        assertEquals(listOf(best, other), statistics.winRateLeaders)
        assertEquals(listOf(best, other), statistics.lowestDealInLeaders)
        assertEquals(listOf(best), statistics.selfDrawLeaders)
    }
}
