package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_attempt
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_attempt_note
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_candidates
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_check
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_description
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_discard
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_done
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_done_note
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_failed
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_more_seats
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_next_seat
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_next_seat_note
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_no
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_pick
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_seat
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_seed
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_start
import mahjongtournamentsuite.adminapp.generated.resources.draw_flow_yes
import org.jetbrains.compose.resources.stringResource

/*
 * The flowchart of the draw of the pairings, as GenerateTournamentScheduleBruteForceParallelUseCase does it.
 *
 * The main path goes down the left column. The "yes" branch of the rule check goes to the right column, and three
 * dashed arrows go back up:
 * - "Others left? Yes" goes back to the random pick, on the right side;
 * - "Attempt failed" goes back to a new attempt, further right and in the error color;
 * - "Seats left? Yes" goes back to the next free seat, on the left side.
 */

private enum class NodeKind { Terminal, Process, Decision, Failure }

private data class FlowNode(val kind: NodeKind, val text: String, val note: String? = null)

// The rows of the main column, top to bottom.
private const val START = 0
private const val SEED = 1
private const val ATTEMPT = 2
private const val NEXT_SEAT = 3
private const val PICK = 4
private const val CHECK = 5
private const val SEAT = 6
private const val MORE_SEATS = 7
private const val DONE = 8
private const val MAIN_COUNT = 9

// The side column shares the rows of CHECK, SEAT and MORE_SEATS.
private const val SIDE_FIRST_ROW = CHECK

/** A drawn flowchart of how the pairings of a tournament are drawn. It follows the theme and the app language. */
@Composable
fun DrawFlowDiagram(modifier: Modifier = Modifier) {
    val main = listOf(
        FlowNode(NodeKind.Terminal, stringResource(Res.string.draw_flow_start)),
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_seed)),
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_attempt), stringResource(Res.string.draw_flow_attempt_note)),
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_next_seat), stringResource(Res.string.draw_flow_next_seat_note)),
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_pick)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_check)),
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_seat)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_more_seats)),
        FlowNode(NodeKind.Terminal, stringResource(Res.string.draw_flow_done), stringResource(Res.string.draw_flow_done_note)),
    )
    val side = listOf(
        FlowNode(NodeKind.Process, stringResource(Res.string.draw_flow_discard)),
        FlowNode(NodeKind.Decision, stringResource(Res.string.draw_flow_candidates)),
        FlowNode(NodeKind.Failure, stringResource(Res.string.draw_flow_failed)),
    )
    val yes = stringResource(Res.string.draw_flow_yes)
    val no = stringResource(Res.string.draw_flow_no)
    val description = stringResource(Res.string.draw_flow_description)

    val colors = MaterialTheme.colorScheme
    val lineColor = colors.onSurfaceVariant
    val loopColor = colors.primary
    val failColor = colors.error
    val labelBackground = colors.surfaceContainerHigh
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.onSurface)
    val textMeasurer = rememberTextMeasurer()

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
                    drawArrows(g, yes, no, lineColor, loopColor, failColor, labelBackground, textMeasurer, labelStyle)
                },
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 520.dp.roundToPx()
            val leftGutter = (if (compact) 22.dp else 30.dp).roundToPx()
            val rightGutter = (if (compact) 28.dp else 40.dp).roundToPx()
            val columnGap = (if (compact) 20.dp else 30.dp).roundToPx()
            val rowGap = (if (compact) 22.dp else 24.dp).roundToPx()
            val available = (width - leftGutter - rightGutter - columnGap).coerceAtLeast(1)
            val mainWidth = (available * (if (compact) 0.6f else 0.58f)).toInt()
            val sideWidth = available - mainWidth

            val mainPlaceables = measurables.take(MAIN_COUNT).map { it.measure(Constraints.fixedWidth(mainWidth)) }
            val sidePlaceables = measurables.drop(MAIN_COUNT).map { it.measure(Constraints.fixedWidth(sideWidth)) }

            val mainX = leftGutter
            val sideX = leftGutter + mainWidth + columnGap
            val mainRects = ArrayList<Rect>(MAIN_COUNT)
            val sideRects = ArrayList<Rect>(sidePlaceables.size)
            var y = 0
            mainPlaceables.forEachIndexed { row, placeable ->
                val sidePlaceable = sidePlaceables.getOrNull(row - SIDE_FIRST_ROW)
                val rowHeight = maxOf(placeable.height, sidePlaceable?.height ?: 0)
                val mainTop = y + (rowHeight - placeable.height) / 2
                mainRects += Rect(Offset(mainX.toFloat(), mainTop.toFloat()), Size(placeable.width.toFloat(), placeable.height.toFloat()))
                if (sidePlaceable != null) {
                    val sideTop = y + (rowHeight - sidePlaceable.height) / 2
                    sideRects += Rect(Offset(sideX.toFloat(), sideTop.toFloat()), Size(sidePlaceable.width.toFloat(), sidePlaceable.height.toFloat()))
                }
                y += rowHeight + rowGap
            }
            val height = y - rowGap
            geometry = FlowGeometry(
                main = mainRects,
                side = sideRects,
                leftLoopX = leftGutter / 2f,
                rightLoopX = (width - rightGutter + (if (compact) 11.dp else 14.dp).roundToPx()).toFloat(),
                failLoopX = (width - (if (compact) 4.dp else 6.dp).roundToPx()).toFloat(),
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
)

/** The points of a decision: a hexagon with a point on each side. */
private val DecisionShape: Shape = GenericShape { size, _ ->
    val inset = minOf(size.height / 2f, size.width * 0.12f)
    moveTo(inset, 0f)
    lineTo(size.width - inset, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - inset, size.height)
    lineTo(inset, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

@Composable
private fun FlowNodeBox(node: FlowNode, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape: Shape
    val container: Color
    val content: Color
    val border: Color
    var horizontalPadding = if (compact) 6.dp else 10.dp
    when (node.kind) {
        NodeKind.Terminal -> {
            shape = RoundedCornerShape(20.dp)
            container = colors.primary
            content = colors.onPrimary
            border = colors.primary
            horizontalPadding = if (compact) 6.dp else 14.dp
        }
        NodeKind.Process -> {
            shape = RoundedCornerShape(10.dp)
            container = colors.surfaceContainerHigh
            content = colors.onSurface
            border = colors.outlineVariant
        }
        NodeKind.Decision -> {
            shape = DecisionShape
            container = colors.secondaryContainer
            content = colors.onSecondaryContainer
            border = colors.secondary
            horizontalPadding = if (compact) 9.dp else 18.dp
        }
        NodeKind.Failure -> {
            shape = RoundedCornerShape(10.dp)
            container = colors.errorContainer
            content = colors.onErrorContainer
            border = colors.error
        }
    }
    Column(
        modifier = Modifier
            .background(container, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            node.text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (compact) 11.sp else MaterialTheme.typography.bodySmall.fontSize,
                lineHeight = if (compact) 14.sp else MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = if (node.kind == NodeKind.Terminal || node.kind == NodeKind.Decision) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = content,
            textAlign = TextAlign.Center,
        )
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
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
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
private fun DrawScope.arrow(points: List<Offset>, color: Color, stroke: Float, effect: PathEffect? = null) {
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
