package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.add_penalty_to_hand
import mahjongtournamentsuite.adminapp.generated.resources.chicken
import mahjongtournamentsuite.adminapp.generated.resources.done
import mahjongtournamentsuite.adminapp.generated.resources.entry_penalty_n
import mahjongtournamentsuite.adminapp.generated.resources.hand_invalid
import mahjongtournamentsuite.adminapp.generated.resources.loser
import mahjongtournamentsuite.adminapp.generated.resources.penalty
import mahjongtournamentsuite.adminapp.generated.resources.remove_penalty_of_hand
import mahjongtournamentsuite.adminapp.generated.resources.score
import mahjongtournamentsuite.adminapp.generated.resources.winner
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * One hand on a phone. The wide table row does not fit, so the hand is a small card:
 * the number and the checks, then the winner and the loser, then the score and the penalties,
 * and at the end the result of each seat with the running total under it.
 */
@Composable
internal fun CompactHandCard(
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
    val shape = MaterialTheme.shapes.small
    val background = if (rowLocked) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f) else Color.Transparent
    val borderColor = if (hand.showValidationError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant

    val penaltyFocus = remember(hand.penaltyRows.size) {
        List(hand.penaltyRows.size) { List(4) { FocusRequester() } }
    }
    // The penalty row to focus after it is composed. A new row has no focus target until the next composition.
    var penaltyRowToFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(penaltyRowToFocus, penaltyFocus) {
        val row = penaltyRowToFocus ?: return@LaunchedEffect
        val target = penaltyFocus.getOrNull(row)?.firstOrNull() ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        penaltyRowToFocus = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (rowEnabled) 1f else 0.6f),
            )
            Spacer(Modifier.weight(1f))
            if (showChicken) {
                ToggleLabel(stringResource(Res.string.chicken), rowEnabled)
                HandToggleControl(
                    width = 48.dp,
                    label = null,
                    checked = hand.isChickenHand,
                    enabled = rowEnabled,
                    isChanged = hand.hasChickenHandChanged,
                    onCheckedChange = { hand.updateChickenHand(it) },
                    focusRequester = navigation.chicken,
                )
            }
            ToggleLabel(stringResource(Res.string.done), doneEnabled)
            HandToggleControl(
                width = 48.dp,
                label = null,
                checked = hand.isDone,
                enabled = doneEnabled,
                isChanged = hand.hasDoneChanged,
                onCheckedChange = { hand.updateDoneState(it) },
                focusRequester = navigation.done,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerDropdown(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.winner),
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
                focusRequester = navigation.winner,
            )
            PlayerDropdown(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.loser),
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
                focusRequester = navigation.loser,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                label = { Text(stringResource(Res.string.score)) },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) hand.markResultFieldsTouched()
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.penalty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { penaltyRowToFocus = hand.addPenaltyRow() }, enabled = rowEnabled) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_penalty_to_hand, index + 1))
                }
            }
        }
        val penaltyBackground = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        hand.penaltyRows.forEachIndexed { penaltyIndex, penalty ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(shape).background(penaltyBackground).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.entry_penalty_n, penaltyIndex + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { hand.removePenaltyRow(penaltyIndex) }, enabled = rowEnabled, buttonModifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.remove_penalty_of_hand, penaltyIndex + 1, index + 1),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    (0..3).forEach { seat ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            SeatWindIcon(seat, Modifier.size(16.dp))
                            SimpleHandCellField(
                                value = penalty.value(seat),
                                onValueChange = { hand.updatePenaltyValue(penaltyIndex, seat, it) },
                                signedIntegerOnly = true,
                                enabled = rowEnabled,
                                isChanged = hand.isPenaltyCellChanged(penaltyIndex, seat),
                                placeholder = "",
                                modifier = Modifier.weight(1f),
                                focusRequester = penaltyFocus.getOrNull(penaltyIndex)?.getOrNull(seat),
                            )
                        }
                    }
                }
            }
        }
        if (hand.showValidationError) {
            Text(
                text = listOfNotNull(hand.validationErrorMessage).plus(hand.validationDetailMessages)
                    .map { it.asString() }
                    .joinToString(" ")
                    .ifBlank { stringResource(Res.string.hand_invalid) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SeatValuesRow(values = result, subtotals = subtotal)
    }
}

/**
 * The value of each seat in four columns, each with its wind. [subtotals], when given, show small under the values.
 * The compact hand cards use it for the result of a hand and for the totals.
 */
@Composable
internal fun SeatValuesRow(
    values: SeatTextValues,
    subtotals: SeatTextValues? = null,
    names: List<String>? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        (0..3).forEach { seat ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeatWindIcon(seat, Modifier.size(16.dp))
                    Text(
                        text = values.bySeat(seat).ifBlank { "-" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
                names?.getOrNull(seat)?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                subtotals?.let {
                    Text(
                        text = "Σ " + it.bySeat(seat).ifBlank { "-" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleLabel(text: String, enabled: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
internal fun SeatWindIcon(seat: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(SeatWindIcons[seat]),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(LocalContentColor.current),
    )
}
