package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.SelfDrawIcon
import com.etologic.mahjongtournamentsuite.presentation.components.focusRing
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.add_penalty_to_hand
import mahjongtournamentsuite.adminapp.generated.resources.chicken_hand_two_lines
import mahjongtournamentsuite.adminapp.generated.resources.close_hand_n
import mahjongtournamentsuite.adminapp.generated.resources.done
import mahjongtournamentsuite.adminapp.generated.resources.entry_penalty_n
import mahjongtournamentsuite.adminapp.generated.resources.hand_draw
import mahjongtournamentsuite.adminapp.generated.resources.hand_invalid
import mahjongtournamentsuite.adminapp.generated.resources.hand_self_draw
import mahjongtournamentsuite.adminapp.generated.resources.loser
import mahjongtournamentsuite.adminapp.generated.resources.open_hand_n
import mahjongtournamentsuite.adminapp.generated.resources.penalty
import mahjongtournamentsuite.adminapp.generated.resources.remove_penalty_of_hand
import mahjongtournamentsuite.adminapp.generated.resources.score
import mahjongtournamentsuite.adminapp.generated.resources.winner
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

/** The width of the hand number on a wide screen. The header of the hands uses it too. */
internal val HandNumberWidth = 40.dp

/** The width of the Done column and of the open/close arrow on a wide screen. The header of the hands uses them too. */
internal val HandDoneWidth = 64.dp
internal val HandArrowWidth = 44.dp

/** The weight of the summary of a hand on a wide screen, next to the four seat columns of weight 1. */
internal const val HandSummaryWeight = 2.4f

/**
 * One hand of a table. A closed hand is one line: who won from whom and the score, the result of each seat with the
 * running total (Σ) under it, and Done. An open hand chooses the winner and the loser with one button per seat (a
 * self-draw is the first loser button), and shows what the hand adds before Done is ticked.
 * [wide] puts the results in the seat columns of the header of the hands, for a desktop or a tablet.
 */
@Composable
internal fun HandCard(
    index: Int,
    hand: HandDraftState,
    seatPlayerIds: List<String>,
    seatNames: List<String>,
    enabled: Boolean,
    forceDoneChecked: Boolean,
    canMarkDone: Boolean,
    result: SeatTextValues,
    subtotal: SeatTextValues,
    preview: Pair<SeatTextValues, SeatTextValues>?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    wide: Boolean,
    navigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
) {
    val number = index + 1
    val rowLocked = forceDoneChecked || hand.isDone
    val rowEnabled = enabled && !rowLocked
    val doneEnabled = enabled && !forceDoneChecked && (hand.isDone || canMarkDone)
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        hand.showValidationError || hand.hasIncompletePenalty -> colors.error
        expanded && !rowLocked -> colors.primary
        else -> colors.outlineVariant
    }
    val background = if (rowLocked) colors.surfaceContainerHigh.copy(alpha = 0.55f) else Color.Transparent
    // A counted hand shows its result and Σ. An open hand that does not count yet shows what it would add.
    val counts = result != SeatTextValues.EMPTY
    val shownValues = if (counts) result else preview?.first ?: SeatTextValues.EMPTY
    val shownTotals = if (counts) subtotal else preview?.second ?: SeatTextValues.EMPTY
    val isPreview = !counts && preview != null && expanded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hand-$number")
            .clip(shape)
            .border(if (expanded && !rowLocked) 1.5.dp else 1.dp, borderColor, shape)
            .background(background)
            .padding(horizontal = if (wide) 4.dp else 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val toggle = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) { onExpandedChange(!expanded) }
            Text(
                text = if (wide) "$number" else "#$number",
                modifier = (if (wide) Modifier.width(HandNumberWidth) else Modifier.padding(end = 8.dp)).then(toggle),
                style = MaterialTheme.typography.titleSmall.inTextFont(),
                fontWeight = FontWeight.Bold,
                textAlign = if (wide) TextAlign.Center else TextAlign.Start,
                color = colors.onSurfaceVariant,
            )
            if (wide) {
                HandSummary(hand, seatPlayerIds, seatNames, Modifier.weight(HandSummaryWeight).then(toggle))
                Column(Modifier.width(SigmaColumnWidth)) {
                    Text(" ", style = MaterialTheme.typography.bodyLarge)
                    if (shownTotals != SeatTextValues.EMPTY) SigmaLabel()
                }
                (0..3).forEach { seat ->
                    SeatValueColumn(
                        value = shownValues.bySeat(seat),
                        total = shownTotals.bySeat(seat),
                        muted = isPreview,
                        modifier = Modifier.weight(1f).then(toggle),
                    )
                }
            } else if (!expanded) {
                HandSummary(hand, seatPlayerIds, seatNames, Modifier.weight(1f).then(toggle))
            } else {
                Spacer(Modifier.weight(1f).height(24.dp).then(toggle))
            }
            DoneControl(number, hand, doneEnabled, wide, navigation)
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = (if (wide) Modifier.width(HandArrowWidth) else Modifier).testTag("hand-$number-toggle"),
                buttonModifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) Res.string.close_hand_n else Res.string.open_hand_n, number),
                )
            }
        }
        if (expanded) {
            HandEditor(
                number = number,
                hand = hand,
                seatPlayerIds = seatPlayerIds,
                seatNames = seatNames,
                rowEnabled = rowEnabled,
                wide = wide,
                navigation = navigation,
                previousNavigation = previousNavigation,
                nextNavigation = nextNavigation,
            )
        }
        if (!wide && (counts || isPreview)) {
            SeatValuesRow(values = shownValues, subtotals = shownTotals, showWinds = false, muted = isPreview)
        }
    }
}

/** Who won from whom and the score, in one line. A hand without a winner is a draw. */
@Composable
private fun HandSummary(hand: HandDraftState, seatPlayerIds: List<String>, seatNames: List<String>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    fun nameOf(id: String): String = seatPlayerIds.indexOf(id).takeIf { it >= 0 }?.let { seatNames[it] } ?: id
    val winner = hand.playerWinnerId.trim()
    val loser = hand.normalizedLoserId
    val score = hand.handScore.trim()
    val penalties = hand.penaltyRows.count { row -> (0..3).any { row.value(it).isNotBlank() } }
    val isEmpty = hand.isBlank
    Row(modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            isEmpty -> Text("-", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            winner.isEmpty() -> Text(
                text = stringResource(Res.string.hand_draw),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            else -> {
                Text(
                    text = nameOf(winner) + if (loser.isNotEmpty()) " ← ${nameOf(loser)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (loser.isEmpty()) {
                    SelfDrawIcon(16.dp, tint = LocalContentColor.current)
                    Text(stringResource(Res.string.hand_self_draw), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                if (score.isNotEmpty()) Text("· $score", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        if (hand.isChickenHand) ChickenIcon(16.dp)
        if (penalties > 0) {
            Text(
                text = "P$penalties",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.tertiary,
            )
        }
    }
}

/** The result of one seat with the running total under it, for the seat columns of a wide screen. */
@Composable
private fun SeatValueColumn(value: String, total: String, muted: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (value.isBlank()) {
            Text("-", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
            )
            if (total.isNotBlank()) {
                Text(
                    text = total,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DoneControl(number: Int, hand: HandDraftState, enabled: Boolean, wide: Boolean, navigation: HandRowKeyboardNavigator) {
    Row(
        modifier = (if (wide) Modifier.width(HandDoneWidth) else Modifier).testTag("hand-$number-done"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (!wide) {
            Text(
                text = stringResource(Res.string.done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        HandToggleControl(
            width = 44.dp,
            label = null,
            checked = hand.isDone,
            enabled = enabled,
            isChanged = hand.hasDoneChanged,
            onCheckedChange = { hand.updateDoneState(it) },
            focusRequester = navigation.done,
        )
    }
}

/** The fields of an open hand: the seat buttons, the penalties, the score and the chicken hand. */
@Composable
private fun ColumnScope.HandEditor(
    number: Int,
    hand: HandDraftState,
    seatPlayerIds: List<String>,
    seatNames: List<String>,
    rowEnabled: Boolean,
    wide: Boolean,
    navigation: HandRowKeyboardNavigator,
    previousNavigation: HandRowKeyboardNavigator?,
    nextNavigation: HandRowKeyboardNavigator?,
) {
    val showChicken = LocalHandStatOptions.current.countChickenHands
    val winner = hand.playerWinnerId.trim()
    val loser = hand.normalizedLoserId
    val sidePadding = if (wide) Modifier.padding(horizontal = 8.dp) else Modifier

    val winnerButtons: @Composable RowScope.() -> Unit = {
        seatPlayerIds.forEachIndexed { seat, id ->
            SeatButton(
                seat = seat,
                label = seatNames[seat],
                selected = id.isNotEmpty() && winner == id,
                accent = WinnerGreen,
                enabled = rowEnabled && id.isNotEmpty(),
                tag = "hand-$number-winner-$id",
                onClick = {
                    hand.setWinnerPlayerId(if (winner == id) "" else id)
                    hand.markResultFieldsTouched()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    val loserButtons: @Composable RowScope.() -> Unit = {
        SeatButton(
            seat = null,
            label = stringResource(Res.string.hand_self_draw),
            selected = winner.isNotEmpty() && loser.isEmpty(),
            accent = MaterialTheme.colorScheme.primary,
            enabled = rowEnabled,
            tag = "hand-$number-loser-self",
            onClick = { hand.setLoserPlayerId("") },
            modifier = Modifier.weight(1f),
            compactLabel = !wide,
        )
        seatPlayerIds.forEachIndexed { seat, id ->
            SeatButton(
                seat = seat,
                label = seatNames[seat],
                selected = id.isNotEmpty() && loser == id,
                accent = MaterialTheme.colorScheme.error,
                enabled = rowEnabled && id.isNotEmpty() && id != winner,
                tag = "hand-$number-loser-$id",
                onClick = { hand.setLoserPlayerId(if (loser == id) "" else id) },
                modifier = Modifier.weight(1f),
                compactLabel = !wide,
            )
        }
    }

    if (wide) {
        Row(sidePadding, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(4f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(Res.string.winner))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = winnerButtons)
            }
            Column(Modifier.weight(5f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(Res.string.loser))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = loserButtons)
            }
        }
    } else {
        FieldLabel(stringResource(Res.string.winner))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = winnerButtons)
        FieldLabel(stringResource(Res.string.loser))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = loserButtons)
    }

    val penaltyFocus = remember(hand.penaltyRows.size) { List(hand.penaltyRows.size) { FocusRequester() } }
    // The new penalty has no focus target until the next composition.
    var penaltyRowToFocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(penaltyRowToFocus, penaltyFocus) {
        val row = penaltyRowToFocus ?: return@LaunchedEffect
        val target = penaltyFocus.getOrNull(row) ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { target.requestFocus() }
        penaltyRowToFocus = null
    }

    // Add a penalty on the left; the score and the chicken hand on the right.
    Row(sidePadding.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DebouncedTextButton(onClick = { penaltyRowToFocus = hand.addPenaltyRow() }, enabled = rowEnabled) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_penalty_to_hand, number), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.penalty))
        }
        Spacer(Modifier.weight(1f))
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
                .width(96.dp)
                .testTag("hand-$number-score")
                .onFocusChanged { if (!it.isFocused) hand.markResultFieldsTouched() },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        )
        if (showChicken) {
            Text(
                text = stringResource(Res.string.chicken_hand_two_lines),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (rowEnabled) 1f else 0.5f),
                textAlign = TextAlign.End,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize,
                maxLines = 2,
                modifier = Modifier.padding(start = 12.dp),
            )
            Row(Modifier.testTag("hand-$number-chicken")) {
                HandToggleControl(
                    width = 44.dp,
                    label = null,
                    checked = hand.isChickenHand,
                    enabled = rowEnabled,
                    isChanged = hand.hasChickenHandChanged,
                    onCheckedChange = { hand.updateChickenHand(it) },
                    focusRequester = navigation.chicken,
                )
            }
        }
    }

    hand.penaltyRows.forEachIndexed { penaltyIndex, penalty ->
        PenaltyEditorRow(
            number = number,
            penaltyNumber = penaltyIndex + 1,
            hand = hand,
            penalty = penalty,
            penaltyIndex = penaltyIndex,
            rowEnabled = rowEnabled,
            wide = wide,
            focusRequester = penaltyFocus.getOrNull(penaltyIndex),
        )
    }

    val messages = buildList {
        if (hand.showValidationError) {
            addAll(listOfNotNull(hand.validationErrorMessage).plus(hand.validationDetailMessages).map { it.asString() })
            if (isEmpty()) add(stringResource(Res.string.hand_invalid))
        }
        addAll(hand.penaltyMessages.map { it.asString() })
    }
    if (messages.isNotEmpty()) {
        Text(
            text = messages.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = sidePadding,
        )
    }
}

/** One penalty: a value for each seat. On a wide screen the values sit in the seat columns of the header. */
@Composable
private fun PenaltyEditorRow(
    number: Int,
    penaltyNumber: Int,
    hand: HandDraftState,
    penalty: PenaltyDraftState,
    penaltyIndex: Int,
    rowEnabled: Boolean,
    wide: Boolean,
    focusRequester: FocusRequester?,
) {
    val incomplete = penaltyNumber in hand.incompletePenaltyNumbers
    val background = if (incomplete) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
    }
    val remove: @Composable () -> Unit = {
        IconButton(onClick = { hand.removePenaltyRow(penaltyIndex) }, enabled = rowEnabled, buttonModifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove_penalty_of_hand, penaltyNumber, number))
        }
    }
    val field: @Composable (Int, Modifier) -> Unit = { seat, modifier ->
        SimpleHandCellField(
            value = penalty.value(seat),
            onValueChange = { hand.updatePenaltyValue(penaltyIndex, seat, it) },
            signedIntegerOnly = true,
            enabled = rowEnabled,
            isChanged = hand.isPenaltyCellChanged(penaltyIndex, seat),
            placeholder = "",
            modifier = modifier,
            focusRequester = if (seat == 0) focusRequester else null,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(background)
            .padding(vertical = 4.dp, horizontal = if (wide) 0.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Spacer(Modifier.width(HandNumberWidth))
            Text(
                text = stringResource(Res.string.entry_penalty_n, penaltyNumber),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(HandSummaryWeight),
            )
            Spacer(Modifier.width(SigmaColumnWidth))
            (0..3).forEach { seat -> field(seat, Modifier.weight(1f).padding(horizontal = 10.dp)) }
            Spacer(Modifier.width(HandDoneWidth))
            Row(Modifier.width(HandArrowWidth), horizontalArrangement = Arrangement.Center) { remove() }
        } else {
            Text(
                text = "P$penaltyNumber",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            (0..3).forEach { seat ->
                Row(Modifier.weight(1f).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeatWindIcon(seat, Modifier.size(14.dp))
                    field(seat, Modifier.weight(1f))
                }
            }
            remove()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val WinnerGreen = Color(0xFF22A45D)

/**
 * A button for one seat, with its wind and the name of the player, or for a self-draw when [seat] is null.
 * The selected button has a coloured border and background.
 */
@Composable
private fun SeatButton(
    seat: Int?,
    label: String,
    selected: Boolean,
    accent: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compactLabel: Boolean = false,
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val alpha = if (enabled || selected) 1f else 0.38f
    Column(
        modifier = modifier
            .testTag(tag)
            .focusRing(focused && enabled, shape)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else colors.outlineVariant.copy(alpha = alpha), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val tint = colors.onSurface.copy(alpha = alpha)
        if (seat == null) {
            SelfDrawIcon(18.dp, tint = tint)
        } else {
            Image(
                painter = painterResource(SeatWindIcons[seat]),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        }
        Text(
            text = label,
            style = if (compactLabel) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
