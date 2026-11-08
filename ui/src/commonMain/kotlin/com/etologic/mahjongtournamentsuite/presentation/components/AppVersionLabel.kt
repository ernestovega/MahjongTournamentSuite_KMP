package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.app_version_label
import org.jetbrains.compose.resources.stringResource

/**
 * The version of the running product: the web, the admin app or the player app.
 * Each entry point provides its own version from `AppVersion`. Empty hides [AppVersionLabel].
 */
val LocalAppVersion = staticCompositionLocalOf { "" }

@Composable
fun AppVersionLabel(
    modifier: Modifier = Modifier,
) {
    val version = LocalAppVersion.current
    if (version.isEmpty()) return
    Text(
        text = stringResource(Res.string.app_version_label, version),
        modifier = modifier.padding(8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
    )
}
