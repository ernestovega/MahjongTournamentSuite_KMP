package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
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
                }
            }
            GridRow(height = GridPlayerRowHeight) {
                GridLabelCell("Table points")
                points.forEachIndexed { seat, value ->
                    GridCell(modifier = Modifier.weight(1f), last = seat == 3) {
                        Text(text = value.ifBlank { "-" }, style = bigNumberStyle, maxLines = 1)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreSumCheck(editor)
        }
    }
}

/** Shows whether the four manual scores add up to zero. It shows nothing before all four scores are valid. */
@Composable
private fun ScoreSumCheck(editor: TableManagerEditorState) {
    val sum = editor.scoreSum ?: return
    if (editor.useTotalsOnly) {
        // Manual totals already include the penalties, so any sum is valid. The sum is only shown.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sum == 0L) "Sum 0 ✓" else "Sum ${if (sum > 0) "+$sum" else "$sum"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sum == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val expected = editor.expectedScoreSum
    fun signed(value: Long) = if (value > 0) "+$value" else "$value"
    Row(verticalAlignment = Alignment.CenterVertically) {
        val penalties = if (expected == 0L) "" else " · Penalties SUM ${signed(expected)}"
        if (sum == expected) {
            Text(
                "Sum ${if (sum == 0L) "0" else signed(sum)}$penalties ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                if (expected == 0L) {
                    "Sum is ${signed(sum)}. The scores must add up to 0."
                } else {
                    "Sum is ${signed(sum)}$penalties. The scores must add up to the penalties sum."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
