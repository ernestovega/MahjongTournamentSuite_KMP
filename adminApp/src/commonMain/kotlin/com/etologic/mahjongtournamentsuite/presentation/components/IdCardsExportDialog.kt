package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun Double.oneDecimal(): String {
    val tenths = (this * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}

private fun mmToScalePercent(widthMm: Double): Double =
    widthMm / (IdCardSheetLayout.CARD_WIDTH_PT * IdCardSheetLayout.MM_PER_PT) * 100

private fun mmHeightToScalePercent(heightMm: Double): Double =
    heightMm / (IdCardSheetLayout.CARD_HEIGHT_PT * IdCardSheetLayout.MM_PER_PT) * 100

/** A chain icon between the width and the height fields. Its tooltip explains the fixed proportion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProportionLinkIcon(originalWidthMm: Double, originalHeightMm: Double) {
    val ratio = (originalWidthMm / originalHeightMm * 100).roundToLong() / 100.0
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(modifier = Modifier.widthIn(max = 340.dp)) {
                Text(
                    "The width and the height are linked, so the card keeps its proportion.\n" +
                        "• The proportion is the relation between the width and the height of the card. " +
                        "The width is always $ratio times the height.\n" +
                        "• When you change one side, the other side changes by itself. The card is never stretched.\n" +
                        "• The original size is ${originalWidthMm.oneDecimal()} x ${originalHeightMm.oneDecimal()} mm. " +
                        "It is the size that lets 10 cards fit on one portrait A4 sheet, in 2 columns and 5 rows.",
                )
            }
        },
        state = rememberTooltipState(),
    ) {
        Icon(
            Icons.Default.Link,
            contentDescription = "The width and the height keep the card proportion",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A slider with its label on the left and the current value written inside the thumb. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    thumbText: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp).width(110.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            thumb = {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .widthIn(min = 32.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        thumbText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        )
    }
}

/** Lets the user choose how the ID cards are placed on A4 sheets. */
@Composable
fun IdCardsExportDialog(
    onExport: (IdCardSheetLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var layout by remember { mutableStateOf(IdCardSheetLayout()) }
    var widthText by remember { mutableStateOf(layout.cardWidthMm().oneDecimal()) }
    var heightText by remember { mutableStateOf(layout.cardHeightMm().oneDecimal()) }
    val maxScale = layout.maxScalePercent()
    val minScale = IdCardSheetLayout.MIN_SCALE_PERCENT
    val capacity = layout.capacity()
    val effective = layout.copy(cardsPerSheet = layout.cardsPerSheet.coerceIn(1, capacity.coerceAtLeast(1)))
    val minWidthMm = IdCardSheetLayout(scalePercent = minScale).cardWidthMm()
    val maxWidthMm = IdCardSheetLayout(scalePercent = maxScale).cardWidthMm()
    val minHeightMm = IdCardSheetLayout(scalePercent = minScale).cardHeightMm()
    val maxHeightMm = IdCardSheetLayout(scalePercent = maxScale).cardHeightMm()

    fun applyScale(scale: Double, updateWidthText: Boolean = true, updateHeightText: Boolean = true) {
        layout = layout.copy(scalePercent = scale.coerceIn(minScale, maxScale))
        if (updateWidthText) widthText = layout.cardWidthMm().oneDecimal()
        if (updateHeightText) heightText = layout.cardHeightMm().oneDecimal()
    }

    fun applyOrientation(landscape: Boolean) {
        layout = layout.copy(landscape = landscape)
        applyScale(layout.scalePercent)
    }

    val widthValue = widthText.replace(',', '.').toDoubleOrNull()
    val heightValue = heightText.replace(',', '.').toDoubleOrNull()
    val widthInvalid = widthValue == null || widthValue < minWidthMm - 0.05 || widthValue > maxWidthMm + 0.05
    val heightInvalid = heightValue == null || heightValue < minHeightMm - 0.05 || heightValue > maxHeightMm + 0.05

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Create ID cards")
                FocusedTextButton(
                    onClick = {
                        layout = IdCardSheetLayout()
                        widthText = layout.cardWidthMm().oneDecimal()
                        heightText = layout.cardHeightMm().oneDecimal()
                    },
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Restore settings", modifier = Modifier.padding(start = 6.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (capacity > 1) {
                    LabeledSlider(
                        icon = Icons.Default.GridView,
                        label = "Cards per sheet",
                        value = effective.cardsPerSheet.toFloat(),
                        onValueChange = { layout = layout.copy(cardsPerSheet = it.roundToInt()) },
                        valueRange = 1f..capacity.toFloat(),
                        steps = capacity - 2,
                        thumbText = effective.cardsPerSheet.toString(),
                    )
                }
                LabeledSlider(
                    icon = Icons.Default.AspectRatio,
                    label = "Card size",
                    value = layout.scalePercent.toFloat(),
                    onValueChange = { applyScale((it * 10).roundToLong() / 10.0) },
                    valueRange = minScale.toFloat()..maxScale.toFloat(),
                    steps = 0,
                    thumbText = "${layout.scalePercent.roundToInt()}%",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(20.dp))
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { text ->
                            widthText = text
                            val mm = text.replace(',', '.').toDoubleOrNull()
                            if (mm != null && mm >= minWidthMm - 0.05 && mm <= maxWidthMm + 0.05) {
                                applyScale(mmToScalePercent(mm), updateWidthText = false)
                            }
                        },
                        label = { Text("Width (mm)") },
                        singleLine = true,
                        isError = widthInvalid,
                        supportingText = if (widthInvalid) {
                            { Text("Use ${minWidthMm.oneDecimal()} to ${maxWidthMm.oneDecimal()}.") }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ProportionLinkIcon(
                        originalWidthMm = IdCardSheetLayout().cardWidthMm(),
                        originalHeightMm = IdCardSheetLayout().cardHeightMm(),
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { text ->
                            heightText = text
                            val mm = text.replace(',', '.').toDoubleOrNull()
                            if (mm != null && mm >= minHeightMm - 0.05 && mm <= maxHeightMm + 0.05) {
                                applyScale(mmHeightToScalePercent(mm), updateHeightText = false)
                            }
                        },
                        label = { Text("Height (mm)") },
                        singleLine = true,
                        isError = heightInvalid,
                        supportingText = if (heightInvalid) {
                            { Text("Use ${minHeightMm.oneDecimal()} to ${maxHeightMm.oneDecimal()}.") }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        FilledTonalIconToggleButton(
                            checked = !layout.landscape,
                            onCheckedChange = { applyOrientation(false) },
                        ) {
                            Icon(Icons.Default.CropPortrait, contentDescription = "Portrait")
                        }
                        FilledTonalIconToggleButton(
                            checked = layout.landscape,
                            onCheckedChange = { applyOrientation(true) },
                        ) {
                            Icon(Icons.Default.CropLandscape, contentDescription = "Landscape")
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    SheetSketch(effective, title = "Front sheet", back = false)
                    SheetSketch(effective, title = "Back sheet", back = true)
                }
                // The checkboxes use a smaller touch target, so the two lines stay close to each other.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = layout.cutMarks,
                                onCheckedChange = { layout = layout.copy(cutMarks = it) },
                            )
                            Text("Print cut marks around the cards.")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = layout.doubleSided,
                                onCheckedChange = { layout = layout.copy(doubleSided = it) },
                            )
                            Text("Arrange the cards for printing on both sides of the paper.")
                        }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                enabled = !widthInvalid && !heightInvalid,
                onClick = { onExport(effective) },
            ) {
                Text("Create PDF")
            }
        },
        dismissButton = {
            FocusedTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Draws a rough A4 sheet with the card grid. It is only a sketch to give an idea of the result.
 * Each card shows the number of its player in the list, so the arrangement of the back sheet is clear.
 */
@Composable
private fun SheetSketch(layout: IdCardSheetLayout, title: String, back: Boolean) {
    val grid = layout.grid()
    val outline = MaterialTheme.colorScheme.outline
    val cardFill = MaterialTheme.colorScheme.primary.copy(alpha = if (back) 0.12f else 0.25f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val sketchHeight = 160.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .height(sketchHeight)
                .width(sketchHeight * (layout.pageWidthPt / layout.pageHeightPt).toFloat())
                .padding(1.dp),
        ) {
            val k = size.height / layout.pageHeightPt.toFloat()
            drawRect(Color.White, size = size)
            drawRect(outline, size = size, style = Stroke(width = 1.5f))
            if (grid == null) return@Canvas
            val cardW = (IdCardSheetLayout.CARD_WIDTH_PT * grid.scale).toFloat() * k
            val cardH = (IdCardSheetLayout.CARD_HEIGHT_PT * grid.scale).toFloat() * k
            val left = (size.width - cardW * grid.columns) / 2f
            val top = (size.height - cardH * grid.rows) / 2f
            val shown = layout.cardsPerSheet.coerceAtMost(grid.columns * grid.rows)
            val numberStyle = TextStyle(fontSize = (cardH * 0.45f).toSp(), color = Color.Black)
            for (index in 0 until shown) {
                // The back sheet is mirrored in the same way as the generated PDF file.
                val cell = layout.cell(index, back) ?: continue
                val origin = Offset(left + cell.column * cardW, top + cell.row * cardH)
                drawRect(cardFill, topLeft = origin, size = Size(cardW, cardH))
                drawRect(outline, topLeft = origin, size = Size(cardW, cardH), style = Stroke(width = 1f))
                val number = textMeasurer.measure((index + 1).toString(), numberStyle)
                drawText(
                    number,
                    topLeft = Offset(
                        origin.x + (cardW - number.size.width) / 2f,
                        origin.y + (cardH - number.size.height) / 2f,
                    ),
                )
            }
            if (layout.cutMarks && !back) {
                val length = 8f * k
                val gap = 3f * k
                val right = left + cardW * grid.columns
                val bottom = top + cardH * grid.rows
                for (column in 0..grid.columns) {
                    val x = left + cardW * column
                    drawLine(Color.Black, Offset(x, top - gap), Offset(x, top - gap - length), 1f)
                    drawLine(Color.Black, Offset(x, bottom + gap), Offset(x, bottom + gap + length), 1f)
                }
                for (row in 0..grid.rows) {
                    val y = top + cardH * row
                    drawLine(Color.Black, Offset(left - gap, y), Offset(left - gap - length, y), 1f)
                    drawLine(Color.Black, Offset(right + gap, y), Offset(right + gap + length, y), 1f)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}
