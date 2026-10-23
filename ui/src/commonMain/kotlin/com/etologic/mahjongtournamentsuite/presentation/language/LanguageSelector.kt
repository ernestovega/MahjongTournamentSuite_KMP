package com.etologic.mahjongtournamentsuite.presentation.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.language_label
import mahjongtournamentsuite.ui.generated.resources.language_system
import org.jetbrains.compose.resources.stringResource

/** The choice of the language of the app: the language of the device, or one of the languages of the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val controller = LocalLanguageController.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.language_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                FilterChip(
                    selected = controller.language == option,
                    onClick = { controller.onChange(option) },
                    label = { Text(option.endonym ?: stringResource(Res.string.language_system)) },
                )
            }
        }
    }
}
