package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import com.etologic.mahjongtournamentsuite.presentation.components.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
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
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_partial_title
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.reset_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.result_section_deleted
import mahjongtournamentsuite.adminapp.generated.resources.result_section_not_deleted
import mahjongtournamentsuite.adminapp.generated.resources.users_delete_confirm_text
import mahjongtournamentsuite.adminapp.generated.resources.users_delete_confirm_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
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
            title = { MtsDialogTitle(stringResource(Res.string.users_delete_confirm_title)) },
            text = { MtsDialogText(pluralStringResource(Res.plurals.users_delete_confirm_text, count, count)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !busy,
                        focusRequester = deleteFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                        colors = MtsButtons.destructiveColors(),
                    ) { ButtonText(stringResource(Res.string.delete_selected_count, count)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !busy,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                    ) { ButtonText(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}

internal fun UserDeleteFailure?.textRes(): StringResource = when (this) {
    UserDeleteFailure.NOT_FOUND -> Res.string.reset_failure_not_found
    UserDeleteFailure.SELF -> Res.string.delete_failure_self
    UserDeleteFailure.LAST_SUPER_ADMIN -> Res.string.delete_failure_last_admin
    UserDeleteFailure.UNKNOWN, null -> Res.string.delete_failure_unknown
}

/** The result of a bulk delete: how many accounts are gone, and one line for each account that stays. */
@Composable
internal fun UserDeletesResultDialog(results: List<UserDeleteResult>, users: List<ManagedUser>, onDismiss: () -> Unit) {
    val deleted = results.filter { it.outcome == UserDeleteOutcome.DELETED }
    val emailOf = { uid: String -> users.firstOrNull { it.uid == uid }?.email ?: uid }
    val failed = results.filter { it.outcome == UserDeleteOutcome.FAILED }
    TrackedDialog("DeleteAccountsDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(if (failed.isEmpty()) Res.string.dialog_done_title else Res.string.ema_players_delete_partial_title)) },
            text = {
                // One section for each outcome that happened: its title, its count and its accounts. One that stays shows why.
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (deleted.isNotEmpty()) {
                        PlayerLinesSection(
                            Icons.Filled.Delete,
                            stringResource(Res.string.result_section_deleted),
                            deleted.map { result -> PlayerLine(emailOf(result.uid), emptyList()) },
                            maxHeight = 120.dp,
                        )
                    }
                    if (failed.isNotEmpty()) {
                        PlayerLinesSection(
                            Icons.Filled.ErrorOutline,
                            stringResource(Res.string.result_section_not_deleted),
                            failed.map { result -> PlayerLine(emailOf(result.uid), listOf(stringResource(result.reason.textRes()))) },
                            color = MaterialTheme.colorScheme.error,
                            detailColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                MtsDialogButtons {
                    Button(onClick = onDismiss) { ButtonText(stringResource(Res.string.ok)) }
                }
            },
        )
    }
}
