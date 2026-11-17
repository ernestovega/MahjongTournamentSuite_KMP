package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.password_reset_confirm
import mahjongtournamentsuite.ui.generated.resources.password_reset_invalid_email
import mahjongtournamentsuite.ui.generated.resources.password_reset_message
import mahjongtournamentsuite.ui.generated.resources.password_reset_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shows the email that gets the password reset link before it is sent: the link goes to the email typed in the sign-in
 * form, which is not obvious. [onConfirm] sends it. [onDismiss] closes the dialog so the email can be corrected.
 */
@Composable
fun PasswordResetConfirmDialog(email: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackedDialog("PasswordResetConfirm") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.password_reset_title)) },
            text = { MtsDialogText(stringResource(Res.string.password_reset_message, email)) },
            confirmButton = {
                MtsDialogButtons {
                    DebouncedButton(onClick = onConfirm) { Text(stringResource(Res.string.password_reset_confirm)) }
                    AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}

private val EMAIL_FORMAT = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * The error to show when [email] cannot be an email, or null. A well formed email never gets an error: whether an
 * account exists, the answer is the same.
 */
fun passwordResetEmailError(email: String): UiText? = if (EMAIL_FORMAT.matches(email.trim())) null else UiText.of(Res.string.password_reset_invalid_email)
