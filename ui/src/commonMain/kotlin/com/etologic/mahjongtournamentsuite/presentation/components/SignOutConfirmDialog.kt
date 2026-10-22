package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Asks the user to confirm the sign out. [onConfirm] signs out. [onDismiss] closes the dialog. */
@Composable
fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out") },
        text = { Text("Do you want to sign out?") },
        confirmButton = { AppTextButton(onClick = onConfirm) { Text("Yes, sign out") } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
