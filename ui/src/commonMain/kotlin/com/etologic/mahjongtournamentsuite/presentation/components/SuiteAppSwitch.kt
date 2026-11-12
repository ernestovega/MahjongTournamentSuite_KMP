package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.canRememberSuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.currentAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.app_chooser_admin
import mahjongtournamentsuite.ui.generated.resources.app_chooser_admin_text
import mahjongtournamentsuite.ui.generated.resources.app_chooser_player
import mahjongtournamentsuite.ui.generated.resources.app_chooser_player_text
import mahjongtournamentsuite.ui.generated.resources.app_chooser_remember
import mahjongtournamentsuite.ui.generated.resources.app_chooser_title
import mahjongtournamentsuite.ui.generated.resources.app_switch_to_admin
import mahjongtournamentsuite.ui.generated.resources.app_switch_to_player
import org.jetbrains.compose.resources.stringResource

/** The icon of each application, the same on the switch button and in the chooser. */
private val SuiteApp.icon: ImageVector
    get() = when (this) {
        SuiteApp.PLAYER -> Icons.Default.Smartphone
        SuiteApp.ADMIN -> Icons.Default.AdminPanelSettings
    }

/**
 * The top bar button that opens the other application of the suite, [target]. It shows nothing where the platform
 * cannot open it. The caller shows it only to admins and editors.
 */
@Composable
fun SuiteAppSwitchButton(target: SuiteApp) {
    val switcher = currentAppSwitcher() ?: return
    val label = when (target) {
        SuiteApp.PLAYER -> stringResource(Res.string.app_switch_to_player)
        SuiteApp.ADMIN -> stringResource(Res.string.app_switch_to_admin)
    }
    AppTopBarButton(text = label, icon = target.icon, onClick = { switcher(target) })
}

/**
 * The step after the sign-in of an admin or an editor: the player app or the admin app. [onChoose] gets the choice and
 * whether to remember it on this device. Closing the dialog keeps the player app, without remembering it.
 */
@Composable
fun SuiteAppChooserDialog(onChoose: (app: SuiteApp, remember: Boolean) -> Unit) {
    var rememberChoice by remember { mutableStateOf(false) }
    TrackedDialog("AppChooser") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { onChoose(SuiteApp.PLAYER, false) },
            title = { Text(stringResource(Res.string.app_chooser_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChooserCard(
                        app = SuiteApp.PLAYER,
                        title = stringResource(Res.string.app_chooser_player),
                        text = stringResource(Res.string.app_chooser_player_text),
                        onClick = { onChoose(SuiteApp.PLAYER, rememberChoice) },
                    )
                    ChooserCard(
                        app = SuiteApp.ADMIN,
                        title = stringResource(Res.string.app_chooser_admin),
                        text = stringResource(Res.string.app_chooser_admin_text),
                        onClick = { onChoose(SuiteApp.ADMIN, rememberChoice) },
                    )
                    if (canRememberSuiteApp()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { rememberChoice = !rememberChoice },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                            Text(stringResource(Res.string.app_chooser_remember), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun ChooserCard(app: SuiteApp, title: String, text: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(app.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
