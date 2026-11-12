package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.currentAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.app_chooser_hint
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
 * The step after the sign-in of an admin or an editor: the player app or the admin app. [onChoose] gets the choice.
 * Closing the dialog keeps the player app. The top bar of each app can switch to the other one later.
 */
@Composable
fun SuiteAppChooserDialog(onChoose: (app: SuiteApp) -> Unit) {
    TrackedDialog("AppChooser") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { onChoose(SuiteApp.PLAYER) },
            title = { Text(stringResource(Res.string.app_chooser_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChooserCard(SuiteApp.PLAYER, stringResource(Res.string.app_switch_to_player), Modifier.weight(1f)) { onChoose(SuiteApp.PLAYER) }
                        ChooserCard(SuiteApp.ADMIN, stringResource(Res.string.app_switch_to_admin), Modifier.weight(1f)) { onChoose(SuiteApp.ADMIN) }
                    }
                    Text(
                        text = stringResource(Res.string.app_chooser_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )
    }
}

/** A square card with the icon of the app and its name. */
@Composable
private fun ChooserCard(app: SuiteApp, title: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(app.icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
