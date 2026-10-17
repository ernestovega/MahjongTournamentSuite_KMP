package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Places the children in columns of equal width. The number of columns grows with the available width.
 * Each child goes into the shortest column, like a staggered grid. The parent must give a bounded width.
 */
@Composable
fun StaggeredColumns(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 420.dp,
    spacing: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val total = constraints.maxWidth
        val columns = max(1, (total + gap) / (minColumnWidth.roundToPx() + gap)).coerceAtMost(max(1, measurables.size))
        val columnWidth = (total - gap * (columns - 1)) / columns
        val heights = IntArray(columns)
        val placeables = measurables.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth)) }
        val columnOf = placeables.map { placeable ->
            val column = heights.indices.minBy { heights[it] }
            heights[column] += placeable.height + gap
            column
        }
        val tops = IntArray(columns)
        val height = (heights.max() - gap).coerceAtLeast(0)
        layout(total, height) {
            placeables.forEachIndexed { index, placeable ->
                val column = columnOf[index]
                placeable.placeRelative(column * (columnWidth + gap), tops[column])
                tops[column] += placeable.height + gap
            }
        }
    }
}
