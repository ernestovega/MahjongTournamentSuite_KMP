package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.etologic.mahjongtournamentsuite.presentation.components.HintTooltip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton

@Composable
internal fun TableResultsCard(
    editor: TableManagerEditorState,
    enabled: Boolean,
    dependentEnabled: Boolean,
    playerNamesById: Map<Int, String>,
    scoreFocusRequester: FocusRequester?,
    otherTablesBestHandScores: List<Int>,
) {
    var editingSeats by remember(editor.roundId, editor.tableId) { mutableStateOf(false) }
    val showSeatSelectors = !editor.hasCompleteSeatPositions || editingSeats
    val seatIds = listOf(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
    val seatChanged = listOf(
        editor.hasEastSeatChanged,
        editor.hasSouthSeatChanged,
        editor.hasWestSeatChanged,
        editor.hasNorthSeatChanged,
    )
    val scores = listOf(editor.displayEastScore, editor.displaySouthScore, editor.displayWestScore, editor.displayNorthScore)
    val scoreChanged = listOf(
        editor.hasEastScoreChanged,
        editor.hasSouthScoreChanged,
        editor.hasWestScoreChanged,
        editor.hasNorthScoreChanged,
    )
    val points = listOf(editor.displayEastPoints, editor.displaySouthPoints, editor.displayWestPoints, editor.displayNorthPoints)
    val marks = seatMarksByPlayerId(editor.playerEastId, editor.playerSouthId, editor.playerWestId, editor.playerNorthId)
    val checks = scoreChecks(editor, seatIds, playerNamesById)
    // Names, total scores and table points share this style. The Gang of Three font is only for titles and selectors.
    val bigNumberStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

    SectionCard(
        title = "Players & results",
        titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        subtitle = if (!editor.hasCompleteSeatPositions) "Select all four seat positions first" else null,
        verticalSpacing = 20.dp,
        centerTitle = true,
        titleActionAtStart = true,
        titleAction = { HandSummaryTitleAction(editor, otherTablesBestHandScores) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editor.hasCompleteSeatPositions) {
                    FocusedTextButton(onClick = { editingSeats = !editingSeats }, enabled = enabled) {
                        Text(if (editingSeats) "Done" else "Edit seats")
                    }
                }
                LabeledSwitch(
                    label = "Manual scores",
                    description = "When on, enter each player's final score yourself. The hands do not count (except for counting chicken and best hands).",
                    checked = editor.useTotalsOnly,
                    enabled = dependentEnabled,
                    onCheckedChange = { manual ->
                        if (manual) editor.enableManualTotals() else editor.disableManualTotals()
                    },
                )
            }
        },
    ) {
        GridTable {
            GridRow(height = GridHeaderHeight, topDivider = false) {
                GridHeaderCell(text = "", modifier = Modifier.width(GridLabelColumnWidth))
                SeatLabels.forEachIndexed { seat, label ->
                    GridHeaderCell(text = label, modifier = Modifier.weight(1f), icon = SeatWindIcons[seat], last = seat == 3)
                }
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Player")
                seatIds.forEachIndexed { seat, id ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        if (showSeatSelectors) {
                            PlayerDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                label = null,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                                playerIds = editor.playerIds,
                                value = id,
                                enabled = enabled,
                                isChanged = seatChanged[seat],
                                onChange = { editor.setSeatAssignment(seat, it) },
                                playerNamesById = playerNamesById,
                                showPlayerId = true,
                                optionSuffixById = marks,
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = playerNameOrNull(id, playerNamesById) ?: "-",
                                    style = bigNumberStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Text(
                                    text = "#${id.trim()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            val seatScoreFocus = remember(scoreFocusRequester) {
                List(4) { seat -> if (seat == 0 && scoreFocusRequester != null) scoreFocusRequester else FocusRequester() }
            }
            fun moveScoreFocus(seat: Int): Boolean {
                val target = seatScoreFocus.getOrNull(seat) ?: return false
                return runCatching { target.requestFocus(); true }.getOrDefault(false)
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Total Scores")
                scores.forEachIndexed { seat, value ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The first slot balances the check slot, so the score stays centered in the cell.
                            Spacer(Modifier.width(ScoreCheckSlotWidth))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (editor.useTotalsOnly) {
                                    CompactOutlinedTextField(
                                        value = value,
                                        onValueChange = { editor.updateManualScore(seat, it) },
                                        enabled = dependentEnabled,
                                        isChanged = scoreChanged[seat],
                                        textStyle = bigNumberStyle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(seatScoreFocus[seat]),
                                        onNavigateLeft = { moveScoreFocus(seat - 1) },
                                        onNavigateRight = { moveScoreFocus(seat + 1) },
                                    )
                                } else {
                                    Text(text = value.ifBlank { "-" }, style = bigNumberStyle, maxLines = 1)
                                }
                            }
                            Box(Modifier.width(ScoreCheckSlotWidth), contentAlignment = Alignment.Center) {
                                ScoreCheckMark(checks[seat])
                            }
                        }
                    }
                }
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Table points")
                points.forEachIndexed { seat, value ->
                    // A substitute who left during the session gets 0 table points by the EMA rules.
                    val isSubstituteSeat = editor.forcedZeroPlayerId.isNotBlank() &&
                        seatIds[seat].trim() == editor.forcedZeroPlayerId.trim()
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        Text(
                            text = value.ifBlank { "-" } + if (isSubstituteSeat) " (substitute)" else "",
                            style = bigNumberStyle,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val ScoreCheckSlotWidth = 44.dp

/** What the check next to one total score shows. */
private sealed interface ScoreCheck {
    /** The scores add up as expected. */
    data class Valid(val hint: String) : ScoreCheck

    /** The seat has a penalty and the sum of the scores matches the penalties. */
    data class Penalty(val amount: Long, val hint: String) : ScoreCheck

    /** The scores do not add up as expected. [accepted] is true for manual scores, which any sum may have. */
    data class Mismatch(val hint: String, val accepted: Boolean) : ScoreCheck

    data object None : ScoreCheck
}

private fun signed(value: Long) = if (value > 0) "+$value" else "$value"

/** The check of each seat. Nothing shows before the four scores are known. */
private fun scoreChecks(
    editor: TableManagerEditorState,
    seatIds: List<String>,
    playerNamesById: Map<Int, String>,
): List<ScoreCheck> {
    val none = List(4) { ScoreCheck.None }
    val sum = editor.scoreSum ?: return none
    if (editor.useTotalsOnly) {
        // Manual totals already include the penalties, so any sum is accepted.
        return if (sum == 0L) {
            List(4) { ScoreCheck.Valid("The four scores add up to 0.") }
        } else {
            List(4) {
                ScoreCheck.Mismatch(
                    "The four scores add up to ${signed(sum)} rather than 0. The manual scores are accepted as they are.",
                    accepted = true,
                )
            }
        }
    }
    val penalties = editor.seatPenaltyTotals
    val expected = penalties.sum()
    if (sum != expected) {
        val target = if (expected == 0L) "0" else "${signed(expected)}, the sum of the penalties"
        return List(4) {
            ScoreCheck.Mismatch(
                "The four scores add up to ${signed(sum)} rather than $target.",
                accepted = false,
            )
        }
    }
    if (expected == 0L) return List(4) { ScoreCheck.Valid("The four scores add up to 0.") }
    fun nameOf(seat: Int) = playerNameOrNull(seatIds[seat], playerNamesById) ?: SeatLabels[seat]
    val penalized = (0..3).filter { penalties[it] != 0L }
    val who = penalized.joinToString(", ", transform = { "${nameOf(it)} has a penalty of ${signed(penalties[it])}" })
    return List(4) { seat ->
        if (penalties[seat] != 0L) {
            ScoreCheck.Penalty(
                penalties[seat],
                "The scores add up to ${signed(sum)} rather than 0 but this matches the penalties,"
                        + "${nameOf(seat)} has a penalty of ${signed(penalties[seat])}. ",
            )
        } else {
            ScoreCheck.Valid("The scores add up to ${signed(sum)} rather than but this matches the penalties. $who.")
        }
    }
}

/** A green tick, an orange penalty amount or a warning, each with a tooltip. */
@Composable
private fun ScoreCheckMark(check: ScoreCheck) {
    when (check) {
        ScoreCheck.None -> Unit
        is ScoreCheck.Valid -> HintTooltip(check.hint) {
            Icon(
                Icons.Default.Check,
                contentDescription = check.hint,
                tint = ScoreOkColor,
                modifier = Modifier.size(22.dp),
            )
        }
        is ScoreCheck.Penalty -> HintTooltip(check.hint) {
            Text(
                signed(check.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ScoreWarningColor,
                maxLines = 1,
            )
        }
        is ScoreCheck.Mismatch -> HintTooltip(check.hint) {
            Icon(
                Icons.Default.Warning,
                contentDescription = check.hint,
                tint = if (check.accepted) ScoreWarningColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val ScoreOkColor = Color(0xFF2E9E4F)
private val ScoreWarningColor = Color(0xFFE08A00)
