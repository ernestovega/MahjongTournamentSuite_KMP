package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.sign_out_confirm
import mahjongtournamentsuite.ui.generated.resources.sign_out_message
import mahjongtournamentsuite.ui.generated.resources.sign_out_title
import org.jetbrains.compose.resources.stringResource

/** Asks the user to confirm the sign out. [onConfirm] signs out. [onDismiss] closes the dialog. */
@Composable
fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sign_out_title)) },
        text = { Text(stringResource(Res.string.sign_out_message)) },
        confirmButton = { AppTextButton(onClick = onConfirm) { Text(stringResource(Res.string.sign_out_confirm)) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
