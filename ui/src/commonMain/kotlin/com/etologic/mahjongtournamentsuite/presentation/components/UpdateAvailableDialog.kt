package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.update_available_download
import mahjongtournamentsuite.ui.generated.resources.update_available_later
import mahjongtournamentsuite.ui.generated.resources.update_available_message
import mahjongtournamentsuite.ui.generated.resources.update_available_title
import org.jetbrains.compose.resources.stringResource

/**
 * Tells the user of the desktop app that [version] is out. [onDownload] opens its download page, [onDismiss] closes
 * the dialog until the next start. See docs/desktop-release.md.
 */
@Composable
fun UpdateAvailableDialog(version: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    TrackedDialog("UpdateAvailable") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MtsDialogTitle(stringResource(Res.string.update_available_title)) },
            text = { MtsDialogText(stringResource(Res.string.update_available_message, version)) },
            confirmButton = {
                MtsDialogButtons {
                    DebouncedButton(onClick = onDownload) { ButtonText(stringResource(Res.string.update_available_download)) }
                    AppTextButton(onClick = onDismiss) { ButtonText(stringResource(Res.string.update_available_later)) }
                }
            },
        )
    }
}
