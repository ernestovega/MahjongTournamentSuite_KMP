package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableSaveConflictTest {
    @Test
    fun independentChangesMergeWithoutAChoice() {
        val base = table(eastScore = "0", southScore = "0", version = 1)
        val server = table(eastScore = "0", southScore = "20", version = 2)
        val conflict = TableSaveConflict(
            baseTable = base,
            baseHands = emptyList(),
            serverTable = server,
            serverHands = emptyList(),
            tablePatch = mapOf("playerEastScore" to "10"),
            handPatches = emptyMap(),
        )

        assertTrue(conflict.fields.isEmpty())
        assertEquals(mapOf("playerEastScore" to "10"), conflict.resolvedTablePatch(emptyMap()))
    }

    @Test
    fun sameFieldChangeRequiresMineOrServerChoice() {
        val conflict = TableSaveConflict(
            baseTable = table(eastScore = "0", version = 1),
            baseHands = emptyList(),
            serverTable = table(eastScore = "20", version = 2),
            serverHands = emptyList(),
            tablePatch = mapOf("playerEastScore" to "10"),
            handPatches = emptyMap(),
        )

        assertEquals(1, conflict.fields.size)
        assertEquals(
            emptyMap(),
            conflict.resolvedTablePatch(mapOf("table:playerEastScore" to ConflictChoice.SERVER)),
        )
        assertEquals(
            mapOf("playerEastScore" to "10"),
            conflict.resolvedTablePatch(mapOf("table:playerEastScore" to ConflictChoice.MINE)),
        )
    }

    private fun table(
        eastScore: String,
        southScore: String = "0",
        version: Long,
    ) = TableState(
        version = version,
        roundId = 1,
        tableId = 1,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "",
        playerSouthId = "",
        playerWestId = "",
        playerNorthId = "",
        playerEastScore = eastScore,
        playerSouthScore = southScore,
        playerWestScore = "0",
        playerNorthScore = "0",
        playerEastPoints = "",
        playerSouthPoints = "",
        playerWestPoints = "",
        playerNorthPoints = "",
        isCompleted = false,
        useTotalsOnly = true,
        usePointsCalculation = true,
    )
}
