package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_block
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_block_note
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_description
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_left
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_pattern
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_pattern_check
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_pattern_note
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_start
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_swaps
import mahjongtournamentsuite.ui.generated.resources.draw_blocks_flow_swaps_note
import mahjongtournamentsuite.ui.generated.resources.draw_flow_done
import mahjongtournamentsuite.ui.generated.resources.draw_flow_no
import mahjongtournamentsuite.ui.generated.resources.draw_flow_yes
import org.jetbrains.compose.resources.stringResource

/*
 * The flowchart of the draw in blocks (DrawRoundsInBlocksUseCase), in the same UML activity notation as
 * DrawFlowDiagram. The main path goes down the left column: start, merge, next block, "perfect pattern?", pattern,
 * merge, "rounds left?", done. The "no" of the pattern goes to the right column (draw by swaps) and back into the
 * second merge; the "yes" of "rounds left?" goes back up on the right, dashed, into the first merge.
 */

// The rows of the main column, top to bottom.
private const val B_INITIAL = 0
private const val B_START = 1
private const val B_MERGE_BLOCK = 2
private const val B_BLOCK = 3
private const val B_PATTERN_CHECK = 4
private const val B_PATTERN = 5
private const val B_MERGE_DRAWN = 6
private const val B_LEFT = 7
private const val B_DONE = 8
private const val B_FINAL = 9
private const val B_MAIN_COUNT = 10

/** A drawn flowchart of the draw in blocks, for more rounds than fit without repeats. Theme and app language. */
@Composable
fun DrawBlocksFlowDiagram(modifier: Modifier = Modifier) {
    val main = listOf(
        FlowNode(NodeKind.Initial, ""),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_blocks_flow_start)),
        FlowNode(NodeKind.Merge, ""),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_blocks_flow_block), stringResource(Res.string.draw_blocks_flow_block_note)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_blocks_flow_pattern_check)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_blocks_flow_pattern), stringResource(Res.string.draw_blocks_flow_pattern_note)),
        FlowNode(NodeKind.Merge, ""),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_blocks_flow_left)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_done)),
        FlowNode(NodeKind.Final, ""),
    )
    // The side column has one node, on the row of the pattern.
    val swaps = FlowNode(NodeKind.Action, stringResource(Res.string.draw_blocks_flow_swaps), stringResource(Res.string.draw_blocks_flow_swaps_note))
    val yes = "[" + stringResource(Res.string.draw_flow_yes) + "]"
    val no = "[" + stringResource(Res.string.draw_flow_no) + "]"
    val description = stringResource(Res.string.draw_blocks_flow_description)

    val colors = MaterialTheme.colorScheme
    val lineColor = colors.onSurfaceVariant
    val loopColor = colors.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.onSurface)
    val textMeasurer = rememberTextMeasurer()

    var geometry by remember { mutableStateOf<BlocksGeometry?>(null) }

    BoxWithConstraints(modifier.widthIn(max = 520.dp)) {
        val compact = maxWidth < 400.dp
        Layout(
            content = {
                main.forEach { FlowNodeBox(it, compact) }
                FlowNodeBox(swaps, compact)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description }
                .drawBehind {
                    val g = geometry ?: return@drawBehind
                    drawBlocksArrows(g, yes, no, lineColor, loopColor, textMeasurer, labelStyle)
                },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 520.dp.roundToPx()
            val leftGutter = (if (compact) 8.dp else 16.dp).roundToPx()
            val rightGutter = (if (compact) 30.dp else 40.dp).roundToPx()
            val columnGap = (if (compact) 16.dp else 30.dp).roundToPx()
            val rowGap = 24.dp.roundToPx()
            val available = (width - leftGutter - rightGutter - columnGap).coerceAtLeast(1)
            val mainWidth = (available * 0.5f).toInt()
            val sideWidth = available - mainWidth

            fun constraintsFor(node: FlowNode, columnWidth: Int): Constraints = if (node.kind == NodeKind.Action) Constraints.fixedWidth(columnWidth) else Constraints(maxWidth = columnWidth)
            val mainPlaceables = measurables.take(B_MAIN_COUNT).mapIndexed { i, m -> m.measure(constraintsFor(main[i], mainWidth)) }
            val sidePlaceable = measurables[B_MAIN_COUNT].measure(constraintsFor(swaps, sideWidth))

            val sideX = leftGutter + mainWidth + columnGap
            val mainRects = ArrayList<Rect>(B_MAIN_COUNT)
            var side = Rect.Zero
            var y = 0
            for (row in 0 until B_MAIN_COUNT) {
                val placeable = mainPlaceables[row]
                val rowHeight = if (row == B_PATTERN) maxOf(placeable.height, sidePlaceable.height) else placeable.height
                val top = y + (rowHeight - placeable.height) / 2
                val left = leftGutter + (mainWidth - placeable.width) / 2
                mainRects += Rect(Offset(left.toFloat(), top.toFloat()), Size(placeable.width.toFloat(), placeable.height.toFloat()))
                if (row == B_PATTERN) {
                    val sideTop = y + (rowHeight - sidePlaceable.height) / 2
                    side = Rect(Offset(sideX.toFloat(), sideTop.toFloat()), Size(sidePlaceable.width.toFloat(), sidePlaceable.height.toFloat()))
                }
                y += rowHeight + rowGap
            }
            val height = y - rowGap + 2.dp.roundToPx()
            geometry = BlocksGeometry(main = mainRects, side = side, loopX = (width - rightGutter / 2).toFloat())

            layout(width, height) {
                mainPlaceables.forEachIndexed { i, p -> p.place(mainRects[i].left.toInt(), mainRects[i].top.toInt()) }
                sidePlaceable.place(side.left.toInt(), side.top.toInt())
            }
        }
    }
}

private class BlocksGeometry(
    val main: List<Rect>,
    val side: Rect,
    val loopX: Float,
)

private fun DrawScope.drawBlocksArrows(
    g: BlocksGeometry,
    yes: String,
    no: String,
    lineColor: Color,
    loopColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val stroke = 1.5.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
    val gap = 4.dp.toPx()
    fun besideLabel(text: String, at: Offset) {
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(layout, topLeft = Offset(at.x + gap, at.y - layout.size.height / 2f))
    }
    fun aboveLabel(text: String, at: Offset) {
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height - 1.dp.toPx()))
    }

    // The main path, straight down.
    for (i in 0 until B_MAIN_COUNT - 1) {
        val from = Offset(g.main[i].center.x, g.main[i].bottom)
        val to = Offset(g.main[i + 1].center.x, g.main[i + 1].top)
        arrow(listOf(from, to), lineColor, stroke)
        if (i == B_PATTERN_CHECK) besideLabel(yes, Offset(from.x, (from.y + to.y) / 2f))
        if (i == B_LEFT) besideLabel(no, Offset(from.x, (from.y + to.y) / 2f))
    }

    val check = g.main[B_PATTERN_CHECK]
    val drawn = g.main[B_MERGE_DRAWN]
    val left = g.main[B_LEFT]
    val block = g.main[B_MERGE_BLOCK]

    // No pattern: the block is drawn by swaps, and joins the main path again.
    arrow(
        listOf(Offset(check.right, check.center.y), Offset(g.side.center.x, check.center.y), Offset(g.side.center.x, g.side.top)),
        lineColor,
        stroke,
    )
    aboveLabel(no, Offset((check.right + g.side.center.x) / 2f, check.center.y))
    arrow(
        listOf(Offset(g.side.center.x, g.side.bottom), Offset(g.side.center.x, drawn.center.y), Offset(drawn.right, drawn.center.y)),
        lineColor,
        stroke,
    )

    // Rounds left: the next block, dashed, back up on the right.
    arrow(
        listOf(
            Offset(left.right, left.center.y),
            Offset(g.loopX, left.center.y),
            Offset(g.loopX, block.center.y),
            Offset(block.right, block.center.y),
        ),
        loopColor,
        stroke,
        dash,
    )
    aboveLabel(yes, Offset((left.right + g.loopX) / 2f, left.center.y))
}
