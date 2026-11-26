package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.sign_out_confirm
import mahjongtournamentsuite.ui.generated.resources.sign_out_message
import mahjongtournamentsuite.ui.generated.resources.sign_out_title
import org.jetbrains.compose.resources.stringResource

/** Asks the user to confirm the sign out. [onConfirm] signs out. [onDismiss] closes the dialog. */
@Composable
fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackedDialog("SignOut") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.sign_out_title)) },
            text = { MtsDialogText(stringResource(Res.string.sign_out_message)) },
            confirmButton = {
                MtsDialogButtons {
                    DebouncedButton(onClick = onConfirm) { ButtonText(stringResource(Res.string.sign_out_confirm)) }
                    AppTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}
