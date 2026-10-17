package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialResultsExportValidationTest {
    @Test
    fun missingFinalScoresAreReported() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = false,
                    isCompleted = true,
                    scores = listOf("", "", "", ""),
                ),
            ),
        )

        assertContains(error.orEmpty(), "Round 2, Table 3: missing final scores for East, South, West, and North.")
    }

    @Test
    fun validManualScoresDoNotNeedCompletedFlag() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = true,
                    isCompleted = false,
                    scores = listOf("30000", "10000", "-10000", "-35000"),
                ),
            ),
        )

        assertNull(error)
    }

    @Test
    fun handsTableMustBeMarkedCompleted() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = false,
                    isCompleted = false,
                ),
            ),
        )

        assertContains(error.orEmpty(), "Round 2, Table 3: the table is not marked Completed.")
    }

    @Test
    fun tournamentWithoutTablesHasSpecificMessage() {
        assertEquals(
            "Official results cannot be exported because the tournament has no tables.",
            officialResultsExportError(emptyList()),
        )
    }
}

private fun rankingTable(
    useTotalsOnly: Boolean = true,
    isCompleted: Boolean = false,
    scores: List<String> = listOf("30000", "10000", "-10000", "-30000"),
) = RankingTable(
    table = TableState(
        roundId = 2,
        tableId = 3,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1",
        playerSouthId = "2",
        playerWestId = "3",
        playerNorthId = "4",
        playerEastScore = scores[0],
        playerSouthScore = scores[1],
        playerWestScore = scores[2],
        playerNorthScore = scores[3],
        isCompleted = isCompleted,
        useTotalsOnly = useTotalsOnly,
    ),
    hands = emptyList(),
)
