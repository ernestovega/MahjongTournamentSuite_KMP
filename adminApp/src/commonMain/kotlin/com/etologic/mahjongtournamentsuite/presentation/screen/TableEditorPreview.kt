package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState

internal fun previewTableManagerEditorState(): TableManagerEditorState {
    return TableManagerEditorState.from(
        table = TableState(
            roundId = 2,
            tableId = 1,
            playerIds = listOf(101, 102, 103, 104),
            playerEastId = "101",
            playerSouthId = "102",
            playerWestId = "103",
            playerNorthId = "104",
            playerEastScore = "48",
            playerSouthScore = "32",
            playerWestScore = "-16",
            playerNorthScore = "-64",
            isCompleted = false,
            useTotalsOnly = true,
        ),
        hands = listOf(
            TableHand(
                handId = 1,
                playerWinnerId = "101",
                playerLooserId = "",
                handScore = "16",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 2,
                playerWinnerId = "102",
                playerLooserId = "104",
                handScore = "24",
                isChickenHand = true,
                isDone = true,
                playerEastPenalty = "",
                playerSouthPenalty = "",
                playerWestPenalty = "-8",
                playerNorthPenalty = "",
            ),
            TableHand(
                handId = 3,
                playerWinnerId = "103",
                playerLooserId = "",
                handScore = "12",
                isChickenHand = false,
                isDone = false,
                playerEastPenalty = "",
                playerSouthPenalty = "-4",
                playerWestPenalty = "",
                playerNorthPenalty = "",
            ),
        ),
    )
}

internal fun previewTablePlayerNamesById(): Map<Int, String> = mapOf(
    101 to "Aiko Tan",
    102 to "Bruno Lee",
    103 to "Carla Ruiz",
    104 to "Diego Mora",
)
