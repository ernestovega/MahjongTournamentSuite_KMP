package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.delete_selected_count
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_failed
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_partial_title
import mahjongtournamentsuite.adminapp.generated.resources.password_resets_sent
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_disabled
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_email_failed
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_no_email
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The bar above the list of accounts. Admins select accounts here, send them the password reset email or delete them. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PasswordResetBar(
    shownCount: Int,
    selectedCount: Int,
    busy: Boolean,
    onSelectAllShown: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    /** The selected accounts that a delete would remove: the own account is never one of them. */
    deleteCount: Int,
    onDelete: () -> Unit,
    /** False on a phone, where the send button floats at the bottom of the screen. */
    showSend: Boolean = true,
) {
    // The items wrap on a narrow screen. On a wide one the send button stays at the end of the line.
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
        if (showSend) Spacer(Modifier.weight(1f))
        // On a phone the delete stays here, and the send button floats at the bottom of the screen.
        TextButton(
            enabled = deleteCount > 0 && !busy,
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(Res.string.delete_selected_count, deleteCount), Modifier.padding(start = 8.dp))
        }
        if (showSend) {
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
    TrackedDialog("PasswordResetsConfirm") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!busy) onDismiss() },
            title = {
                Text(stringResource(Res.string.password_resets_confirm_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    stringResource(Res.string.password_resets_confirm_text, count),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            // Everything goes centered, with Cancel under Send, in every window size.
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(enabled = !busy, onClick = onConfirm) { Text(stringResource(Res.string.send_password_reset_count, count)) }
                    TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
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
    TrackedDialog("PasswordResetsDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (failed.isEmpty()) Res.string.dialog_done_title else Res.string.password_resets_partial_title)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sent > 0) Text(pluralStringResource(Res.plurals.password_resets_sent, sent, sent))
                    if (failed.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pluralStringResource(Res.plurals.password_resets_failed, failed.size, failed.size), color = MaterialTheme.colorScheme.error)
                            PlayerLinesCard(
                                failed.map { result ->
                                    PlayerLine(
                                        users.firstOrNull { it.uid == result.uid }?.email ?: result.uid,
                                        listOf(stringResource(result.reason.textRes())),
                                    )
                                },
                                detailColor = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
        )
    }
}
