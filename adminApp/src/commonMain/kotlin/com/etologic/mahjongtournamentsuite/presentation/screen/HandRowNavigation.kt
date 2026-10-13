package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

internal val HandMiniFieldHeight = 38.dp

private val TableSummaryRowLabelWidth = 84.dp

internal enum class HandArrowDirection {
    Left,
    Right,
    Up,
    Down,
}

internal enum class HandFieldSlot {
    Winner,
    Loser,
    Score,
    Chicken,
    SubtotalEast,
    SubtotalSouth,
    SubtotalWest,
    SubtotalNorth,
    PenaltyEast,
    PenaltySouth,
    PenaltyWest,
    PenaltyNorth,
    Done,
}

internal class HandRowKeyboardNavigator {
    val winner = FocusRequester()
    val loser = FocusRequester()
    val score = FocusRequester()
    val chicken = FocusRequester()
    val subtotalEast = FocusRequester()
    val subtotalSouth = FocusRequester()
    val subtotalWest = FocusRequester()
    val subtotalNorth = FocusRequester()
    val penaltyEast = FocusRequester()
    val penaltySouth = FocusRequester()
    val penaltyWest = FocusRequester()
    val penaltyNorth = FocusRequester()
    val done = FocusRequester()
}

internal fun handFieldSlotAtOffset(slot: HandFieldSlot, direction: HandArrowDirection): HandFieldSlot? {
    val order = listOf(
        HandFieldSlot.Winner,
        HandFieldSlot.Loser,
        HandFieldSlot.Score,
        HandFieldSlot.Chicken,
        HandFieldSlot.Done,
    )
    val index = order.indexOf(slot)
    return when (direction) {
        HandArrowDirection.Left -> order.getOrNull(index - 1)
        HandArrowDirection.Right -> order.getOrNull(index + 1)
        else -> slot
    }
}

private fun HandRowKeyboardNavigator.focusRequesterFor(slot: HandFieldSlot): FocusRequester = when (slot) {
    HandFieldSlot.Winner -> winner
    HandFieldSlot.Loser -> loser
    HandFieldSlot.Score -> score
    HandFieldSlot.Chicken -> chicken
    HandFieldSlot.SubtotalEast -> subtotalEast
    HandFieldSlot.SubtotalSouth -> subtotalSouth
    HandFieldSlot.SubtotalWest -> subtotalWest
    HandFieldSlot.SubtotalNorth -> subtotalNorth
    HandFieldSlot.PenaltyEast -> penaltyEast
    HandFieldSlot.PenaltySouth -> penaltySouth
    HandFieldSlot.PenaltyWest -> penaltyWest
    HandFieldSlot.PenaltyNorth -> penaltyNorth
    HandFieldSlot.Done -> done
}

internal fun moveHandFieldFocus(
    current: HandFieldSlot,
    direction: HandArrowDirection,
    rowEnabled: Boolean,
    rowNavigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
): Boolean {
    if (!rowEnabled) return false
    val target = when (direction) {
        HandArrowDirection.Left, HandArrowDirection.Right -> {
            val slot = handFieldSlotAtOffset(current, direction) ?: return false
            rowNavigation.focusRequesterFor(slot)
        }

        HandArrowDirection.Up -> previousNavigation?.focusRequesterFor(current) ?: return false
        HandArrowDirection.Down -> nextNavigation?.focusRequesterFor(current) ?: return false
    }

    return runCatching {
        target.requestFocus()
        true
    }.getOrDefault(false)
}
