package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

@Composable
fun ManualScoreTotalConfirmationDialog(
    total: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onCancel,
        title = { Text("Save manual scores?") },
        text = { Text("The manual scores total $total, not 0. Save them anyway?") },
        confirmButton = {
            FocusedButton(
                onClick = onConfirm,
                focusRequester = saveFocusRequester,
                buttonModifier = Modifier.focusLoop(
                    previous = cancelFocusRequester,
                    next = cancelFocusRequester,
                ),
            ) {
                Text("Save anyway")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusedTextButton(
                    onClick = onCancel,
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = saveFocusRequester,
                        next = saveFocusRequester,
                    ),
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}
