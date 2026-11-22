package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.draw_flow_attempt
import mahjongtournamentsuite.ui.generated.resources.draw_flow_candidates
import mahjongtournamentsuite.ui.generated.resources.draw_flow_check
import mahjongtournamentsuite.ui.generated.resources.draw_flow_description
import mahjongtournamentsuite.ui.generated.resources.draw_flow_discard
import mahjongtournamentsuite.ui.generated.resources.draw_flow_done
import mahjongtournamentsuite.ui.generated.resources.draw_flow_failed
import mahjongtournamentsuite.ui.generated.resources.draw_flow_more_seats
import mahjongtournamentsuite.ui.generated.resources.draw_flow_next_seat
import mahjongtournamentsuite.ui.generated.resources.draw_flow_next_seat_note
import mahjongtournamentsuite.ui.generated.resources.draw_flow_no
import mahjongtournamentsuite.ui.generated.resources.draw_flow_parallel
import mahjongtournamentsuite.ui.generated.resources.draw_flow_pick
import mahjongtournamentsuite.ui.generated.resources.draw_flow_seat
import mahjongtournamentsuite.ui.generated.resources.draw_flow_seed
import mahjongtournamentsuite.ui.generated.resources.draw_flow_start
import mahjongtournamentsuite.ui.generated.resources.draw_flow_yes
import org.jetbrains.compose.resources.stringResource

/*
 * The flowchart of the draw of the pairings, as GenerateTournamentScheduleBruteForceParallelUseCase does it, in the
 * notation of a UML activity diagram: a filled circle starts it, actions are rounded boxes, decisions are diamonds with
 * the guards [Yes] and [No] on their arrows, and a ringed circle ends it (Ernesto, 2026-11-18).
 *
 * The main path goes down the left column. The "yes" branch of the rule check goes to the right column, and three
 * dashed arrows go back up:
 * - "Others left? Yes" goes back to the random pick, on the right side;
 * - "Attempt failed" goes back to a new attempt, further right and in the error color;
 * - "Seats left? Yes" goes back to the next free seat, on the left side.
 */

/** Merge is the small empty diamond of UML where two paths join. */
internal enum class NodeKind { Initial, Final, Action, Decision, Failure, Merge }

internal data class FlowNode(val kind: NodeKind, val text: String, val note: String? = null)

// The rows of the main column, top to bottom.
private const val INITIAL = 0
private const val START = 1
private const val SEED = 2
private const val ATTEMPT = 3
private const val NEXT_SEAT = 4
private const val PICK = 5
private const val CHECK = 6
private const val SEAT = 7
private const val MORE_SEATS = 8
private const val DONE = 9
private const val FINAL = 10
private const val MAIN_COUNT = 11

// The side column shares the rows of CHECK, SEAT and MORE_SEATS.
private const val SIDE_FIRST_ROW = CHECK

/** A drawn flowchart of how the pairings of a tournament are drawn. It follows the theme and the app language. */
@Composable
fun DrawFlowDiagram(modifier: Modifier = Modifier) {
    val main = listOf(
        FlowNode(NodeKind.Initial, ""),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_start)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_seed)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_attempt)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_next_seat), stringResource(Res.string.draw_flow_next_seat_note)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_pick)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_check)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_seat)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_more_seats)),
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_done)),
        FlowNode(NodeKind.Final, ""),
    )
    val side = listOf(
        FlowNode(NodeKind.Action, stringResource(Res.string.draw_flow_discard)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_candidates)),
        FlowNode(NodeKind.Failure, stringResource(Res.string.draw_flow_failed)),
    )
    // The guards of a decision, between brackets as in UML.
    val yes = "[" + stringResource(Res.string.draw_flow_yes) + "]"
    val no = "[" + stringResource(Res.string.draw_flow_no) + "]"
    val description = stringResource(Res.string.draw_flow_description)
    val parallelNote = stringResource(Res.string.draw_flow_parallel)

    val colors = MaterialTheme.colorScheme
    val lineColor = colors.onSurfaceVariant
    val loopColor = colors.primary
    val failColor = colors.error
    val labelBackground = colors.surfaceContainerHigh
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.onSurface)
    val textMeasurer = rememberTextMeasurer()
    val regionColor = colors.onSurfaceVariant
    val noteStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.End)

    // Filled by the layout and read by the drawing of the arrows.
    var geometry by remember { mutableStateOf<FlowGeometry?>(null) }

    // A phone has little room for two columns, so the gutters and the paddings get smaller there.
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
                    drawParallelRegion(g, regionColor)
                    drawArrows(g, yes, no, lineColor, loopColor, failColor, labelBackground, textMeasurer, labelStyle)
                },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 520.dp.roundToPx()
            val leftGutter = (if (compact) 24.dp else 30.dp).roundToPx()
            val rightGutter = (if (compact) 36.dp else 46.dp).roundToPx()
            val columnGap = (if (compact) 16.dp else 30.dp).roundToPx()
            val rowGap = 24.dp.roundToPx()
            val available = (width - leftGutter - rightGutter - columnGap).coerceAtLeast(1)
            val mainWidth = (available * (if (compact) 0.6f else 0.58f)).toInt()
            val sideWidth = available - mainWidth

            // The note of the part that runs in parallel goes in its top right corner, right of the arrow that enters it.
            val note = textMeasurer.measure(
                parallelNote,
                noteStyle,
                constraints = Constraints(maxWidth = (width - leftGutter - mainWidth / 2 - 24.dp.roundToPx()).coerceAtLeast(1)),
            )
            val regionPad = 8.dp.roundToPx()

            // Room above the first row of the region for the note, and under the last one for its bottom side.
            fun extraGapBefore(row: Int): Int = when (row) {
                ATTEMPT -> note.size.height + regionPad + 4.dp.roundToPx()
                DONE -> 16.dp.roundToPx()
                else -> 0
            }

            // Actions fill their column. Circles and diamonds keep their own size and go in the middle of it.
            fun constraintsFor(node: FlowNode, columnWidth: Int): Constraints {
                val fills = node.kind == NodeKind.Action || node.kind == NodeKind.Failure
                return if (fills) Constraints.fixedWidth(columnWidth) else Constraints(maxWidth = columnWidth)
            }
            val mainPlaceables = measurables.take(MAIN_COUNT).mapIndexed { i, m -> m.measure(constraintsFor(main[i], mainWidth)) }
            val sidePlaceables = measurables.drop(MAIN_COUNT).mapIndexed { i, m -> m.measure(constraintsFor(side[i], sideWidth)) }

            val mainX = leftGutter
            val sideX = leftGutter + mainWidth + columnGap
            val mainRects = ArrayList<Rect>(MAIN_COUNT)
            val sideRects = ArrayList<Rect>(sidePlaceables.size)
            var y = 0
            mainPlaceables.forEachIndexed { row, placeable ->
                y += extraGapBefore(row)
                val sidePlaceable = sidePlaceables.getOrNull(row - SIDE_FIRST_ROW)
                val rowHeight = maxOf(placeable.height, sidePlaceable?.height ?: 0)
                val mainTop = y + (rowHeight - placeable.height) / 2
                val mainLeft = mainX + (mainWidth - placeable.width) / 2
                mainRects += Rect(Offset(mainLeft.toFloat(), mainTop.toFloat()), Size(placeable.width.toFloat(), placeable.height.toFloat()))
                if (sidePlaceable != null) {
                    val sideTop = y + (rowHeight - sidePlaceable.height) / 2
                    val sideLeft = sideX + (sideWidth - sidePlaceable.width) / 2
                    sideRects += Rect(Offset(sideLeft.toFloat(), sideTop.toFloat()), Size(sidePlaceable.width.toFloat(), sidePlaceable.height.toFloat()))
                }
                y += rowHeight + rowGap
            }
            val height = y - rowGap
            geometry = FlowGeometry(
                main = mainRects,
                side = sideRects,
                leftLoopX = leftGutter / 2f,
                rightLoopX = (width - rightGutter + (if (compact) 12.dp else 16.dp).roundToPx()).toFloat(),
                // Well inside the dashed box, so the red line and the side of the box do not run together.
                failLoopX = (width - (if (compact) 11.dp else 13.dp).roundToPx()).toFloat(),
                region = Rect(
                    left = 2.dp.toPx(),
                    top = mainRects[ATTEMPT].top - note.size.height - regionPad,
                    right = width - 1.dp.toPx(),
                    bottom = maxOf(mainRects[MORE_SEATS].bottom, sideRects.last().bottom) + 8.dp.toPx(),
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

private class FlowGeometry(
    val main: List<Rect>,
    val side: List<Rect>,
    val leftLoopX: Float,
    val rightLoopX: Float,
    val failLoopX: Float,
    /** The part of the diagram that runs many times at once, in a dashed box. */
    val region: Rect,
    val note: TextLayoutResult,
)

private fun DrawScope.drawParallelRegion(g: FlowGeometry, color: Color) = drawDashedRegion(g.region, g.note, color)

/** A dashed box around a part of a diagram, with [note] in its top right corner. */
internal fun DrawScope.drawDashedRegion(region: Rect, note: TextLayoutResult, color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    drawRoundRect(
        color = color.copy(alpha = 0.7f),
        topLeft = region.topLeft,
        size = region.size,
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
    )
    drawText(note, topLeft = Offset(region.right - 8.dp.toPx() - note.size.width, region.top + 4.dp.toPx()))
}

@Composable
internal fun FlowNodeBox(node: FlowNode, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = if (compact) (if (node.kind == NodeKind.Decision) 10.sp else 11.sp) else MaterialTheme.typography.bodySmall.fontSize,
        lineHeight = if (compact) (if (node.kind == NodeKind.Decision) 13.sp else 14.sp) else MaterialTheme.typography.bodySmall.lineHeight,
        fontWeight = if (node.kind == NodeKind.Decision) FontWeight.SemiBold else FontWeight.Normal,
    )
    when (node.kind) {
        NodeKind.Initial -> {
            // UML initial node: a filled circle.
            val color = colors.onSurface
            Box(Modifier.size(24.dp).drawBehind { drawCircle(color, radius = size.minDimension / 2f - 2.dp.toPx()) })
        }
        NodeKind.Final -> {
            // UML final node: a filled circle inside a ring.
            val color = colors.onSurface
            Box(
                Modifier.size(24.dp).drawBehind {
                    drawCircle(color, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    drawCircle(color, radius = size.minDimension / 2f - 5.dp.toPx())
                },
            )
        }
        NodeKind.Merge -> {
            // UML merge node: an empty diamond, where two paths join.
            val fill = colors.secondaryContainer
            val line = colors.secondary
            Box(
                Modifier.size(22.dp).drawBehind {
                    val diamond = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(size.width / 2f, size.height)
                        lineTo(0f, size.height / 2f)
                        close()
                    }
                    drawPath(diamond, fill)
                    drawPath(diamond, line, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
                },
            )
        }
        NodeKind.Decision -> DecisionNode(node.text, textStyle, colors.secondaryContainer, colors.secondary, colors.onSecondaryContainer, compact)
        NodeKind.Action, NodeKind.Failure -> {
            val failure = node.kind == NodeKind.Failure
            val shape = MaterialTheme.shapes.medium
            val content = if (failure) colors.onErrorContainer else colors.onSurface
            Column(
                modifier = Modifier
                    .background(if (failure) colors.errorContainer else colors.surfaceContainerHigh, shape)
                    .border(1.dp, if (failure) colors.error else colors.outline, shape)
                    .padding(horizontal = if (compact) MtsTheme.spacing.xs else MtsTheme.spacing.md, vertical = MtsTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(node.text, style = textStyle, color = content, textAlign = TextAlign.Center)
                node.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 10.sp else MaterialTheme.typography.labelSmall.fontSize),
                        color = content.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A UML decision: a diamond with the question inside. The diamond takes the size its text needs, up to the width of its
 * column, so that the text fits between its four sides.
 */
@Composable
private fun DecisionNode(text: String, style: TextStyle, container: Color, border: Color, content: Color, compact: Boolean) {
    Layout(
        content = { Text(text, style = style, color = content, textAlign = TextAlign.Center) },
        modifier = Modifier.drawBehind {
            val diamond = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            drawPath(diamond, container)
            drawPath(diamond, border, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        },
    ) { measurables, constraints ->
        val columnWidth = constraints.maxWidth
        // The longest word never breaks: on a phone the text may take more of the diamond, which then grows taller.
        val longestWord = measurables.first().minIntrinsicWidth(Int.MAX_VALUE)
        // On a wide column the text wraps sooner, so the diamond does not take the whole width.
        val textMax = maxOf((columnWidth * (if (compact) 0.6f else 0.42f)).toInt(), longestWord).coerceAtMost((columnWidth * 0.8f).toInt())
        val text = measurables.first().measure(Constraints(maxWidth = textMax))
        // A text box fits in a diamond when (text width / diamond width) + (text height / diamond height) <= 1.
        val width = (text.width / 0.56f).toInt().coerceAtMost(columnWidth)
        val widthShare = text.width.toFloat() / width
        val height = maxOf((text.height / (1f - widthShare)).toInt() + 8.dp.roundToPx(), (width * 0.42f).toInt())
        layout(width, height) { text.place((width - text.width) / 2, (height - text.height) / 2) }
    }
}

private fun DrawScope.drawArrows(
    g: FlowGeometry,
    yes: String,
    no: String,
    lineColor: Color,
    loopColor: Color,
    failColor: Color,
    labelBackground: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val stroke = 1.5.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
    // A label on a straight arrow goes beside it, so it does not hide the arrowhead. A label on a long loop goes on it.
    val beside = ArrayList<Pair<String, Offset>>()
    val above = ArrayList<Pair<String, Offset>>()
    val onLine = ArrayList<Pair<String, Offset>>()

    // The main path, straight down.
    for (i in 0 until MAIN_COUNT - 1) {
        val from = Offset(g.main[i].center.x, g.main[i].bottom)
        val to = Offset(g.main[i + 1].center.x, g.main[i + 1].top)
        arrow(listOf(from, to), lineColor, stroke)
        if (i == CHECK || i == MORE_SEATS) beside += no to Offset(from.x, (from.y + to.y) / 2f)
    }

    val check = g.main[CHECK]
    val discard = g.side[0]
    val others = g.side[1]
    val failed = g.side[2]

    // A conflict: the candidate is discarded.
    arrow(listOf(Offset(check.right, check.center.y), Offset(discard.left, discard.center.y)), lineColor, stroke)
    above += yes to Offset((check.right + discard.left) / 2f, check.center.y)
    arrow(listOf(Offset(discard.center.x, discard.bottom), Offset(others.center.x, others.top)), lineColor, stroke)
    arrow(listOf(Offset(others.center.x, others.bottom), Offset(failed.center.x, failed.top)), lineColor, stroke)
    beside += no to Offset(others.center.x, (others.bottom + failed.top) / 2f)

    // Others left: back to the random pick.
    val pick = g.main[PICK]
    arrow(
        listOf(
            Offset(others.right, others.center.y),
            Offset(g.rightLoopX, others.center.y),
            Offset(g.rightLoopX, pick.center.y),
            Offset(pick.right, pick.center.y),
        ),
        loopColor,
        stroke,
        dash,
    )
    onLine += yes to Offset(g.rightLoopX, (others.center.y + discard.center.y) / 2f)

    // A failed attempt: a new one starts.
    val attempt = g.main[ATTEMPT]
    arrow(
        listOf(
            Offset(failed.right, failed.center.y),
            Offset(g.failLoopX, failed.center.y),
            Offset(g.failLoopX, attempt.center.y),
            Offset(attempt.right, attempt.center.y),
        ),
        failColor,
        stroke,
        dash,
    )

    // Seats left: the next seat.
    val more = g.main[MORE_SEATS]
    val nextSeat = g.main[NEXT_SEAT]
    arrow(
        listOf(
            Offset(more.left, more.center.y),
            Offset(g.leftLoopX, more.center.y),
            Offset(g.leftLoopX, nextSeat.center.y),
            Offset(nextSeat.left, nextSeat.center.y),
        ),
        loopColor,
        stroke,
        dash,
    )
    onLine += yes to Offset(g.leftLoopX, (more.center.y + nextSeat.center.y) / 2f)

    val gap = 4.dp.toPx()
    beside.forEach { (text, at) ->
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(layout, topLeft = Offset(at.x + gap, at.y - layout.size.height / 2f))
    }
    above.forEach { (text, at) ->
        val layout = textMeasurer.measure(text, labelStyle)
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height - 1.dp.toPx()))
    }
    // On a loop, the label sits on a small pill that hides the line under it.
    onLine.forEach { (text, center) ->
        val layout = textMeasurer.measure(text, labelStyle)
        val padH = 3.dp.toPx()
        val padV = 2.dp.toPx()
        val w = layout.size.width + padH * 2
        val h = layout.size.height + padV * 2
        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
        drawRoundRect(labelBackground, topLeft, Size(w, h), CornerRadius(h / 2f))
        drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
    }
}

/** A line through [points] with an arrowhead at the last one. */
internal fun DrawScope.arrow(points: List<Offset>, color: Color, stroke: Float, effect: PathEffect? = null) {
    val head = 7.dp.toPx()
    val end = points.last()
    val before = points[points.size - 2]
    val dx = end.x - before.x
    val dy = end.y - before.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val ux = dx / length
    val uy = dy / length
    // The line stops at the base of the head, so the dashes do not poke through it.
    val base = Offset(end.x - ux * head, end.y - uy * head)
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).dropLast(1).forEach { lineTo(it.x, it.y) }
        lineTo(base.x, base.y)
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    val half = head * 0.55f
    val headPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(base.x - uy * half, base.y + ux * half)
        lineTo(base.x + uy * half, base.y - ux * half)
        close()
    }
    drawPath(headPath, color)
}
