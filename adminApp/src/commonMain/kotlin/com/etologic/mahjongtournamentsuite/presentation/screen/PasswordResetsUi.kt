package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_summary
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_disabled
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_email_failed
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_line
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_no_email
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The bar above the list of accounts. Admins select accounts here and send them the password reset email. */
@Composable
internal fun PasswordResetBar(
    shownCount: Int,
    selectedCount: Int,
    busy: Boolean,
    onSelectAllShown: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = shownCount > 0 && !busy, onClick = onSelectAllShown) {
            Text(stringResource(Res.string.select_all_shown, shownCount))
        }
        TextButton(enabled = selectedCount > 0 && !busy, onClick = onClear) { Text(stringResource(Res.string.clear)) }
        Text(stringResource(Res.string.selected_count, selectedCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Button(enabled = selectedCount > 0 && !busy, onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(Res.string.send_password_reset_count, selectedCount), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Asks before the app sends many emails. */
@Composable
internal fun PasswordResetsConfirmDialog(count: Int, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
        title = { Text(stringResource(Res.string.password_resets_confirm_title)) },
        text = { Text(stringResource(Res.string.password_resets_confirm_text, count)) },
        confirmButton = { Button(enabled = !busy, onClick = onConfirm) { Text(stringResource(Res.string.send_password_reset_count, count)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private fun PasswordResetFailure?.textRes(): StringResource = when (this) {
    PasswordResetFailure.NOT_FOUND -> Res.string.reset_failure_not_found
    PasswordResetFailure.NO_EMAIL -> Res.string.reset_failure_no_email
    PasswordResetFailure.DISABLED -> Res.string.reset_failure_disabled
    PasswordResetFailure.EMAIL_FAILED -> Res.string.reset_failure_email_failed
    PasswordResetFailure.UNKNOWN, null -> Res.string.reset_failure_unknown
}

/** The result of a mass reset: how many emails went out, and one line for each account that failed. */
@Composable
internal fun PasswordResetsResultDialog(results: List<PasswordResetResult>, users: List<ManagedUser>, onDismiss: () -> Unit) {
    val sent = results.count { it.outcome == PasswordResetOutcome.SENT }
    val failed = results.filter { it.outcome == PasswordResetOutcome.FAILED }
    val color = if (failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        icon = { Icon(if (failed.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = color) },
        title = { Text(stringResource(Res.string.dialog_done_title), color = color) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.password_resets_summary, sent, failed.size))
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
