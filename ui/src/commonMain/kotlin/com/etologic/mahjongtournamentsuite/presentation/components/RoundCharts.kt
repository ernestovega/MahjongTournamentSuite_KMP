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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

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
    draw: DrawScope.(slot: Float) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = if (RoundSlotWidth * rounds > maxWidth) RoundSlotWidth * rounds else maxWidth
        val scroll = rememberScrollState()
        LaunchedEffect(rounds, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        Canvas(
            Modifier.horizontalScroll(scroll).width(width).height(height)
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
 * Stacked bars, one for each round, from zero up. [labels] go over the bars. A round with no parts shows a dash.
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
    maxValue: Double = bars.maxOfOrNull { bar -> bar.sumOf { it.value } }?.takeIf { it > 0 } ?: 1.0,
    height: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    RoundCanvas(names.size, height, onSelect, modifier) { slot ->
        selection(selected, slot, colors.primary.copy(alpha = 0.08f))
        val top = AxisSpace.toPx()
        val bottom = size.height - AxisSpace.toPx()
        drawLine(colors.outlineVariant, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        val barWidth = slot * 0.56f
        bars.forEachIndexed { index, parts ->
            val left = slot * index + (slot - barWidth) / 2f
            var y = bottom
            parts.filter { it.value > 0 }.forEach { part ->
                val h = ((bottom - top) * (part.value / maxValue)).toFloat()
                drawRect(part.color, Offset(left, y - h), Size(barWidth, h))
                y -= h
            }
            val label = labels.getOrNull(index) ?: if (parts.none { it.value > 0 }) "—" else null
            if (label != null) {
                val layout = measurer.measure(label, TextStyle(fontSize = 11.sp, color = colors.onSurface, fontWeight = FontWeight.Bold))
                drawText(layout, topLeft = Offset(slot * index + (slot - layout.size.width) / 2f, (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        roundNames(names, slot, selected, measurer, colors.onSurfaceVariant, colors.primary)
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
) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    RoundCanvas(names.size, height, onSelect, modifier) { slot ->
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
        if (selected != null && value != null) {
            val layout = measurer.measure(format(value), TextStyle(fontSize = 11.sp, color = colors.onSurface, fontWeight = FontWeight.Bold))
            val x = (slot * selected + (slot - layout.size.width) / 2f).coerceIn(0f, size.width - layout.size.width)
            val py = y(value)
            val ty = if (py - layout.size.height - 8.dp.toPx() >= 0f) py - layout.size.height - 8.dp.toPx() else py + 8.dp.toPx()
            drawText(layout, topLeft = Offset(x, ty))
        }
        roundNames(names, slot, selected, measurer, colors.onSurfaceVariant, colors.primary)
    }
}
