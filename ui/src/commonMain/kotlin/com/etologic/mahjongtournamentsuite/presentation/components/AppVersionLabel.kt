package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.app_version_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppVersionLabel(
    backendVersion: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(Res.string.app_version_label, AppVersion.name, backendVersion ?: "…"),
        modifier = modifier.padding(8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
    )
}
