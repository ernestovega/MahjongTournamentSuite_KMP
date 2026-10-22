package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Puts the cells in a grid. All cells have the same width, and the cells of a row have the same height.
 * The grid uses one row when all cells fit with [minCellWidth]. If not, it adds rows with the same columns.
 * The cells never grow wider than [maxCellWidth]. The grid sits in the middle of the available width.
 */
@Composable
internal fun StatGrid(
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 140.dp,
    maxCellWidth: Dp = 240.dp,
    gap: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val count = measurables.size
        if (count == 0) return@Layout layout(0, 0) {}
        val gapPx = gap.roundToPx()
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else count * maxCellWidth.roundToPx() + (count - 1) * gapPx
        val columns = ((available + gapPx) / (minCellWidth.roundToPx() + gapPx)).coerceIn(1, count)
        val cellWidth = min((available - gapPx * (columns - 1)) / columns, maxCellWidth.roundToPx())
        val rows = measurables.chunked(columns)
        val rowHeights = rows.map { row -> row.maxOf { it.maxIntrinsicHeight(cellWidth) } }
        val placeables = rows.mapIndexed { index, row ->
            row.map { it.measure(Constraints.fixed(cellWidth, rowHeights[index])) }
        }
        val gridWidth = columns * cellWidth + (columns - 1) * gapPx
        val width = if (constraints.hasBoundedWidth) max(constraints.minWidth, available) else gridWidth
        val height = rowHeights.sum() + (rows.size - 1) * gapPx
        layout(width, height) {
            val left = (width - gridWidth) / 2
            var y = 0
            placeables.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(left + column * (cellWidth + gapPx), y) }
                y += rowHeights[index] + gapPx
            }
        }
    }
}
