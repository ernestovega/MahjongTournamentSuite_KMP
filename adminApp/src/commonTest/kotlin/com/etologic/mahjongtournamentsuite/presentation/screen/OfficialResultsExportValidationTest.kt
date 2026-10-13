package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialResultsExportValidationTest {
    @Test
    fun manualPointsExplainThatFinalScoresAreMissing() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = false,
                    usePointsCalculation = false,
                    scores = listOf("", "", "", ""),
                ),
            ),
        )

        assertContains(error.orEmpty(), "Round 2, Table 3: missing final scores for East, South, West, and North.")
        assertContains(error.orEmpty(), "Manual Points does not provide the final scores required for official results.")
    }

    @Test
    fun validManualScoresDoNotNeedCompletedFlag() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = true,
                    usePointsCalculation = true,
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
                    usePointsCalculation = true,
                    isCompleted = false,
                ),
            ),
        )

        assertContains(error.orEmpty(), "Round 2, Table 3: the table is not marked Completed.")
    }

    @Test
    fun invalidTablePointTotalIsReported() {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    scores = listOf("10", "10", "-5", "-5"),
                    points = listOf("4", "2", "1", "1"),
                ),
            ),
        )

        assertContains(error.orEmpty(), "table points total 8 instead of 7")
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
    usePointsCalculation: Boolean = true,
    isCompleted: Boolean = false,
    scores: List<String> = listOf("30000", "10000", "-10000", "-30000"),
    points: List<String> = listOf("4", "2", "1", "0"),
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
        playerEastPoints = points[0],
        playerSouthPoints = points[1],
        playerWestPoints = points[2],
        playerNorthPoints = points[3],
        isCompleted = isCompleted,
        useTotalsOnly = useTotalsOnly,
        usePointsCalculation = usePointsCalculation,
    ),
    hands = emptyList(),
)
