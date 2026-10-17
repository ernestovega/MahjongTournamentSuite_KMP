package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerLevelTest {
    private fun result(handsWon: Int, selfDraws: Int, dealIns: Int, scoreSum: Int) = TableResult(
        roundId = 1, tableId = 1, seat = 0, position = 1, score = 0, points = 0.0, hasHands = true,
        handsPlayed = 16, handsWon = handsWon, selfDraws = selfDraws, dealIns = dealIns, winningScoreSum = scoreSum,
    )

    @Test
    fun playerWithFewHandsHasNoLevel() {
        val stats = PlayerStatistics(1, listOf(result(2, 1, 1, 30).copy(handsPlayed = 7)))
        assertNull(stats.skillScore)
        assertNull(stats.level)
    }

    @Test
    fun fullAxesGiveMaster() {
        val stats = PlayerStatistics(1, listOf(result(handsWon = 7, selfDraws = 4, dealIns = 0, scoreSum = 7 * 32)))
        assertEquals(PlayerLevel.MASTER, stats.level)
    }

    @Test
    fun emptyAxesGiveBeginner() {
        val stats = PlayerStatistics(1, listOf(result(handsWon = 0, selfDraws = 0, dealIns = 8, scoreSum = 0)))
        assertEquals(PlayerLevel.BEGINNER, stats.level)
    }

    @Test
    fun levelsFollowTheScoreLimits() {
        assertEquals(PlayerLevel.BEGINNER, PlayerLevel.of(29.9))
        assertEquals(PlayerLevel.INTERMEDIATE, PlayerLevel.of(30.0))
        assertEquals(PlayerLevel.ADVANCED, PlayerLevel.of(45.0))
        assertEquals(PlayerLevel.EXPERT, PlayerLevel.of(60.0))
        assertEquals(PlayerLevel.MASTER, PlayerLevel.of(75.0))
    }

    @Test
    fun levelIsProvisionalBelowOneHundredHands() {
        assertEquals(true, PlayerStatistics(1, listOf(result(3, 1, 1, 60))).isLevelProvisional)
    }
}
