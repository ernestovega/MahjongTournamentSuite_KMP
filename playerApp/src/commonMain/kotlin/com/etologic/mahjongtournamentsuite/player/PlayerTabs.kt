package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The color of every tab row. It is the color of the top bar. */
@Composable
internal fun tabRowColor(): Color = MaterialTheme.colorScheme.primaryContainer

/** The color of the text, the icons and the indicator of a tab row. It is the color of the title of the top bar. */
internal val TabContentColor: Color = Color.White

/**
 * A low tab with the icon over the label. The height is fixed, so all tab rows have the same height.
 * A tab without an [icon] shows only the label.
 */
@Composable
internal fun CompactTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(if (icon != null) 52.dp else 40.dp),
        selectedContentColor = TabContentColor,
        unselectedContentColor = TabContentColor.copy(alpha = 0.7f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            icon?.invoke()
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
