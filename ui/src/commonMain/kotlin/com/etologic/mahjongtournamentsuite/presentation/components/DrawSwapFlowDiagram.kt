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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.draw_flow_done
import mahjongtournamentsuite.ui.generated.resources.draw_flow_no
import mahjongtournamentsuite.ui.generated.resources.draw_flow_yes
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_check
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_deal
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_deal_note
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_description
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_limit
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_pick
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_shake
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_start
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_stuck
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_swap
import mahjongtournamentsuite.ui.generated.resources.draw_swap_flow_swap_note
import org.jetbrains.compose.resources.stringResource

/*
 * The flowchart of the second way to draw (GenerateScheduleBySwapsUseCase), in the same UML activity notation as
 * DrawFlowDiagram. The main path goes down the left column: start, deal, merge, "any repeat left?", done. The "yes"
 * branch goes to the right column (pick a player, swap, "long without progress?", shake), and one dashed loop goes back
 * up on the right into the merge node.
 */

// The rows of the main column, top to bottom.
private const val S_INITIAL = 0
private const val S_START = 1
private const val S_DEAL = 2
private const val S_MERGE = 3
private const val S_CHECK = 4
private const val S_DONE = 5
private const val S_FINAL = 6
private const val S_MAIN_COUNT = 7

// The side column starts at the row of S_CHECK and has 4 nodes.
private const val S_SIDE_COUNT = 4

/** A drawn flowchart of the second way to draw. It follows the theme and the app language. */
@Composable
fun DrawSwapFlowDiagram(modifier: Modifier = Modifier) {
    val main = listOf(
        FlowNode(NodeKind.Initial, ""),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_swap_flow_start)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_swap_flow_deal), stringResource(Res.string.draw_swap_flow_deal_note)),
        FlowNode(NodeKind.Merge, ""),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_swap_flow_check)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_done)),
        FlowNode(NodeKind.Final, ""),
    )
    val side = listOf(
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_swap_flow_pick)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_swap_flow_swap), stringResource(Res.string.draw_swap_flow_swap_note)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_swap_flow_stuck)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_swap_flow_shake)),
    )
    val yes = "[" + stringResource(Res.string.draw_flow_yes) + "]"
    val no = "[" + stringResource(Res.string.draw_flow_no) + "]"
    val description = stringResource(Res.string.draw_swap_flow_description)
    val limitNote = stringResource(Res.string.draw_swap_flow_limit)

    val colors = MaterialTheme.colorScheme
    val lineColor = colors.onSurfaceVariant
    val loopColor = colors.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.onSurface)
    val noteStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.End)
    val textMeasurer = rememberTextMeasurer()

    var geometry by remember { mutableStateOf<SwapGeometry?>(null) }

    BoxWithConstraints(modifier.widthIn(max = 520.dp)) {
        val compact = maxWidth < 400.dp
        Layout(
            content = {
                main.forEach { FlowNodeBox(it, compact) }
                side.forEach { FlowNodeBox(it, compact) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description }
                .drawBehind {
                    val g = geometry ?: return@drawBehind
                    drawDashedRegion(g.region, g.note, lineColor)
                    drawSwapArrows(g, yes, no, lineColor, loopColor, textMeasurer, labelStyle)
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

            val note = textMeasurer.measure(
                limitNote,
                noteStyle,
                constraints = Constraints(maxWidth = (width - leftGutter - mainWidth / 2 - 24.dp.roundToPx()).coerceAtLeast(1)),
            )
            val regionPad = 8.dp.roundToPx()

            fun constraintsFor(node: FlowNode, columnWidth: Int): Constraints {
                val fills = node.kind == NodeKind.Action || node.kind == NodeKind.Failure
                return if (fills) Constraints.fixedWidth(columnWidth) else Constraints(maxWidth = columnWidth)
            }
            val mainPlaceables = measurables.take(S_MAIN_COUNT).mapIndexed { i, m -> m.measure(constraintsFor(main[i], mainWidth)) }
            val sidePlaceables = measurables.drop(S_MAIN_COUNT).mapIndexed { i, m -> m.measure(constraintsFor(side[i], sideWidth)) }

            val sideX = leftGutter + mainWidth + columnGap
            val rows = maxOf(S_MAIN_COUNT, S_CHECK + S_SIDE_COUNT)
            val mainRects = ArrayList<Rect>(S_MAIN_COUNT)
            val sideRects = ArrayList<Rect>(S_SIDE_COUNT)
            var y = 0
            for (row in 0 until rows) {
                if (row == S_MERGE) y += note.size.height + regionPad
                val mainPlaceable = mainPlaceables.getOrNull(row)
                val sidePlaceable = sidePlaceables.getOrNull(row - S_CHECK)
                val rowHeight = maxOf(mainPlaceable?.height ?: 0, sidePlaceable?.height ?: 0)
                if (mainPlaceable != null) {
                    val top = y + (rowHeight - mainPlaceable.height) / 2
                    val left = leftGutter + (mainWidth - mainPlaceable.width) / 2
                    mainRects += Rect(Offset(left.toFloat(), top.toFloat()), Size(mainPlaceable.width.toFloat(), mainPlaceable.height.toFloat()))
                }
                if (sidePlaceable != null) {
                    val top = y + (rowHeight - sidePlaceable.height) / 2
                    val left = sideX + (sideWidth - sidePlaceable.width) / 2
                    sideRects += Rect(Offset(left.toFloat(), top.toFloat()), Size(sidePlaceable.width.toFloat(), sidePlaceable.height.toFloat()))
                }
                y += rowHeight + rowGap
            }
            val height = y - rowGap + regionPad + 2.dp.roundToPx()
            geometry = SwapGeometry(
                main = mainRects,
                side = sideRects,
                loopX = (width - rightGutter / 2).toFloat(),
                region = Rect(
                    left = 2.dp.toPx(),
                    top = mainRects[S_MERGE].top - note.size.height - regionPad,
                    right = width - 1.dp.toPx(),
                    bottom = sideRects.last().bottom + regionPad,
                ),
                note = note,
            )

            layout(width, height) {
                mainPlaceables.forEachIndexed { i, p -> p.place(mainRects[i].left.toInt(), mainRects[i].top.toInt()) }
                sidePlaceables.forEachIndexed { i, p -> p.place(sideRects[i].left.toInt(), sideRects[i].top.toInt()) }
            }
        }
    }
}

private class SwapGeometry(
    val main: List<Rect>,
    val side: List<Rect>,
    val loopX: Float,
    /** The part that runs until the time limit, in a dashed box. */
    val region: Rect,
    val note: TextLayoutResult,
)

private fun DrawScope.drawSwapArrows(
    g: SwapGeometry,
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
    for (i in 0 until S_MAIN_COUNT - 1) {
        val from = Offset(g.main[i].center.x, g.main[i].bottom)
        val to = Offset(g.main[i + 1].center.x, g.main[i + 1].top)
        arrow(listOf(from, to), lineColor, stroke)
        if (i == S_CHECK) besideLabel(no, Offset(from.x, (from.y + to.y) / 2f))
    }

    val check = g.main[S_CHECK]
    val merge = g.main[S_MERGE]
    val pick = g.side[0]
    val swap = g.side[1]
    val stuck = g.side[2]
    val shake = g.side[3]

    // A repeat left: a player of it is picked and swapped.
    arrow(listOf(Offset(check.right, check.center.y), Offset(pick.left, pick.center.y)), lineColor, stroke)
    aboveLabel(yes, Offset((check.right + pick.left) / 2f, check.center.y))
    arrow(listOf(Offset(pick.center.x, pick.bottom), Offset(swap.center.x, swap.top)), lineColor, stroke)
    arrow(listOf(Offset(swap.center.x, swap.bottom), Offset(stuck.center.x, stuck.top)), lineColor, stroke)
    arrow(listOf(Offset(stuck.center.x, stuck.bottom), Offset(shake.center.x, shake.top)), lineColor, stroke)
    besideLabel(yes, Offset(stuck.center.x, (stuck.bottom + shake.top) / 2f))

    // Both ends of the side column go back up, into the merge node, to count the repeats again.
    arrow(
        listOf(
            Offset(shake.right, shake.center.y),
            Offset(g.loopX, shake.center.y),
            Offset(g.loopX, merge.center.y),
            Offset(merge.right, merge.center.y),
        ),
        loopColor,
        stroke,
        dash,
    )
    drawLine(loopColor, Offset(stuck.right, stuck.center.y), Offset(g.loopX, stuck.center.y), stroke, pathEffect = dash)
    aboveLabel(no, Offset((stuck.right + g.loopX) / 2f, stuck.center.y))
}
