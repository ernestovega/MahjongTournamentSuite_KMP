package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.theme_title
import org.jetbrains.compose.resources.stringResource

/** The choice of the theme: a title and one button for each theme. The user can select only one. */
@Composable
fun ThemeSelector(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    val options = ThemePreference.entries
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.theme_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = controller.preference == option,
                    onClick = { controller.onSelectPreference(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                    label = { Icon(option.icon, contentDescription = option.label, Modifier.size(20.dp)) },
                )
            }
        }
    }
}
