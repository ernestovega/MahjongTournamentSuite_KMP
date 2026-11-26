package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsCheckboxRow
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_close_selected
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_col_hands
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_col_round
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_col_table
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_go
import mahjongtournamentsuite.adminapp.generated.resources.round_control_finished_title
import org.jetbrains.compose.resources.stringResource

/**
 * The review of the open tables of the rounds that are over, from the round control. One row per table: its round,
 * its number, the hands entered and a button that goes to its round. The checkboxes and "Close selected" close some
 * tables and keep the dialog open, so the admin sees the ones left and can go to them. The header checkbox checks
 * them all. The header row stays in place while the rows scroll. At the end, Close and Close selected.
 */
@Composable
internal fun OpenTablesReviewDialog(
    /** The open tables of the finished rounds, kept current: a closed table leaves the list. */
    tables: List<TableKey>,
    /** True while tables close: the dialog waits and takes no new action. */
    busy: Boolean,
    loadRoundHands: suspend (roundId: Int) -> AppResult<List<Pair<TableState, List<TableHand>>>>,
    onGoTo: (TableKey) -> Unit,
    onCloseSelected: (List<TableKey>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<TableKey>()) }
    // A closed table leaves the list, and leaves the selection too.
    val selected = picked.intersect(tables.toSet())
    // Hands entered by table. Null while they load; a table missing from the map could not be read.
    val rounds = tables.map { it.roundId }.toSet()
    val handsByTable by produceState<Map<TableKey, Int>?>(null, rounds) {
        val counts = mutableMapOf<TableKey, Int>()
        rounds.sorted().forEach { roundId ->
            (loadRoundHands(roundId) as? AppResult.Success)?.value?.forEach { (table, hands) ->
                counts[TableKey(table.roundId, table.tableId)] = hands.count { it.isDone }
            }
        }
        value = counts
    }
    TrackedDialog("RoundControlOpenTables") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.round_control_finished_title)) },
            text = {
                Column {
                    TableRow(
                        checked = tables.isNotEmpty() && selected.size == tables.size,
                        enabled = !busy,
                        onCheckedChange = { all -> picked = if (all) tables.toSet() else emptySet() },
                        checkTag = "open_tables_select_all",
                        round = stringResource(Res.string.round_control_finished_col_round),
                        table = stringResource(Res.string.round_control_finished_col_table),
                        hands = stringResource(Res.string.round_control_finished_col_hands),
                        header = true,
                    ) {
                        // An invisible copy of the button, so the header columns line up with the rows.
                        Box(Modifier.alpha(0f).clearAndSetSemantics {}) {
                            FocusedOutlinedButton(onClick = {}, enabled = false) {
                                ButtonText(stringResource(Res.string.round_control_finished_go), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    HorizontalDivider()
                    ScrollableColumnWithScrollbar(state = rememberScrollState(), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), inDialog = true) {
                        Column {
                            tables.forEachIndexed { index, table ->
                                if (index > 0) HorizontalDivider()
                                TableRow(
                                    checked = table in selected,
                                    enabled = !busy,
                                    onCheckedChange = { on -> picked = if (on) selected + table else selected - table },
                                    checkTag = "open_table_${table.roundId}_${table.tableId}_check",
                                    round = table.roundId.toString(),
                                    table = table.tableId.toString(),
                                    hands = handsByTable?.let { counts -> counts[table]?.toString() ?: "?" } ?: "…",
                                    header = false,
                                    modifier = Modifier.testTag("open_table_${table.roundId}_${table.tableId}"),
                                ) {
                                    FocusedOutlinedButton(
                                        onClick = { onGoTo(table) },
                                        enabled = !busy,
                                        buttonModifier = Modifier.testTag("open_table_${table.roundId}_${table.tableId}_go"),
                                    ) { ButtonText(stringResource(Res.string.round_control_finished_go), maxLines = 1, softWrap = false) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    // Closes the checked tables and keeps the dialog open, with a spinner while they close.
                    FocusedButton(
                        onClick = { onCloseSelected(selected.sortedWith(compareBy(TableKey::roundId, TableKey::tableId))) },
                        enabled = !busy && selected.isNotEmpty(),
                        buttonModifier = Modifier.testTag("open_tables_close_selected"),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                                modifier = Modifier.size(16.dp).testTag("open_tables_closing"),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        ButtonText(stringResource(Res.string.round_control_finished_close_selected))
                    }

                    FocusedTextButton(onClick = onDismiss, buttonModifier = Modifier.testTag("open_tables_dismiss")) {
                        ButtonText(stringResource(Res.string.cancel))
                    }
                }
            },
        )
    }
}

/** One row of the review: a checkbox, three short columns and the button of the last one. */
@Composable
private fun TableRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkTag: String,
    round: String,
    table: String,
    hands: String,
    header: Boolean,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A tap on the round, the table or the hands checks the line too: only the button does something else.
        MtsCheckboxRow(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            gap = 4.dp,
            modifier = Modifier.weight(1f).testTag(checkTag),
        ) {
            Text(round, style = style, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(table, style = style, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(hands, style = style, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        // The button keeps its natural width, so its label never breaks.
        Box(contentAlignment = Alignment.Center) { action() }
    }
}
