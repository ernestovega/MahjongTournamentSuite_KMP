package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.hand_chicken_maximum
import mahjongtournamentsuite.adminapp.generated.resources.hand_invalid
import mahjongtournamentsuite.adminapp.generated.resources.hand_loser_without_winner
import mahjongtournamentsuite.adminapp.generated.resources.hand_missing_fields
import mahjongtournamentsuite.adminapp.generated.resources.hand_score_minimum
import mahjongtournamentsuite.adminapp.generated.resources.hand_score_whole
import org.jetbrains.compose.resources.stringResource

@Stable
internal class PenaltyDraftState(
    east: String = "",
    south: String = "",
    west: String = "",
    north: String = "",
) {
    var east by mutableStateOf(east)
    var south by mutableStateOf(south)
    var west by mutableStateOf(west)
    var north by mutableStateOf(north)

    fun value(seatIndex: Int): String = when (seatIndex) {
        0 -> east
        1 -> south
        2 -> west
        else -> north
    }

    fun setValue(seatIndex: Int, value: String) {
        when (seatIndex) {
            0 -> east = value
            1 -> south = value
            2 -> west = value
            else -> north = value
        }
    }
}

@Stable
internal class HandDraftState private constructor(
    private val initial: TableHand,
) {
    val handId: Int = initial.handId

    var playerWinnerId by mutableStateOf(initial.playerWinnerId)
    var playerLooserId by mutableStateOf(initial.playerLooserId)
    var handScore by mutableStateOf(initial.handScore)
    var isChickenHand by mutableStateOf(initial.isChickenHand)
    var isDone by mutableStateOf(initial.isDone)
    var playerEastPenalty by mutableStateOf(initial.playerEastPenalty)
    var playerSouthPenalty by mutableStateOf(initial.playerSouthPenalty)
    var playerWestPenalty by mutableStateOf(initial.playerWestPenalty)
    var playerNorthPenalty by mutableStateOf(initial.playerNorthPenalty)
    val penaltyRows = mutableStateListOf<PenaltyDraftState>()
    private var penaltyRowsEdited by mutableStateOf(false)
    private var resultFieldsTouched by mutableStateOf(false)

    init {
        penaltyRows += initialPenaltyRows()
    }

    val hasLoserSelected: Boolean
        get() = normalizedLoserId.isNotEmpty()

    val selectedWinnerPlayerId: Int?
        get() = playerWinnerId.trim().toIntOrNull()

    val selectedLoserPlayerId: Int?
        get() = normalizedLoserId.toIntOrNull()

    val normalizedLoserId: String
        get() = playerLooserId.trim().takeUnless { it == "-" }.orEmpty()

    val showValidationError: Boolean
        get() = resultFieldsTouched && isResultSelectionInvalid

    val validationErrorMessage: UiText?
        get() = when {
            !isResultSelectionInvalid -> null
            isCompletelyEmpty -> null
            else -> UiText.of(Res.string.hand_invalid)
        }

    val validationDetailMessages: List<UiText>
        get() {
            if (!isResultSelectionInvalid || isCompletelyEmpty) return emptyList()

            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()
            val parsedScore = score.toIntOrNull()
            val messages = mutableListOf<UiText>()

            if (winnerId.isEmpty() || score.isEmpty()) {
                messages += UiText.of(Res.string.hand_missing_fields)
            }

            if (winnerId.isEmpty() && loserId.isNotEmpty()) {
                messages += UiText.of(Res.string.hand_loser_without_winner)
            }

            if (score.isNotEmpty() && parsedScore == null) {
                messages += UiText.of(Res.string.hand_score_whole)
            }

            if (parsedScore != null && parsedScore < MIN_HAND_SCORE) {
                messages += UiText.of(Res.string.hand_score_minimum)
            }

            if (parsedScore != null && isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) {
                messages += UiText.of(Res.string.hand_chicken_maximum)
            }

            return messages.distinct()
        }

    val hasWinnerChanged: Boolean get() = playerWinnerId.trim() != initial.playerWinnerId
    val hasLoserChanged: Boolean get() = normalizedLoserId != initial.playerLooserId
    val hasScoreChanged: Boolean get() = handScore.trim() != initial.handScore
    val hasChickenHandChanged: Boolean get() = isChickenHand != initial.isChickenHand
    val hasDoneChanged: Boolean get() = isDone != initial.isDone

    /** A penalty cell shows as changed only when its text differs from the saved text. An empty new cell does not. */
    fun isPenaltyCellChanged(rowIndex: Int, seatIndex: Int): Boolean {
        val current = penaltyRows.getOrNull(rowIndex)?.value(seatIndex)?.trim().orEmpty()
        val saved = initialPenaltyRows().getOrNull(rowIndex)?.value(seatIndex)?.trim().orEmpty()
        return current != saved
    }

    /** True when the hand has no data at all. It is not done and has no penalty. */
    val isBlank: Boolean
        get() = !isDone && !isChickenHand &&
            playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty() &&
            penaltyRows.none { row -> (0..3).any { row.value(it).isNotBlank() } }

    val isIgnoredForCalculation: Boolean
        get() = isResultSelectionInvalid || isCompletelyEmpty

    private val isCompletelyEmpty: Boolean
        get() = playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty() &&
            !isChickenHand

    val isResultSelectionInvalid: Boolean
        get() {
            val winnerId = playerWinnerId.trim()
            val loserId = normalizedLoserId
            val score = handScore.trim()

            if (winnerId.isEmpty() && loserId.isEmpty() && score.isEmpty()) return isChickenHand
            if (winnerId.isEmpty() && loserId.isEmpty() && score == "0") return isChickenHand
            if (winnerId.isEmpty() && loserId.isNotEmpty()) return true
            if (winnerId.isEmpty() || score.isEmpty()) return true

            val parsedScore = score.toIntOrNull() ?: return true
            if (parsedScore < MIN_HAND_SCORE) return true
            if (isChickenHand && parsedScore > MAX_CHICKEN_HAND_SCORE) return true
            return false
        }

    fun setWinnerPlayerId(value: String) {
        val trimmed = value.trim().takeUnless { it == "-" }.orEmpty()
        playerWinnerId = trimmed
        if (trimmed.isNotEmpty() && normalizedLoserId == trimmed) {
            playerLooserId = "-"
            isChickenHand = false
        }
    }

    fun setLoserPlayerId(value: String) {
        playerLooserId = value.trim().ifBlank { "-" }
        if (!hasLoserSelected) {
            isChickenHand = false
        }
        markResultFieldsTouched()
    }

    fun updateHandScore(value: String) {
        handScore = value.toSignedIntegerInput()
        markResultFieldsTouched()
    }

    fun updateChickenHand(value: Boolean) {
        isChickenHand = value
        markResultFieldsTouched()
    }

    fun updateDoneState(value: Boolean) {
        if (!value) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        if (isResultSelectionInvalid) {
            isDone = false
            markResultFieldsTouched()
            return
        }
        // A done hand with no winner, loser or score is a draw. It scores zero.
        if (playerWinnerId.trim().isEmpty() && normalizedLoserId.isEmpty() && handScore.trim().isEmpty()) {
            handScore = "0"
        }
        removeEmptyPenaltyRows()
        isDone = true
        markResultFieldsTouched()
    }

    fun markResultFieldsTouched() {
        resultFieldsTouched = true
    }

    fun updatePenaltyValue(rowIndex: Int, seatIndex: Int, value: String) {
        penaltyRows[rowIndex].setValue(seatIndex, value.toSignedIntegerInput())
        penaltyRowsEdited = true
    }

    /** Adds a penalty row and returns its index. If an empty row already exists, it returns that row instead. */
    fun addPenaltyRow(): Int {
        val emptyIndex = penaltyRows.indexOfFirst { row -> (0..3).all { row.value(it).isBlank() } }
        if (emptyIndex >= 0) return emptyIndex
        penaltyRows += PenaltyDraftState()
        penaltyRowsEdited = true
        return penaltyRows.lastIndex
    }

    fun removePenaltyRow(rowIndex: Int) {
        penaltyRows.removeAt(rowIndex)
        penaltyRowsEdited = true
    }

    private fun removeEmptyPenaltyRows() {
        val removed = penaltyRows.removeAll { row -> (0..3).all { row.value(it).isBlank() } }
        if (removed) penaltyRowsEdited = true
    }

    /** One row for each ";" separated part of the saved penalties. No row when the hand has no penalty. */
    private fun initialPenaltyRows(): List<PenaltyDraftState> {
        val parts = listOf(
            initial.playerEastPenalty,
            initial.playerSouthPenalty,
            initial.playerWestPenalty,
            initial.playerNorthPenalty,
        ).map { value -> value.split(';').map { it.trim() } }
        val rowCount = parts.maxOf { it.size }
        val hasAny = parts.any { seat -> seat.any { it.isNotEmpty() } }
        if (!hasAny) return emptyList()
        return List(rowCount) { row ->
            PenaltyDraftState(
                east = parts[0].getOrElse(row) { "" },
                south = parts[1].getOrElse(row) { "" },
                west = parts[2].getOrElse(row) { "" },
                north = parts[3].getOrElse(row) { "" },
            )
        }
    }

    fun reset() {
        playerWinnerId = initial.playerWinnerId
        playerLooserId = initial.playerLooserId.ifBlank { "-" }
        handScore = initial.handScore
        isChickenHand = initial.isChickenHand
        isDone = initial.isDone
        playerEastPenalty = initial.playerEastPenalty
        playerSouthPenalty = initial.playerSouthPenalty
        playerWestPenalty = initial.playerWestPenalty
        playerNorthPenalty = initial.playerNorthPenalty
        penaltyRows.clear()
        penaltyRows += initialPenaltyRows()
        penaltyRowsEdited = false
        resultFieldsTouched = false
    }

    fun snapshot(): TableHand = TableHand(
        handId = handId,
        playerWinnerId = playerWinnerId.trim(),
        playerLooserId = normalizedLoserId,
        handScore = handScore.trim(),
        isChickenHand = isChickenHand,
        isDone = isDone,
        playerEastPenalty = currentPenalty(0),
        playerSouthPenalty = currentPenalty(1),
        playerWestPenalty = currentPenalty(2),
        playerNorthPenalty = currentPenalty(3),
    )

    fun copyValuesFrom(other: HandDraftState) {
        playerWinnerId = other.playerWinnerId
        playerLooserId = other.playerLooserId
        handScore = other.handScore
        isChickenHand = other.isChickenHand
        isDone = other.isDone
        playerEastPenalty = other.playerEastPenalty
        playerSouthPenalty = other.playerSouthPenalty
        playerWestPenalty = other.playerWestPenalty
        playerNorthPenalty = other.playerNorthPenalty
        penaltyRows.clear()
        other.penaltyRows.forEach { row ->
            penaltyRows += PenaltyDraftState(row.east, row.south, row.west, row.north)
        }
        penaltyRowsEdited = other.penaltyRowsEdited
        resultFieldsTouched = other.resultFieldsTouched
    }

    fun buildPatch(): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>()

        fun putIfChanged(key: String, current: Any?, initialValue: Any?) {
            if (current != initialValue) patch[key] = current
        }

        putIfChanged("playerWinnerId", playerWinnerId.trim(), initial.playerWinnerId)
        putIfChanged("playerLooserId", normalizedLoserId, initial.playerLooserId)
        putIfChanged("handScore", handScore.trim(), initial.handScore)
        putIfChanged("isChickenHand", isChickenHand, initial.isChickenHand)
        putIfChanged("isDone", isDone, initial.isDone)
        putIfChanged("playerEastPenalty", currentPenalty(0), initial.playerEastPenalty)
        putIfChanged("playerSouthPenalty", currentPenalty(1), initial.playerSouthPenalty)
        putIfChanged("playerWestPenalty", currentPenalty(2), initial.playerWestPenalty)
        putIfChanged("playerNorthPenalty", currentPenalty(3), initial.playerNorthPenalty)

        return patch
    }

    /** The penalties of one seat as saved text. Several rows join with ";". */
    fun currentPenalty(seatIndex: Int): String {
        if (!penaltyRowsEdited) return when (seatIndex) {
            0 -> playerEastPenalty.trim()
            1 -> playerSouthPenalty.trim()
            2 -> playerWestPenalty.trim()
            else -> playerNorthPenalty.trim()
        }
        // Empty rows at the end carry no data, so they do not change the saved text.
        return penaltyRows.joinToString(";") { it.value(seatIndex).trim() }
    }

    companion object {
        fun from(hand: TableHand): HandDraftState = HandDraftState(hand).also { draft ->
            if (draft.playerLooserId.trim().isEmpty()) {
                draft.playerLooserId = "-"
            }
        }
    }
}
