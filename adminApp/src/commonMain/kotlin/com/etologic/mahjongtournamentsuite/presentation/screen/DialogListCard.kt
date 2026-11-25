package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

/** One line in a [PlayerLinesCard]: a name (a player or an account) and the lines under it. */
internal class PlayerLine(val name: String, val details: List<String>)

/**
 * A soft card with a scrollable list of players or accounts, so the list reads apart from the text around it. The
 * delete and email result dialogs use it.
 */
@Composable
internal fun PlayerLinesCard(
    lines: List<PlayerLine>,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxHeight: Dp = 200.dp,
) {
    // The dialog is already surfaceContainerHigh: the lowest container and a thin border set the card apart in both themes.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).scrollIndicators(listState, scrollbar = MtsScrollbar.Overlay),
            state = listState,
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

/**
 * A part of a result dialog: an icon, a short title and the count on one line, and the players under it in a
 * [PlayerLinesCard]. [color] tints the icon, the title and the count.
 */
@Composable
internal fun PlayerLinesSection(
    icon: ImageVector,
    title: String,
    lines: List<PlayerLine>,
    color: Color = MtsTheme.colors.link,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxHeight: Dp = 200.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = color)
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
                Text(
                    lines.size.toString(),
                    Modifier.padding(horizontal = MtsTheme.spacing.md, vertical = MtsTheme.spacing.xxs),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
        }
        PlayerLinesCard(lines, detailColor = detailColor, maxHeight = maxHeight)
    }
}
