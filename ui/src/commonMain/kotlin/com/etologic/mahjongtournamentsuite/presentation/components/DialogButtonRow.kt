package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The buttons of a dialog with one of them apart: [start] on the left and [end] on the right of the same line. When
 * both do not fit (a small phone, a long language), [start] goes on a line of its own above [end], so no button is
 * squeezed to a column of letters.
 */
@Composable
fun DialogButtonRow(
    start: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    end: @Composable RowScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) { start() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) { end() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaceable = measurables[0].measure(loose)
        val endPlaceable = measurables[1].measure(loose)
        val gap = spacing.roundToPx()
        val oneLine = startPlaceable.width + gap + endPlaceable.width <= constraints.maxWidth
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else if (oneLine) {
            startPlaceable.width + gap + endPlaceable.width
        } else {
            maxOf(startPlaceable.width, endPlaceable.width)
        }
        if (oneLine) {
            val height = maxOf(startPlaceable.height, endPlaceable.height)
            layout(width, height) {
                startPlaceable.place(0, (height - startPlaceable.height) / 2)
                endPlaceable.place(width - endPlaceable.width, (height - endPlaceable.height) / 2)
            }
        } else {
            layout(width, startPlaceable.height + endPlaceable.height) {
                startPlaceable.place(0, 0)
                endPlaceable.place(width - endPlaceable.width, startPlaceable.height)
            }
        }
    }
}
