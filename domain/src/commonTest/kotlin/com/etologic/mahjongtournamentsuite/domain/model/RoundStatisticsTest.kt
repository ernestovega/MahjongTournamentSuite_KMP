package com.etologic.mahjongtournamentsuite.domain.model

import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundStatisticsTest {
    private fun result(round: Int, table: Int = 1, position: Int = 1, score: Int = 0, won: Int = 0, played: Int = 0, bestHand: Int = 0) = TableResult(
        roundId = round,
        tableId = table,
        seat = 0,
        position = position,
        score = score,
        points = (4 - position).toDouble(),
        hasHands = played > 0,
        handsPlayed = played,
        handsWon = won,
        winningScoreSum = won * 10,
        bestHand = bestHand,
    )

    @Test
    fun aPlayerSplitsByRoundAndAddsUpTheRoundsBefore() {
        val player = PlayerStatistics(7, listOf(result(2, won = 2, played = 8), result(1, won = 4, played = 8)))
        val rounds = player.byRound(listOf(3, 1, 2))
        assertEquals(listOf(1, 2, 3), rounds.map { it.roundId })
        assertEquals(0.5, rounds[0].round.winRate)
        assertEquals(0.25, rounds[1].round.winRate)
        // The rate up to round 2 uses the 16 hands of both rounds.
        assertEquals(6.0 / 16, rounds[1].cumulative.winRate)
        assertTrue(rounds[2].isEmpty)
        assertEquals(16, rounds[2].cumulative.handsPlayed)
    }

    private fun table(round: Int, table: Int, completed: Boolean, played: Int, drawn: Int, selfDraws: Int, results: Map<Int, TableResult>) = TableStatistics(
        roundId = round,
        tableId = table,
        isCompleted = completed,
        hasHands = true,
        players = results.map { (slot, result) -> PlayerStatistics(slot, listOf(result)) },
        handsPlayed = played,
        drawnHands = drawn,
        selfDraws = selfDraws,
        dealIns = 0,
        bestHand = results.values.maxOfOrNull { it.bestHand },
        chickenHands = 0,
        penaltyCount = 1,
        swing = emptyList(),
    )

    @Test
    fun aTournamentSplitsByRoundWithRecordsAndRunningTotals() {
        val statistics = TournamentStatistics(
            tablesTotal = 3,
            tablesCompleted = 2,
            tablesWithHands = 2,
            players = emptyList(),
            handsPlayed = 32,
            drawnHands = 4,
            selfDraws = 6,
            averageHandScore = null,
            highestHand = null,
            chickenHands = 0,
            penaltyCount = 2,
            tables = listOf(
                table(1, 1, true, 16, 2, 4, mapOf(1 to result(1, score = 120, won = 7, bestHand = 40), 2 to result(1, score = -30, won = 7))),
                table(2, 1, true, 16, 2, 2, mapOf(3 to result(2, score = 200, won = 14, bestHand = 24))),
                table(2, 2, false, 5, 0, 1, mapOf(4 to result(2, score = 300, won = 5, bestHand = 88))),
            ),
        )
        val rounds = statistics.byRound(listOf(1, 2))
        assertEquals(16, rounds[0].round.handsPlayed)
        assertEquals(RoundRecord(40, 1), rounds[0].round.highestHand)
        assertEquals(RoundRecord(120, 1), rounds[0].round.highestTableScore)
        // The table that is not completed counts in the tables but not in the hands or the records.
        assertEquals(2, rounds[1].round.tablesTotal)
        assertEquals(1, rounds[1].round.tablesCompleted)
        assertEquals(16, rounds[1].round.handsPlayed)
        assertEquals(RoundRecord(24, 3), rounds[1].round.highestHand)
        assertEquals(RoundRecord(200, 3), rounds[1].round.highestTableScore)
        assertEquals(32, rounds[1].cumulative.handsPlayed)
        assertEquals(RoundRecord(40, 1), rounds[1].cumulative.highestHand)
        assertEquals(4.0 / 32, rounds[1].cumulative.drawRate)
        assertEquals(10.0, rounds[0].round.averageHandScore)
    }

    @Test
    fun anEmptyRoundHasNoRates() {
        val round = TournamentRoundTotals(3, 0, 0, 0, 0, 0, 0, 0, null, null)
        assertNull(round.drawRate)
        assertNull(round.averageHandScore)
    }

    @Test
    fun theRankingAfterEachRoundCountsOnlyTheRoundsUpToIt() {
        val players = (1..4).map { TournamentPlayer(id = it, name = "P$it", team = 0) }
        fun rankingTable(round: Int, scores: List<String>) = RankingTable(
            TableState(
                roundId = round,
                tableId = 1,
                playerIds = listOf(1, 2, 3, 4),
                playerEastId = "1",
                playerSouthId = "2",
                playerWestId = "3",
                playerNorthId = "4",
                playerEastScore = scores[0],
                playerSouthScore = scores[1],
                playerWestScore = scores[2],
                playerNorthScore = scores[3],
                isCompleted = true,
                useTotalsOnly = true,
            ),
            emptyList(),
        )
        val tables = listOf(rankingTable(1, listOf("100", "50", "-50", "-100")), rankingTable(2, listOf("-100", "-50", "50", "100")))
        val calculate = CalculateTournamentRankingsUseCase()
        val byRound = calculate.afterEachRound(players, tables, includeTeams = false, rounds = listOf(1, 2))
        assertEquals(calculate(players, tables.take(1), includeTeams = false), byRound.getValue(1))
        assertEquals(calculate(players, tables, includeTeams = false), byRound.getValue(2))
    }
}
