package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.enter_these_values_again_if_they
import mahjongtournamentsuite.adminapp.generated.resources.hands_not_done_line
import mahjongtournamentsuite.adminapp.generated.resources.hands_not_done_save
import mahjongtournamentsuite.adminapp.generated.resources.hands_not_done_text
import mahjongtournamentsuite.adminapp.generated.resources.hands_not_done_title
import mahjongtournamentsuite.adminapp.generated.resources.lost_change_line
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.reset_table
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_keeps_items
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_keeps_label
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_logged
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_removes_items
import mahjongtournamentsuite.adminapp.generated.resources.reset_table_removes_label
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_latest
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_rejected
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_replaced
import mahjongtournamentsuite.adminapp.generated.resources.table_notice_where
import mahjongtournamentsuite.adminapp.generated.resources.table_updated
import mahjongtournamentsuite.adminapp.generated.resources.tables_updated
import org.jetbrains.compose.resources.stringResource

/** Where one notice happened: its round and table. */
internal fun TableUpdateNotice.where(): UiText = UiText.of(Res.string.table_notice_where, key.roundId, key.tableId)

/** What happened in one notice, in two short lines. The wording tells the user what happened to the typed values. */
internal fun TableUpdateNotice.headline(): UiText = when {
    saveRejected -> UiText.of(Res.string.table_headline_rejected)
    lostChanges.isNotEmpty() -> UiText.of(Res.string.table_headline_replaced)
    else -> UiText.of(Res.string.table_headline_latest)
}

/** Tells the user that server data replaced one or more tables. The user must accept it. */
@Composable
internal fun TableUpdatesDialog(
    notices: List<TableUpdateNotice>,
    onDismiss: () -> Unit,
) {
    val okFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    LaunchedEffect(notices.size) { okFocusRequester.requestFocus() }

    TrackedDialog("TableUpdated") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(if (notices.size == 1) Res.string.table_updated else Res.string.tables_updated)) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    // One block for each table: where, in a small title; what happened; and the values to type again in a card.
                    notices.forEachIndexed { index, notice ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                notice.where().asString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MtsTheme.colors.link,
                                textAlign = TextAlign.Center,
                            )
                            Text(notice.headline().asString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            if (notice.lostChanges.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.enter_these_values_again_if_they),
                                    Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                LostChangesCard(notice.lostChanges.map { stringResource(Res.string.lost_change_line, it.label.asString(), it.value.asString()) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(onClick = onDismiss, focusRequester = okFocusRequester) { Text(stringResource(Res.string.ok)) }
                }
            },
        )
    }
}

/** One hand with a valid result and Done off, in the warning before a save. */
internal data class HandNotDone(val roundId: Int, val tableId: Int, val handId: Int)

/**
 * Asks before saving hands that have a valid result but Done off: they will not count. It can be on purpose, or a
 * Done that the editor forgot to mark again after a correction.
 */
@Composable
internal fun HandsNotDoneDialog(
    hands: List<HandNotDone>,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackedDialog("HandsNotDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.hands_not_done_title)) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.hands_not_done_text), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    LostChangesCard(hands.map { stringResource(Res.string.hands_not_done_line, it.roundId, it.tableId, it.handId) })
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onSave, modifier = Modifier.testTag("handsNotDoneSave")) { Text(stringResource(Res.string.hands_not_done_save)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** Asks before an admin empties a table: seats, scores and hands. The server keeps a record of who did it. */
@Composable
internal fun ResetTableDialog(
    roundId: Int,
    tableId: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackedDialog("ResetTable") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!busy) onDismiss() },
            // A short title fits one line on a phone. The round goes under it.
            title = {
                MtsDialogTitle(
                    stringResource(Res.string.reset_table_confirm_title, tableId),
                    subtitle = stringResource(Res.string.round_n, roundId),
                )
            },
            text = {
                // What goes and what stays, as two short lists. The block is centered and its lines read from the left.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResetTableGroup(stringResource(Res.string.reset_table_removes_label), stringResource(Res.string.reset_table_removes_items))
                        ResetTableGroup(stringResource(Res.string.reset_table_keeps_label), stringResource(Res.string.reset_table_keeps_items))
                    }
                    Text(
                        stringResource(Res.string.reset_table_logged),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(
                        enabled = !busy,
                        onClick = onConfirm,
                        modifier = Modifier.testTag("resetTableConfirm"),
                        colors = MtsButtons.destructiveColors(),
                    ) { Text(stringResource(Res.string.reset_table)) }
                    TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

/** A label and its bullet lines in the clear table dialog. */
@Composable
private fun ResetTableGroup(label: String, items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(items, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The values that the user typed and lost, in a soft card so they read apart from the text. */
@Composable
private fun LostChangesCard(lines: List<String>) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
