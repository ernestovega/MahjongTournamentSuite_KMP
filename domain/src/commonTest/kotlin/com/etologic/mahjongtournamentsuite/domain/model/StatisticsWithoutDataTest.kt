package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the statistics say while there is nothing to count: no tables, no hands, or only totals. */
class StatisticsWithoutDataTest {
    @Test
    fun aPlayerWithoutTablesHasNoValues() {
        val nobody = PlayerStatistics(1, emptyList())

        assertNull(nobody.bestTableScore)
        assertNull(nobody.averagePosition)
        assertNull(nobody.bestHand)
        assertNull(nobody.mostHandsWonAtATable)
        assertNull(nobody.mostChickenHandsAtATable)
        assertNull(nobody.highestPenalty)
        assertNull(nobody.winRate)
        assertNull(nobody.selfDrawRate)
        assertNull(nobody.averageWinningScore)
        assertNull(nobody.averageDealInScore)
        assertNull(nobody.skillScore)
        assertNull(nobody.level)
        assertTrue(nobody.isLevelProvisional)
        assertEquals(Streaks(tableWins = 0, handsWon = null, handsWithoutDealIn = null), nobody.streaks)
        assertEquals(emptyList(), nobody.toughestRivals())
    }

    @Test
    fun aPlayerWithOnlyTotalsHasNoHandValues() {
        val totals = PlayerTotals("1", tables = 3, points = 6.0, score = 40, tablesWithHands = 0, handsPlayed = 0, handsWon = 0, selfDraws = 0, dealIns = 0, penaltyCount = 0)

        val player = PlayerStatistics.ofTotals(7, totals)

        assertFalse(player.results.single().hasHands)
        assertEquals(0, player.tablesWithHands)
        assertEquals(6.0, player.totalPoints)
        assertEquals(40, player.bestTableScore)
    }

    @Test
    fun aTournamentWithoutHandsHasNoRates() {
        val statistics = tournament(
            tables = listOf(table(completed = true, hasHands = false, times = listOf(60_000)), table(completed = false, hasHands = true, times = listOf(90_000))),
        )

        assertNull(statistics.drawRate)
        assertNull(statistics.averageHandsPerTable)
        assertNull(statistics.averageHandTimeMs, "only completed tables with hands count their times")
        assertEquals(emptyList(), statistics.winRateLeaders)
        assertEquals(emptyList(), statistics.selfDrawLeaders)
    }

    @Test
    fun theAverageTimeOfAHandComesFromTheCompletedTablesWithHands() {
        val statistics = tournament(tables = listOf(table(completed = true, hasHands = true, times = listOf(60_000, 120_000))))

        assertEquals(90_000L, statistics.averageHandTimeMs)
    }

    private fun tournament(tables: List<TableStatistics>) = TournamentStatistics(
        tablesTotal = tables.size,
        tablesCompleted = tables.count { it.isCompleted },
        tablesWithHands = 0,
        players = emptyList(),
        handsPlayed = 0,
        drawnHands = 0,
        selfDraws = 0,
        averageHandScore = null,
        highestHand = null,
        chickenHands = 0,
        penaltyCount = 0,
        tables = tables,
    )

    private fun table(completed: Boolean, hasHands: Boolean, times: List<Long>) = TableStatistics(
        roundId = 1,
        tableId = 1,
        isCompleted = completed,
        hasHands = hasHands,
        players = emptyList(),
        handsPlayed = 0,
        drawnHands = 0,
        selfDraws = 0,
        dealIns = 0,
        bestHand = null,
        chickenHands = 0,
        penaltyCount = 0,
        swing = emptyList(),
        handTimesMs = times,
    )
}
