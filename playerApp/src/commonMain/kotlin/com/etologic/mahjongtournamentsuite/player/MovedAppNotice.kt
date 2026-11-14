package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.moved_app_install
import mahjongtournamentsuite.playerapp.generated.resources.moved_app_later
import mahjongtournamentsuite.playerapp.generated.resources.moved_app_message
import mahjongtournamentsuite.playerapp.generated.resources.moved_app_title
import org.jetbrains.compose.resources.stringResource

/**
 * The old Android player app tells the user that the player part now lives in the one app of the suite, which also
 * gets the updates. It shows at each start until the user installs the new app. [onInstall] opens its Google Play page.
 */
@Composable
internal fun MovedAppNotice(onInstall: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(true) }
    if (!open) return
    TrackedDialog("MovedApp") {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(Res.string.moved_app_title)) },
            text = { Text(stringResource(Res.string.moved_app_message)) },
            confirmButton = {
                AppTextButton(onClick = {
                    open = false
                    onInstall()
                }) { Text(stringResource(Res.string.moved_app_install)) }
            },
            dismissButton = { AppTextButton(onClick = { open = false }) { Text(stringResource(Res.string.moved_app_later)) } },
        )
    }
}
