package com.etologic.mahjongtournamentsuite.presentation.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.theme_title
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.MtsSegmentedButton as SegmentedButton

/**
 * The choice of the theme: a small title above, and one low button for each theme with its icon and its name.
 * The user can select only one.
 */
@Composable
fun ThemeSelector(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    val options = ThemePreference.entries
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.theme_title),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(36.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = controller.preference == option,
                    onClick = { controller.onSelectPreference(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    icon = {},
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(option.icon, contentDescription = null, Modifier.size(16.dp))
                            Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                )
            }
        }
    }
}
