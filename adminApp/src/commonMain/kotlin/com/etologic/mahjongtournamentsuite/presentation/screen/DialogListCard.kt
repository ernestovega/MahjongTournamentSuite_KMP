package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One line in a [PlayerLinesCard]: a name (a player or an account) and the lines under it. */
internal class PlayerLine(val name: String, val details: List<String>)

/**
 * A soft card with a scrollable list of players or accounts, so the list reads apart from the text around it. The
 * delete and email result dialogs use it.
 */
@Composable
internal fun PlayerLinesCard(lines: List<PlayerLine>, detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    // The dialog is already surfaceContainerHigh: the lowest container and a thin border set the card apart in both themes.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 200.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines) { line ->
                Column {
                    Text(line.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    line.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = detailColor) }
                }
            }
        }
    }
}
