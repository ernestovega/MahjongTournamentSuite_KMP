package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlin.test.Test
import kotlin.test.assertEquals

class TournamentScreenTest {

    @Test
    fun manualScoresTakePriorityOverCompletedFlag() {
        val table = tournamentTable(
            roundId = 2,
            tableId = 2,
            isCompleted = true,
            hasProgress = true,
            hasValidManualTotals = true,
        )

        assertEquals(CompletionStatus.Manual, tableCompletionStatus(table))
    }

    @Test
    fun firstTableToOpenUsesFirstTableNeedingAttention() {
        val tables = listOf(
            tournamentTable(roundId = 2, tableId = 1, isCompleted = true, hasProgress = true),
            tournamentTable(roundId = 1, tableId = 2, isCompleted = true, hasProgress = true),
            tournamentTable(roundId = 1, tableId = 1, isCompleted = false, hasProgress = true),
        )

        val selected = firstTournamentTableToOpen(tables)

        assertEquals(1, selected?.roundId)
        assertEquals(1, selected?.tableId)
    }

    @Test
    fun firstTableToOpenFallsBackToFirstTableWhenAllAreComplete() {
        val tables = listOf(
            tournamentTable(roundId = 2, tableId = 1, isCompleted = false, hasProgress = true, hasValidManualTotals = true),
            tournamentTable(roundId = 1, tableId = 2, isCompleted = true, hasProgress = true),
        )

        val selected = firstTournamentTableToOpen(tables)

        assertEquals(1, selected?.roundId)
        assertEquals(2, selected?.tableId)
    }

    @Test
    fun firstTableToOpenSkipsManualTablesWithSingleTick() {
        val tables = listOf(
            tournamentTable(roundId = 1, tableId = 1, isCompleted = false, hasProgress = true, hasValidManualTotals = true),
            tournamentTable(roundId = 1, tableId = 2, isCompleted = false, hasProgress = true),
        )

        val selected = firstTournamentTableToOpen(tables)

        assertEquals(2, selected?.tableId)
    }

    private fun tournamentTable(
        roundId: Int,
        tableId: Int,
        isCompleted: Boolean,
        hasProgress: Boolean,
        hasValidManualTotals: Boolean = false,
    ) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = listOf(1, 2, 3, 4),
        isCompleted = isCompleted,
        useTotalsOnly = false,
        usePointsCalculation = true,
        hasProgress = hasProgress,
        hasValidManualTotals = hasValidManualTotals,
    )
}
