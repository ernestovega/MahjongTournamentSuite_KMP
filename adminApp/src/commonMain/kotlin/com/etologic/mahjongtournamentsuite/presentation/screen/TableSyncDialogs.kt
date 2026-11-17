package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.enter_these_values_again_if_they
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
import mahjongtournamentsuite.adminapp.generated.resources.table_updated
import mahjongtournamentsuite.adminapp.generated.resources.tables_updated
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The headline of one notice. The wording tells the user what happened to the typed values. */
internal fun TableUpdateNotice.headline(): UiText = when {
    saveRejected -> UiText.of(Res.string.table_headline_rejected, key.roundId, key.tableId)
    lostChanges.isNotEmpty() -> UiText.of(Res.string.table_headline_replaced, key.roundId, key.tableId)
    else -> UiText.of(Res.string.table_headline_latest, key.roundId, key.tableId)
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
            title = { Text(stringResource(if (notices.size == 1) Res.string.table_updated else Res.string.tables_updated)) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    notices.forEachIndexed { index, notice ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Text(notice.headline().asString(), fontWeight = FontWeight.Bold)
                        if (notice.lostChanges.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(Res.string.enter_these_values_again_if_they), style = MaterialTheme.typography.bodySmall)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                notice.lostChanges.forEach { change ->
                                    Text(stringResource(Res.string.lost_change_line, change.label.asString(), change.value.asString()), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FocusedButton(onClick = onDismiss, focusRequester = okFocusRequester) { Text(stringResource(Res.string.ok)) }
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
            title = {
                // A short title fits one line on a phone. The round goes under it.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.reset_table_confirm_title, tableId), textAlign = TextAlign.Center)
                    Text(
                        stringResource(Res.string.round_n, roundId),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            // Clear table first and Cancel under it, centered, in every window size.
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = onConfirm,
                        modifier = Modifier.testTag("resetTableConfirm"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        ),
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
