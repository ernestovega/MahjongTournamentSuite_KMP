package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun UnsavedChangesDialog(
    isSaving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved changes. Save them before continuing?") },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = onSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isSaving,
                    onClick = onCancel,
                ) {
                    Text("Cancel")
                }
                TextButton(
                    enabled = !isSaving,
                    onClick = onDiscard,
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun UnsavedChangesDialogPreview() {
    MtsTheme(useDarkTheme = false) {
        UnsavedChangesDialog(
            isSaving = false,
            onSave = {},
            onDiscard = {},
            onCancel = {},
        )
    }
}
