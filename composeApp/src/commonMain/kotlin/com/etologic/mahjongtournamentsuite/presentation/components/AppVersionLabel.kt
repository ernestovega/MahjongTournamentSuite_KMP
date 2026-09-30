package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.etologic.mahjongtournamentsuite.domain.AppVersion

@Composable
fun AppVersionLabel(
    backendVersion: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "App ${AppVersion.name} · Backend ${backendVersion ?: "…"}",
        modifier = modifier.padding(8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
    )
}
