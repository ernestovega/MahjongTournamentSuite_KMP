package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundTablesByPlayerTest {
    private fun table(roundId: Int, tableId: Int, vararg playerIds: Int) = TournamentTable(
        roundId = roundId,
        tableId = tableId,
        playerIds = playerIds.toList(),
        isCompleted = false,
        useTotalsOnly = false,
        hasProgress = false,
    )

    @Test
    fun eachPlayerGetsTheTableOfEveryRoundInRoundOrder() {
        val tables = listOf(
            table(2, 1, 1, 3),
            table(1, 2, 1, 4),
            table(1, 1, 2, 3),
            table(2, 2, 2, 4),
        )

        val result = roundTablesByPlayer(tables)

        assertEquals(listOf(PlayerRoundTable(1, 2), PlayerRoundTable(2, 1)), result[1])
        assertEquals(listOf(PlayerRoundTable(1, 1), PlayerRoundTable(2, 2)), result[2])
        assertEquals(null, result[5])
    }
}
