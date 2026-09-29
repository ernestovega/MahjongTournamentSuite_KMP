package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
internal expect fun mondayFirstCalendarLocale(): CalendarLocale

@Composable
fun TournamentColorField(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onPreviewClick: () -> Unit,
    fieldModifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val previewColor = value.toTournamentColorOrNull() ?: DefaultTournamentColor

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusedIconButton(
            enabled = enabled,
            onClick = onPreviewClick,
            buttonModifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Choose tournament color" },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(previewColor, MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(7)) },
            modifier = fieldModifier.weight(1f),
            label = { Text("Tournament color") },
            placeholder = { Text("#RRGGBB") },
            enabled = enabled,
            singleLine = true,
            isError = isError,
            supportingText = errorMessage?.let { message -> { Text(message) } },
        )
    }
}

@Composable
fun TournamentColorPickerDialog(
    initialColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        initialColor.toRgbColorOrNull()?.toHsvColor() ?: DefaultTournamentHsv
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv.saturation) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv.value) }
    var hexValue by remember(initialColor) {
        val hexText = initialHsv.toRgbColor().toHexColor()
        mutableStateOf(TextFieldValue(hexText, selection = TextRange(0, hexText.length)))
    }
    var isColorFieldFocused by remember { mutableStateOf(false) }
    val colorFieldFocusRequester = remember { FocusRequester() }
    val wheelFocusRequester = remember { FocusRequester() }

    fun updateFromHsv(newHue: Float, newSaturation: Float, newBrightness: Float) {
        hue = normalizeHue(newHue)
        saturation = newSaturation.coerceIn(0f, 1f)
        brightness = newBrightness.coerceIn(0f, 1f)
        val hexText = HsvColor(hue, saturation, brightness).toRgbColor().toHexColor()
        hexValue = TextFieldValue(hexText, selection = TextRange(hexText.length))
    }

    LaunchedEffect(Unit) {
        colorFieldFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text("Choose tournament color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VisualColorWheel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    focusRequester = wheelFocusRequester,
                    onColorChanged = { newHue, newSaturation ->
                        updateFromHsv(newHue, newSaturation, brightness)
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Brightness", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = brightness,
                        onValueChange = { updateFromHsv(hue, saturation, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            HsvColor(hue, saturation, brightness).toRgbColor().toComposeColor(),
                            MaterialTheme.shapes.small,
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                )
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { newValue ->
                        val limitedText = newValue.text.take(7)
                        hexValue = newValue.copy(
                            text = limitedText,
                            selection = TextRange(
                                start = newValue.selection.start.coerceAtMost(limitedText.length),
                                end = newValue.selection.end.coerceAtMost(limitedText.length),
                            ),
                        )
                        limitedText.toRgbColorOrNull()?.toHsvColor()?.let { hsv ->
                            hue = hsv.hue
                            saturation = hsv.saturation
                            brightness = hsv.value
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(colorFieldFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !isColorFieldFocused) {
                                hexValue = hexValue.copy(
                                    selection = TextRange(0, hexValue.text.length),
                                )
                            }
                            isColorFieldFocused = focusState.isFocused
                        },
                    label = { Text("Tournament color") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    isError = hexValue.text.toRgbColorOrNull() == null,
                )
            }
        },
        confirmButton = {
            FocusedButton(
                enabled = hexValue.text.toRgbColorOrNull() != null,
                onClick = { onColorSelected(hexValue.text.uppercase()) },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            FocusedTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun VisualColorWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    focusRequester: FocusRequester,
    onColorChanged: (hue: Float, saturation: Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun selectAt(position: Offset) {
        if (canvasSize == IntSize.Zero) return
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val radius = min(canvasSize.width, canvasSize.height) / 2f
        val selectedSaturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
        val selectedHue = normalizeHue(atan2(dy, dx) * 180f / PI.toFloat())
        onColorChanged(selectedHue, selectedSaturation)
    }

    FocusHighlightContainer(
        modifier = Modifier,
        interactionSource = interactionSource,
    ) {
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .semantics {
                    contentDescription = "Color wheel. Use arrows or select a point."
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onColorChanged(normalizeHue(hue - 2f), saturation)
                        Key.DirectionRight -> onColorChanged(normalizeHue(hue + 2f), saturation)
                        Key.DirectionUp -> onColorChanged(hue, (saturation + 0.02f).coerceAtMost(1f))
                        Key.DirectionDown -> onColorChanged(hue, (saturation - 0.02f).coerceAtLeast(0f))
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) { detectTapGestures(onTap = ::selectAt) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = ::selectAt,
                        onDrag = { change, _ ->
                            change.consume()
                            selectAt(change.position)
                        },
                    )
                },
        ) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                    center = center,
                ),
                radius = radius,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
            if (brightness < 1f) {
                drawCircle(Color.Black.copy(alpha = 1f - brightness), radius = radius)
            }

            val angle = hue * PI.toFloat() / 180f
            val markerCenter = Offset(
                x = center.x + cos(angle) * saturation * radius,
                y = center.y + sin(angle) * saturation * radius,
            )
            drawCircle(Color.White, radius = 9.dp.toPx(), center = markerCenter)
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = markerCenter, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun TournamentLogoPreview(
    model: Any?,
    onImageInfo: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Text(
                "No logo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = "Tournament logo preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                onSuccess = { state ->
                    val image = state.result.image
                    val dimensions = if (image.width > 0 && image.height > 0) {
                        "${image.width} × ${image.height} px"
                    } else {
                        "Unknown dimensions"
                    }
                    onImageInfo("$dimensions · ${formatByteSize(image.size)} decoded")
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDatePickerDialog(
    selectedDisplayDate: String,
    minimumDisplayDate: String? = null,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit,
) {
    val initialMillis = displayDateToEpochMillis(selectedDisplayDate)
    val minimumMillis = minimumDisplayDate?.let(::displayDateToEpochMillis)
    val selectableDates = remember(minimumMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minimumMillis == null || utcTimeMillis >= minimumMillis
        }
    }
    val locale = remember { mondayFirstCalendarLocale() }
    val state = remember(initialMillis, minimumMillis) {
        DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initialMillis?.coerceAtLeast(minimumMillis ?: Long.MIN_VALUE),
            selectableDates = selectableDates,
        )
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FocusedTextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(epochMillisToDisplayDate(it)) }
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            FocusedTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDateRangePickerDialog(
    selectedStartDisplayDate: String,
    selectedEndDisplayDate: String,
    onDismiss: () -> Unit,
    onDatesSelected: (startDisplayDate: String, endDisplayDate: String) -> Unit,
) {
    val initialStartMillis = displayDateToEpochMillis(selectedStartDisplayDate)
    val initialEndMillis = displayDateToEpochMillis(selectedEndDisplayDate)
    val locale = remember { mondayFirstCalendarLocale() }
    val pickerColors = DatePickerDefaults.colors()
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    val state = remember(initialStartMillis, initialEndMillis) {
        DateRangePickerState(
            locale = locale,
            initialSelectedStartDateMillis = initialStartMillis,
            initialSelectedEndDateMillis = initialEndMillis,
        )
    }

    // This picker needs added height to show the next month. The 420 dp cap keeps day spacing compact.
    // Outer padding keeps the custom dialog clear of small window edges.
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .heightIn(max = 720.dp),
            shape = DatePickerDefaults.shape,
            color = pickerColors.containerColor,
            tonalElevation = DatePickerDefaults.TonalElevation,
        ) {
            Column {
                DateRangePicker(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = pickerColors,
                    title = {
                        DateRangePickerDefaults.DateRangePickerTitle(
                            displayMode = state.displayMode,
                            modifier = Modifier.padding(
                                start = 24.dp,
                                top = 24.dp,
                                end = 24.dp,
                                bottom = 8.dp,
                            ),
                            contentColor = pickerColors.titleContentColor,
                        )
                    },
                    headline = {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                            DateRangePickerDefaults.DateRangePickerHeadline(
                                selectedStartDateMillis = state.selectedStartDateMillis,
                                selectedEndDateMillis = state.selectedEndDateMillis,
                                displayMode = state.displayMode,
                                dateFormatter = dateFormatter,
                                modifier = Modifier.padding(start = 24.dp, end = 8.dp, bottom = 16.dp),
                                contentColor = pickerColors.headlineContentColor,
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FocusedTextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    FocusedTextButton(
                        enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                        onClick = {
                            val startMillis = state.selectedStartDateMillis
                            val endMillis = state.selectedEndDateMillis
                            if (startMillis != null && endMillis != null) {
                                onDatesSelected(
                                    epochMillisToDisplayDate(startMillis),
                                    epochMillisToDisplayDate(endMillis),
                                )
                            }
                        },
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

fun String?.toDisplayTournamentDate(): String {
    val isoDate = this?.trim().orEmpty()
    if (!TournamentDateRangeValidator.isValidDate(isoDate)) return ""
    return "${isoDate.substring(8, 10)}/${isoDate.substring(5, 7)}/${isoDate.substring(0, 4)}"
}

fun String.toIsoTournamentDateOrNull(): String? {
    val displayDate = trim()
    if (!Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(displayDate)) return null
    val isoDate = buildString {
        append(displayDate.substring(6, 10))
        append('-')
        append(displayDate.substring(3, 5))
        append('-')
        append(displayDate.substring(0, 2))
    }
    return isoDate.takeIf(TournamentDateRangeValidator::isValidDate)
}

fun adjustedEndDate(startDisplayDate: String, endDisplayDate: String): String {
    val startIso = startDisplayDate.toIsoTournamentDateOrNull() ?: return endDisplayDate
    if (endDisplayDate.isBlank()) return startDisplayDate
    val endIso = endDisplayDate.toIsoTournamentDateOrNull()
    return if (endIso != null && endIso < startIso) startDisplayDate else endDisplayDate
}

fun formatByteSize(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${(bytes + 512L) / 1_024L} KB"
    else -> {
        val tenths = (bytes * 10L + 524_288L) / 1_048_576L
        "${tenths / 10L}.${tenths % 10L} MB"
    }
}

internal fun String.toTournamentColorOrNull(): Color? = toRgbColorOrNull()?.toComposeColor()

private fun displayDateToEpochMillis(value: String): Long? = runCatching {
    val isoDate = value.toIsoTournamentDateOrNull() ?: return@runCatching null
    LocalDate.parse(isoDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}.getOrNull()

private fun epochMillisToDisplayDate(value: Long): String =
    Instant.fromEpochMilliseconds(value)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
        .toDisplayTournamentDate()

private data class RgbColor(val red: Int, val green: Int, val blue: Int) {
    fun toHexColor(): String = buildString {
        append('#')
        append(red.toString(16).uppercase().padStart(2, '0'))
        append(green.toString(16).uppercase().padStart(2, '0'))
        append(blue.toString(16).uppercase().padStart(2, '0'))
    }

    fun toComposeColor(): Color = Color(red / 255f, green / 255f, blue / 255f)

    fun toHsvColor(): HsvColor {
        val redValue = red / 255f
        val greenValue = green / 255f
        val blueValue = blue / 255f
        val maxValue = maxOf(redValue, greenValue, blueValue)
        val minValue = minOf(redValue, greenValue, blueValue)
        val delta = maxValue - minValue
        val hue = when {
            delta == 0f -> 0f
            maxValue == redValue -> 60f * (((greenValue - blueValue) / delta) % 6f)
            maxValue == greenValue -> 60f * (((blueValue - redValue) / delta) + 2f)
            else -> 60f * (((redValue - greenValue) / delta) + 4f)
        }
        val saturation = if (maxValue == 0f) 0f else delta / maxValue
        return HsvColor(normalizeHue(hue), saturation, maxValue)
    }
}

private data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toRgbColor(): RgbColor {
        val chroma = value * saturation
        val sector = hue / 60f
        val second = chroma * (1f - kotlin.math.abs((sector % 2f) - 1f))
        val (redPart, greenPart, bluePart) = when (sector.toInt().coerceIn(0, 5)) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val match = value - chroma
        return RgbColor(
            red = ((redPart + match) * 255f).roundToInt().coerceIn(0, 255),
            green = ((greenPart + match) * 255f).roundToInt().coerceIn(0, 255),
            blue = ((bluePart + match) * 255f).roundToInt().coerceIn(0, 255),
        )
    }
}

private fun String.toRgbColorOrNull(): RgbColor? {
    val normalized = trim().uppercase()
    if (!Regex("^#[0-9A-F]{6}$").matches(normalized)) return null
    return RgbColor(
        red = normalized.substring(1, 3).toInt(16),
        green = normalized.substring(3, 5).toInt(16),
        blue = normalized.substring(5, 7).toInt(16),
    )
}

private fun normalizeHue(value: Float): Float = ((value % 360f) + 360f) % 360f

private val DefaultTournamentColor = Color(red = 2 / 255f, green = 177 / 255f, blue = 107 / 255f)
private val DefaultTournamentHsv = RgbColor(2, 177, 107).toHsvColor()
