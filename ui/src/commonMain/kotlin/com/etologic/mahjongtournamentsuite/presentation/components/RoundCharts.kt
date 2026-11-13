package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** One part of a stacked bar. */
data class BarPart(val value: Double, val color: Color)

/** One line of a line chart. A null value leaves a gap. [dotsOnly] draws only the points, for the values of one round. */
data class LineSeries(val values: List<Double?>, val color: Color, val dotsOnly: Boolean = false)

/** The width of one round in the charts. More rounds than fit scroll sideways. */
private val RoundSlotWidth = 44.dp

/** The space under the chart for the round names, and over it for the labels. */
private val AxisSpace = 24.dp

/**
 * A frame for the round charts: a canvas as wide as the card, or wider when the rounds do not fit, then it scrolls
 * sideways and starts at the end, where the last rounds are. A tap selects the round under the finger.
 */
@Composable
private fun RoundCanvas(
    rounds: Int,
    height: Dp,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    description: String?,
    draw: DrawScope.(slot: Float) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = if (RoundSlotWidth * rounds > maxWidth) RoundSlotWidth * rounds else maxWidth
        val scroll = rememberScrollState()
        LaunchedEffect(rounds, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        Canvas(
            Modifier.horizontalScroll(scroll).width(width).height(height)
                .semantics { if (description != null) contentDescription = description }
                .pointerInput(rounds) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / rounds.coerceAtLeast(1)
                        onSelect((offset.x / slot).toInt().coerceIn(0, rounds - 1))
                    }
                },
        ) { draw(size.width / rounds.coerceAtLeast(1)) }
    }
}

private fun DrawScope.roundNames(names: List<String>, slot: Float, selected: Int?, measurer: TextMeasurer, color: Color, selectedColor: Color) {
    names.forEachIndexed { index, name ->
        val layout = measurer.measure(
            name,
            TextStyle(fontSize = 11.sp, color = if (index == selected) selectedColor else color, fontWeight = if (index == selected) FontWeight.Bold else null),
        )
        drawText(layout, topLeft = Offset(slot * index + (slot - layout.size.width) / 2f, size.height - layout.size.height))
    }
}

private fun DrawScope.selection(index: Int?, slot: Float, color: Color) {
    if (index == null) return
    drawRoundRect(color, Offset(slot * index + 2.dp.toPx(), 0f), Size(slot - 4.dp.toPx(), size.height), CornerRadius(8.dp.toPx()))
}

/**
 * The tooltip of the selected round: a bubble over [anchorY], the top of its bar or its point. The text comes as
 * "Round 3 · Table 1 · place 1", or as lines: the first part is the title and every other part is a bullet point. Without room over
 * the anchor, the bubble goes beside the round.
 */
private fun DrawScope.tooltip(text: String, index: Int, slot: Float, anchorY: Float, measurer: TextMeasurer, background: Color, content: Color) {
    // Several lines are the title and one bullet point for each line. One line splits at " · ".
    val parts = if ('\n' in text) text.split('\n') else text.split(" · ")
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parts.first()) }
        parts.drop(1).forEach { append("\n• $it") }
    }
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val gap = 8.dp.toPx()
    val maxText = (min(size.width, 240.dp.toPx()) - 2 * padH).toInt().coerceAtLeast(1)
    val layout = measurer.measure(annotated, TextStyle(fontSize = 11.sp, color = content), constraints = Constraints(maxWidth = maxText))
    val w = layout.size.width + 2 * padH
    val h = layout.size.height + 2 * padV
    val center = slot * index + slot / 2f
    var x: Float
    var y: Float
    if (anchorY - gap - h >= 0f) {
        x = center - w / 2f
        y = anchorY - gap - h
    } else {
        y = anchorY - h / 2f
        x = if (center + slot / 2f + w <= size.width) center + slot / 2f else center - slot / 2f - w
    }
    x = x.coerceIn(0f, max(0f, size.width - w))
    y = y.coerceIn(0f, max(0f, size.height - h))
    drawRoundRect(background, Offset(x, y), Size(w, h), CornerRadius(8.dp.toPx()))
    drawText(layout, topLeft = Offset(x + padH, y + padV))
}

/**
 * Stacked bars, one for each round: the positive parts from zero up and the negative ones from zero down. [labels] go over the bars. A round with no parts shows a dash.
 * [maxValue] is the top of the scale, so several charts can share it.
 */
@Composable
fun RoundBarChart(
    names: List<String>,
    bars: List<List<BarPart>>,
    labels: List<String?>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Double = bars.maxOfOrNull { bar -> bar.filter { it.value > 0 }.sumOf { it.value } }?.takeIf { it > 0 } ?: 1.0,
    height: Dp = 180.dp,
    /** The text of the selected round, in a bubble over its bar. Parts split by " · " show as bullet points. */
    tooltip: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    // The negative parts, like a negative score, go down from the zero line. Without them the zero line is the bottom.
    val minValue = bars.minOfOrNull { bar -> bar.filter { it.value < 0 }.sumOf { it.value } }?.coerceAtMost(0.0) ?: 0.0
    RoundCanvas(names.size, height, onSelect, modifier, tooltip) { slot ->
        selection(selected, slot, colors.primary.copy(alpha = 0.08f))
        val top = AxisSpace.toPx()
        val bottom = size.height - AxisSpace.toPx()
        val range = maxValue - minValue
        val zero = top + ((bottom - top) * (maxValue / range)).toFloat()
        fun extent(value: Double) = ((bottom - top) * (kotlin.math.abs(value) / range)).toFloat()
        drawLine(colors.outlineVariant, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        val barWidth = slot * 0.56f
        var selectedTop = zero
        bars.forEachIndexed { index, parts ->
            val left = slot * index + (slot - barWidth) / 2f
            var up = zero
            parts.filter { it.value > 0 }.forEach { part ->
                val h = extent(part.value)
                drawRect(part.color, Offset(left, up - h), Size(barWidth, h))
                up -= h
            }
            var down = zero
            parts.filter { it.value < 0 }.forEach { part ->
                val h = extent(part.value)
                drawRect(part.color, Offset(left, down), Size(barWidth, h))
                down += h
            }
            if (index == selected) selectedTop = up
            val label = labels.getOrNull(index) ?: if (parts.none { it.value != 0.0 }) "—" else null
            if (label != null && !(index == selected && tooltip != null)) {
                val layout = measurer.measure(label, TextStyle(fontSize = 11.sp, color = colors.onSurface, fontWeight = FontWeight.Bold))
                drawText(layout, topLeft = Offset(slot * index + (slot - layout.size.width) / 2f, (up - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        roundNames(names, slot, selected, measurer, colors.onSurfaceVariant, colors.primary)
        if (tooltip != null && selected != null) tooltip(tooltip, selected, slot, selectedTop, measurer, colors.inverseSurface, colors.inverseOnSurface)
    }
}

/**
 * Lines with one point for each round, between [minValue] and [maxValue]. With [inverted] the smallest value is at the
 * top, as a ranking place. [format] writes the value of the selected round next to its point.
 */
@Composable
fun RoundLineChart(
    names: List<String>,
    series: List<LineSeries>,
    minValue: Double,
    maxValue: Double,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    format: (Double) -> String = { it.toString() },
    height: Dp = 180.dp,
    /** The text of the selected round, in a bubble over its point instead of the value. See [RoundBarChart]. */
    tooltip: String? = null,
    /** True writes the value over every point of the first line, not only over the selected one. */
    allValues: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    RoundCanvas(names.size, height, onSelect, modifier, tooltip) { slot ->
        selection(selected, slot, colors.primary.copy(alpha = 0.08f))
        val top = AxisSpace.toPx()
        val bottom = size.height - AxisSpace.toPx()
        val range = max(maxValue - minValue, 1e-9)
        fun y(value: Double): Float {
            val share = ((value - minValue) / range).coerceIn(0.0, 1.0).toFloat()
            return if (inverted) top + (bottom - top) * share else bottom - (bottom - top) * share
        }
        // The guides at the top, the middle and the bottom of the scale.
        listOf(top, (top + bottom) / 2f, bottom).forEach { gy ->
            drawLine(colors.outlineVariant, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        series.forEach { line ->
            val points = line.values.mapIndexed { index, value -> value?.let { Offset(slot * index + slot / 2f, y(it)) } }
            if (!line.dotsOnly) {
                val path = Path()
                var open = false
                points.forEach { point ->
                    if (point == null) {
                        open = false
                    } else if (!open) {
                        path.moveTo(point.x, point.y)
                        open = true
                    } else {
                        path.lineTo(point.x, point.y)
                    }
                }
                drawPath(path, line.color, style = Stroke(2.5.dp.toPx()))
            }
            points.forEach { point ->
                if (point != null) drawCircle(line.color, if (line.dotsOnly) 3.dp.toPx() else 4.dp.toPx(), point)
            }
        }
        // The value of the selected round, over its point on the first line.
        val first = series.firstOrNull()
        val value = selected?.let { first?.values?.getOrNull(it) }
        // With a tooltip, the selected round has its value in the tooltip.
        val labelled: List<Int> = (if (allValues) first?.values?.indices?.toList().orEmpty() else listOfNotNull(selected))
            .filterNot { tooltip != null && it == selected }
        labelled.forEach { index ->
            val v = first?.values?.getOrNull(index) ?: return@forEach
            val bold = index == selected
            val layout = measurer.measure(
                format(v),
                TextStyle(fontSize = 11.sp, color = if (bold) colors.onSurface else colors.onSurfaceVariant, fontWeight = if (bold) FontWeight.Bold else null),
            )
            val x = (slot * index + (slot - layout.size.width) / 2f).coerceIn(0f, size.width - layout.size.width)
            val py = y(v)
            val ty = if (py - layout.size.height - 8.dp.toPx() >= 0f) py - layout.size.height - 8.dp.toPx() else py + 8.dp.toPx()
            drawText(layout, topLeft = Offset(x, ty))
        }
        roundNames(names, slot, selected, measurer, colors.onSurfaceVariant, colors.primary)
        if (selected != null && tooltip != null) {
            tooltip(tooltip, selected, slot, value?.let { y(it) } ?: ((top + bottom) / 2f), measurer, colors.inverseSurface, colors.inverseOnSurface)
        }
    }
}
