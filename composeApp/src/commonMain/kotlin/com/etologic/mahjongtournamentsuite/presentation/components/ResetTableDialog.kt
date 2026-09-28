package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

@Composable
fun ResetTableDialog(
    roundId: Int,
    tableId: Int,
    isResetting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!isResetting) onCancel() },
        title = { Text("Reset Round $roundId, Table $tableId?") },
        text = {
            Text(
                "Warning: This permanently clears all seat positions, scores, points, and hand values. " +
                    "Unsaved edits will also be lost. This action cannot be undone.",
            )
        },
        confirmButton = {
            FocusedButton(
                enabled = !isResetting,
                onClick = onConfirm,
                focusRequester = resetFocusRequester,
                buttonModifier = Modifier.focusLoop(
                    previous = cancelFocusRequester,
                    next = cancelFocusRequester,
                ),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Reset table")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusedTextButton(
                    enabled = !isResetting,
                    onClick = onCancel,
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = resetFocusRequester,
                        next = resetFocusRequester,
                    ),
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}
