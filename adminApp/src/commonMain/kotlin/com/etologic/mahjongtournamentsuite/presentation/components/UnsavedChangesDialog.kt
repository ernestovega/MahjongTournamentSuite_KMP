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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.discard
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.unsaved_changes
import mahjongtournamentsuite.adminapp.generated.resources.you_have_unsaved_changes_save_them
import org.jetbrains.compose.resources.stringResource

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

    TrackedDialog("UnsavedChanges") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isSaving) onCancel() },
            title = { Text(stringResource(Res.string.unsaved_changes)) },
            text = { Text(stringResource(Res.string.you_have_unsaved_changes_save_them)) },
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
                    Text(stringResource(Res.string.save))
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
                        Text(stringResource(Res.string.cancel))
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
                            text = stringResource(Res.string.discard),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
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
