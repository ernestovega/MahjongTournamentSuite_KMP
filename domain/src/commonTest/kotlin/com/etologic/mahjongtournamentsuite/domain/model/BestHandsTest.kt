package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BestHandsTest {
    @Test
    fun keepsAtMostThreeScoresWithoutTies() {
        assertEquals(listOf(40, 30, 12), topHandScores(listOf(8, 12, 40, 30)))
    }

    @Test
    fun keepsEveryScoreTiedWithTheThirdOne() {
        assertEquals(listOf(40, 30, 30, 30), topHandScores(listOf(30, 8, 40, 30, 30)))
    }

    @Test
    fun keepsAllScoresWhenThereAreFewerThanThree() {
        assertEquals(listOf(8, 4), topHandScores(listOf(4, 8)))
    }

    @Test
    fun oneTableCanHoldSeveralTournamentBestHands() {
        val tables = listOf(
            table(tableId = 1, scores = listOf(40, 36, 10)),
            table(tableId = 2, scores = listOf(38, 20)),
        )

        val best = tournamentBestHandScores(tables)

        assertEquals(listOf(40, 38, 36), best)
        assertEquals(listOf(40, 36), tables[0].tournamentBestHandScores(best))
        assertEquals(2, tables[0].tournamentBestHandCount(best))
        assertEquals(1, tables[1].tournamentBestHandCount(best))
    }

    private fun table(tableId: Int, scores: List<Int>) = TournamentTable(
        roundId = 1,
        tableId = tableId,
        playerIds = emptyList(),
        isCompleted = false,
        useTotalsOnly = false,
        usePointsCalculation = true,
        hasProgress = true,
        bestHandScores = topHandScores(scores),
    )
}
