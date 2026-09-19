package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    val saveFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val discardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        saveFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!isSaving) onCancel() },
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved changes. Save them before continuing?") },
        confirmButton = {
            FocusedButton(
                enabled = !isSaving,
                onClick = onSave,
                focusRequester = saveFocusRequester,
                buttonModifier = Modifier.focusLoop(
                    previous = discardFocusRequester,
                    next = cancelFocusRequester,
                ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusedTextButton(
                    enabled = !isSaving,
                    onClick = onCancel,
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = saveFocusRequester,
                        next = discardFocusRequester,
                    ),
                ) {
                    Text("Cancel")
                }
                FocusedTextButton(
                    enabled = !isSaving,
                    onClick = onDiscard,
                    focusRequester = discardFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = cancelFocusRequester,
                        next = saveFocusRequester,
                    ),
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
