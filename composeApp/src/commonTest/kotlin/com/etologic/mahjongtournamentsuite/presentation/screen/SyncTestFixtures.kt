package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable

internal fun syncTable(
    version: Long = 1,
    roundId: Int = 1,
    tableId: Int = 1,
    east: String = "1",
    eastScore: String = "",
    isCompleted: Boolean = false,
) = TableState(
    version = version,
    roundId = roundId,
    tableId = tableId,
    playerIds = listOf(1, 2, 3, 4),
    playerEastId = east,
    playerSouthId = "2",
    playerWestId = "3",
    playerNorthId = "4",
    playerEastScore = eastScore,
    playerSouthScore = "",
    playerWestScore = "",
    playerNorthScore = "",
    playerEastPoints = "",
    playerSouthPoints = "",
    playerWestPoints = "",
    playerNorthPoints = "",
    isCompleted = isCompleted,
    useTotalsOnly = false,
    usePointsCalculation = true,
)

internal fun syncHands(score: String = "", count: Int = 16): List<TableHand> = (1..count).map { id ->
    TableHand(
        handId = id,
        playerWinnerId = "",
        playerLooserId = "",
        handScore = if (id == 1) score else "",
        isChickenHand = false,
        isDone = false,
        playerEastPenalty = "",
        playerSouthPenalty = "",
        playerWestPenalty = "",
        playerNorthPenalty = "",
    )
}

internal fun syncSummary(roundId: Int = 1, tableId: Int = 1, version: Long = 1) = TournamentTable(
    roundId = roundId,
    tableId = tableId,
    playerIds = listOf(1, 2, 3, 4),
    isCompleted = false,
    useTotalsOnly = false,
    usePointsCalculation = true,
    hasProgress = false,
    version = version,
)
