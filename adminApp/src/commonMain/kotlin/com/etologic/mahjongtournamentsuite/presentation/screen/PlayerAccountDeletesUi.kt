package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.delete_accounts_count
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.player_account_delete_failure_last_admin
import mahjongtournamentsuite.adminapp.generated.resources.player_account_delete_failure_no_account
import mahjongtournamentsuite.adminapp.generated.resources.player_account_delete_failure_self
import mahjongtournamentsuite.adminapp.generated.resources.player_account_delete_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.player_accounts_delete_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.player_accounts_delete_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.player_accounts_delete_summary
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_line
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The bar above the player base list. Admins select players here and delete their accounts. The EMA players stay. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PlayerSelectionBar(
    shownCount: Int,
    selectedCount: Int,
    busy: Boolean,
    onSelectAllShown: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(enabled = shownCount > 0 && !busy, onClick = onSelectAllShown) {
            Text(stringResource(Res.string.select_all_shown, shownCount))
        }
        TextButton(enabled = selectedCount > 0 && !busy, onClick = onClear) { Text(stringResource(Res.string.clear)) }
        Text(stringResource(Res.string.selected_count, selectedCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            enabled = selectedCount > 0 && !busy,
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(Res.string.delete_accounts_count, selectedCount), Modifier.padding(start = 8.dp))
        }
    }
}

/** Asks before the app deletes the accounts of many EMA players. Cancel has the focus. */
@Composable
internal fun PlayerAccountDeletesConfirmDialog(count: Int, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeletePlayersConfirm") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!busy) onDismiss() },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.player_accounts_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.player_accounts_delete_confirm_text, count)) },
            confirmButton = {
                FocusedButton(
                    onClick = onConfirm,
                    enabled = !busy,
                    focusRequester = deleteFocus,
                    buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(Res.string.delete_accounts_count, count)) }
            },
            dismissButton = {
                FocusedTextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    focusRequester = cancelFocus,
                    buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

private fun PlayerAccountDeleteFailure?.textRes(): StringResource = when (this) {
    PlayerAccountDeleteFailure.NO_ACCOUNT -> Res.string.player_account_delete_failure_no_account
    PlayerAccountDeleteFailure.SELF -> Res.string.player_account_delete_failure_self
    PlayerAccountDeleteFailure.LAST_ADMIN -> Res.string.player_account_delete_failure_last_admin
    PlayerAccountDeleteFailure.UNKNOWN, null -> Res.string.player_account_delete_failure_unknown
}

/** The result of a bulk delete: how many accounts are gone, and one line for each player whose account stays. */
@Composable
internal fun PlayerAccountDeletesResultDialog(results: List<PlayerAccountDeleteResult>, players: List<Player>, onDismiss: () -> Unit) {
    val deleted = results.count { it.outcome == PlayerAccountDeleteOutcome.DELETED }
    val failed = results.filter { it.outcome == PlayerAccountDeleteOutcome.FAILED }
    val color = if (failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    TrackedDialog("DeletePlayersDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            icon = { Icon(if (failed.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = color) },
            title = { Text(stringResource(Res.string.dialog_done_title), color = color) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.player_accounts_delete_summary, deleted, failed.size))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(failed, key = { it.emaId }) { result ->
                            val name = players.firstOrNull { it.emaId == result.emaId }?.let { "${it.displayName} (${it.emaId})" } ?: result.emaId
                            Text(
                                stringResource(Res.string.reset_failure_line, name, stringResource(result.reason.textRes())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
        )
    }
}
