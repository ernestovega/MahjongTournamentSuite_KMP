package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_last_admin
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_self
import mahjongtournamentsuite.adminapp.generated.resources.delete_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.delete_selected_count
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_line
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.users_delete_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.users_delete_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.users_delete_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button

/** Asks before the app deletes many accounts for good. Cancel has the focus, like the single delete. */
@Composable
internal fun UserDeletesConfirmDialog(count: Int, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeleteAccountsConfirm") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!busy) onDismiss() },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.users_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.users_delete_confirm_text, count)) },
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
                ) { Text(stringResource(Res.string.delete_selected_count, count)) }
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

private fun UserDeleteFailure?.textRes(): StringResource = when (this) {
    UserDeleteFailure.NOT_FOUND -> Res.string.reset_failure_not_found
    UserDeleteFailure.SELF -> Res.string.delete_failure_self
    UserDeleteFailure.LAST_ADMIN -> Res.string.delete_failure_last_admin
    UserDeleteFailure.UNKNOWN, null -> Res.string.delete_failure_unknown
}

/** The result of a bulk delete: how many accounts are gone, and one line for each account that stays. */
@Composable
internal fun UserDeletesResultDialog(results: List<UserDeleteResult>, users: List<ManagedUser>, onDismiss: () -> Unit) {
    val deleted = results.count { it.outcome == UserDeleteOutcome.DELETED }
    val failed = results.filter { it.outcome == UserDeleteOutcome.FAILED }
    val color = if (failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    TrackedDialog("DeleteAccountsDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            icon = { Icon(if (failed.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = color) },
            title = { Text(stringResource(Res.string.dialog_done_title), color = color) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.users_delete_summary, deleted, failed.size))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(failed, key = { it.uid }) { result ->
                            val name = users.firstOrNull { it.uid == result.uid }?.email ?: result.uid
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
