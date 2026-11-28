package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.util.render
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OfficialResultsExportValidationTest {
    @Test
    fun missingFinalScoresAreReported() = runTest {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = false,
                    isCompleted = true,
                    scores = listOf("", "", "", ""),
                ),
            ),
        )

        assertContains(error.render(), "Round 2, Table 3: missing final scores for East, South, West, and North.")
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
    fun handsTableMustBeMarkedCompleted() = runTest {
        val error = officialResultsExportError(
            listOf(
                rankingTable(
                    useTotalsOnly = false,
                    isCompleted = false,
                ),
            ),
        )

        assertContains(error.render(), "Round 2, Table 3: the table is not marked Completed.")
    }

    @Test
    fun aSeatWithoutAPlayerOfTheTableIsReported() = runTest {
        val error = officialResultsExportError(listOf(rankingTable(seats = listOf("1", "2", "3", "9"))))
        assertContains(error.render(), "North")
    }

    @Test
    fun aPlayerInTwoSeatsIsReported() = runTest {
        val error = officialResultsExportError(listOf(rankingTable(seats = listOf("1", "1", "3", "4"))))
        assertNotNull(error)
    }

    @Test
    fun scoresThatAreNotNumbersAreReported() = runTest {
        val error = officialResultsExportError(listOf(rankingTable(scores = listOf("x", "y", "-10000", "-30000"))))
        assertContains(error.render(), "East and South")
    }

    @Test
    fun tournamentWithoutTablesHasSpecificMessage() = runTest {
        assertEquals(
            "Official results cannot be exported because the tournament has no tables.",
            officialResultsExportError(emptyList()).render(),
        )
    }
}

private fun rankingTable(
    useTotalsOnly: Boolean = true,
    isCompleted: Boolean = false,
    scores: List<String> = listOf("30000", "10000", "-10000", "-30000"),
    seats: List<String> = listOf("1", "2", "3", "4"),
) = RankingTable(
    table = TableState(
        roundId = 2,
        tableId = 3,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = seats[0],
        playerSouthId = seats[1],
        playerWestId = seats[2],
        playerNorthId = seats[3],
        playerEastScore = scores[0],
        playerSouthScore = scores[1],
        playerWestScore = scores[2],
        playerNorthScore = scores[3],
        isCompleted = isCompleted,
        useTotalsOnly = useTotalsOnly,
    ),
    hands = emptyList(),
)
