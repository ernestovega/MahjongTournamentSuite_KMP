package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions

/** One hand as a table row. The penalty rows open below it and use the same columns as the seat totals. */
@Composable
internal fun HandTableRow(
    index: Int,
    hand: HandDraftState,
    playerIds: List<Int>,
    playerNamesById: Map<Int, String>,
    enabled: Boolean,
    forceDoneChecked: Boolean,
    result: SeatTextValues,
    subtotal: SeatTextValues,
    navigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
) {
    val showChicken = LocalHandStatOptions.current.countChickenHands
    val rowLocked = forceDoneChecked || hand.isDone
    val rowEnabled = enabled && !rowLocked
    val doneEnabled = enabled && !forceDoneChecked
    val rowBackground = if (rowLocked) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }

    // A hidden chicken column is skipped: moving across it starts from the chicken slot.
    fun move(slot: HandFieldSlot, direction: HandArrowDirection): Boolean = moveHandFieldFocus(
        current = if (!showChicken && handFieldSlotAtOffset(slot, direction) == HandFieldSlot.Chicken) {
            HandFieldSlot.Chicken
        } else {
            slot
        },
        direction = direction,
        rowEnabled = rowEnabled,
        rowNavigation = navigation,
        previousNavigation = previousNavigation,
        nextNavigation = nextNavigation,
    )

    val penaltyFocus = remember(hand.penaltyRows.size) {
        List(hand.penaltyRows.size) { List(4) { FocusRequester() } }
    }

    // The row to focus after it is composed. A new row has no focus target until the next composition.
    var penaltyRowToFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(penaltyRowToFocus, penaltyFocus) {
        val row = penaltyRowToFocus ?: return@LaunchedEffect
        val target = penaltyFocus.getOrNull(row)?.firstOrNull() ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        penaltyRowToFocus = null
    }

    fun movePenalty(row: Int, seat: Int, direction: HandArrowDirection): Boolean {
        if (!rowEnabled) return false
        val target = when (direction) {
            HandArrowDirection.Left -> penaltyFocus.getOrNull(row)?.getOrNull(seat - 1)
            HandArrowDirection.Right -> penaltyFocus.getOrNull(row)?.getOrNull(seat + 1)
            HandArrowDirection.Up -> penaltyFocus.getOrNull(row - 1)?.getOrNull(seat)
            HandArrowDirection.Down -> penaltyFocus.getOrNull(row + 1)?.getOrNull(seat)
        } ?: return false
        return runCatching {
            target.requestFocus()
            true
        }.getOrDefault(false)
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth).fillMaxHeight(), background = rowBackground) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (rowEnabled) 1f else 0.5f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
        GridRow(height = GridRowHeight, background = rowBackground, topDivider = false) {
            GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {
                PlayerDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = null,
                    playerIds = playerIds,
                    value = hand.playerWinnerId,
                    enabled = rowEnabled,
                    isChanged = hand.hasWinnerChanged,
                    onChange = { hand.setWinnerPlayerId(it) },
                    onDismiss = { hand.markResultFieldsTouched() },
                    onFocusLost = { hand.markResultFieldsTouched() },
                    playerNamesById = playerNamesById,
                    showPlayerId = true,
                    excludedPlayerIds = setOfNotNull(hand.selectedLoserPlayerId),
                    emptyOptionLabel = "-",
                    onNavigateLeft = { move(HandFieldSlot.Winner, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Winner, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Winner, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Winner, HandArrowDirection.Down) },
                    focusRequester = navigation.winner,
                )
            }
            GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {
                PlayerDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = null,
                    playerIds = playerIds,
                    value = hand.playerLooserId,
                    enabled = rowEnabled,
                    isChanged = hand.hasLoserChanged,
                    onChange = { hand.setLoserPlayerId(it) },
                    onDismiss = { hand.markResultFieldsTouched() },
                    onFocusLost = { hand.markResultFieldsTouched() },
                    playerNamesById = playerNamesById,
                    showPlayerId = true,
                    excludedPlayerIds = setOfNotNull(hand.selectedWinnerPlayerId),
                    emptyOptionLabel = "-",
                    onNavigateLeft = { move(HandFieldSlot.Loser, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Loser, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Loser, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Loser, HandArrowDirection.Down) },
                    focusRequester = navigation.loser,
                )
            }
            GridCell(modifier = Modifier.width(HandsScoreColumnWidth)) {
                KeyboardAwareOutlinedTextField(
                    value = hand.handScore,
                    onValueChange = { hand.updateHandScore(it) },
                    signedIntegerOnly = true,
                    enabled = rowEnabled,
                    isChanged = hand.hasScoreChanged,
                    isError = hand.showValidationError,
                    currentSlot = HandFieldSlot.Score,
                    rowEnabled = rowEnabled,
                    rowNavigation = navigation,
                    previousNavigation = previousNavigation,
                    nextNavigation = nextNavigation,
                    focusRequester = navigation.score,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) hand.markResultFieldsTouched()
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
            }
            if (showChicken) {
                GridCell(modifier = Modifier.width(HandsChickenColumnWidth)) {
                    HandToggleControl(
                        width = 48.dp,
                        label = null,
                        checked = hand.isChickenHand,
                        enabled = rowEnabled,
                        isChanged = hand.hasChickenHandChanged,
                        onCheckedChange = { hand.updateChickenHand(it) },
                        focusRequester = navigation.chicken,
                        onNavigateLeft = { move(HandFieldSlot.Chicken, HandArrowDirection.Left) },
                        onNavigateRight = { move(HandFieldSlot.Chicken, HandArrowDirection.Right) },
                        onNavigateUp = { move(HandFieldSlot.Chicken, HandArrowDirection.Up) },
                        onNavigateDown = { move(HandFieldSlot.Chicken, HandArrowDirection.Down) },
                    )
                }
            }
            listOf(result.east, result.south, result.west, result.north).forEach { value ->
                GridCell(modifier = Modifier.weight(HandsSeatColumnWeight)) {
                    Text(
                        text = value.ifBlank { "-" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth)) {
                IconButton(onClick = { penaltyRowToFocus = hand.addPenaltyRow() }, enabled = rowEnabled, buttonModifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add penalty to hand ${index + 1}",
                    )
                }
            }
            GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {
                HandToggleControl(
                    width = 48.dp,
                    label = null,
                    checked = hand.isDone,
                    enabled = doneEnabled,
                    isChanged = hand.hasDoneChanged,
                    onCheckedChange = { hand.updateDoneState(it) },
                    focusRequester = navigation.done,
                    onNavigateLeft = { move(HandFieldSlot.Done, HandArrowDirection.Left) },
                    onNavigateRight = { move(HandFieldSlot.Done, HandArrowDirection.Right) },
                    onNavigateUp = { move(HandFieldSlot.Done, HandArrowDirection.Up) },
                    onNavigateDown = { move(HandFieldSlot.Done, HandArrowDirection.Down) },
                )
            }
        }

        val penaltyBackground = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        hand.penaltyRows.forEachIndexed { penaltyIndex, penalty ->
            GridRow(height = GridRowHeight, background = penaltyBackground) {
                GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), alignment = Alignment.CenterStart) {
                    Text(
                        text = "Penalty ${penaltyIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight)) {}
                GridCell(modifier = Modifier.width(HandsScoreColumnWidth)) {}
                if (showChicken) GridCell(modifier = Modifier.width(HandsChickenColumnWidth)) {}
                (0..3).forEach { seat ->
                    GridCell(modifier = Modifier.weight(HandsSeatColumnWeight)) {
                        SimpleHandCellField(
                            value = penalty.value(seat),
                            onValueChange = { hand.updatePenaltyValue(penaltyIndex, seat, it) },
                            signedIntegerOnly = true,
                            enabled = rowEnabled,
                            isChanged = hand.isPenaltyCellChanged(penaltyIndex, seat),
                            placeholder = "",
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = penaltyFocus.getOrNull(penaltyIndex)?.getOrNull(seat),
                            onNavigateLeft = { movePenalty(penaltyIndex, seat, HandArrowDirection.Left) },
                            onNavigateRight = { movePenalty(penaltyIndex, seat, HandArrowDirection.Right) },
                            onNavigateUp = { movePenalty(penaltyIndex, seat, HandArrowDirection.Up) },
                            onNavigateDown = { movePenalty(penaltyIndex, seat, HandArrowDirection.Down) },
                        )
                    }
                }
                GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth)) {
                    IconButton(
                        onClick = { hand.removePenaltyRow(penaltyIndex) },
                        enabled = rowEnabled,
                        buttonModifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove penalty ${penaltyIndex + 1} of hand ${index + 1}",
                        )
                    }
                }
                GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
            }
        }
        }
    }

    if (hand.showValidationError) {
        GridRow(height = 40.dp, background = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
            Text(
                text = listOfNotNull(hand.validationErrorMessage).plus(hand.validationDetailMessages)
                    .joinToString(" ")
                    .ifBlank { "This hand is invalid and cannot be marked as done or completed." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = HandsIndexColumnWidth + 8.dp),
            )
        }
    }

    GridRow(height = HandSubtotalRowHeight) {
        GridCell(modifier = Modifier.width(HandsIndexColumnWidth), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.weight(HandsPlayerColumnWeight), last = true) {}
        GridCell(modifier = Modifier.width(HandsScoreColumnWidth), last = true) {}
        if (showChicken) GridCell(modifier = Modifier.width(HandsChickenColumnWidth), last = true) {}
        listOf(subtotal.east, subtotal.south, subtotal.west, subtotal.north).forEach { value ->
            GridCell(modifier = Modifier.weight(HandsSeatColumnWeight), last = true) {
                Text(
                    text = value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        GridCell(modifier = Modifier.width(HandsPenaltyColumnWidth), last = true) {}
        GridCell(modifier = Modifier.width(HandsDoneColumnWidth), last = true) {}
    }
}
