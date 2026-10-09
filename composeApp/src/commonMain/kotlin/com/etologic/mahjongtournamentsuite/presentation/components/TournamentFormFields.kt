package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.TimePickerState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.SquareImageCrop
import com.etologic.mahjongtournamentsuite.presentation.platform.cropSelectedImage
import com.etologic.mahjongtournamentsuite.presentation.theme.GangOfThreeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
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
            onValueChange = { onValueChange(it.toHexColorInput()) },
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
                        val limitedText = newValue.text.toHexColorInput()
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

@Composable
fun TournamentLogoCropDialog(
    image: SelectedImage,
    onDismiss: () -> Unit,
    onCropped: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var horizontalPosition by remember(image) { mutableFloatStateOf(0f) }
    var verticalPosition by remember(image) { mutableFloatStateOf(0f) }
    var imageSize by remember(image) { mutableStateOf(IntSize.Zero) }
    var isCropping by remember(image) { mutableStateOf(false) }
    val zoomFocusRequester = remember { FocusRequester() }

    LaunchedEffect(image) {
        zoomFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = { if (!isCropping) onDismiss() },
        title = { Text("Crop tournament logo") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Drag the image or use the controls. The circle shows the ID card logo area.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                CircularLogoCropPreview(
                    model = image.dataUrl,
                    imageSize = imageSize,
                    zoom = zoom,
                    horizontalPosition = horizontalPosition,
                    verticalPosition = verticalPosition,
                    onImageSize = { imageSize = it },
                    onPositionChange = { horizontal, vertical ->
                        horizontalPosition = horizontal
                        verticalPosition = vertical
                    },
                )
                CropSlider(
                    label = "Zoom",
                    value = zoom,
                    valueRange = 1f..3f,
                    onValueChange = { zoom = it },
                    modifier = Modifier.focusRequester(zoomFocusRequester),
                )
                CropSlider(
                    label = "Left / right",
                    value = horizontalPosition,
                    valueRange = -1f..1f,
                    onValueChange = { horizontalPosition = it },
                )
                CropSlider(
                    label = "Up / down",
                    value = verticalPosition,
                    valueRange = -1f..1f,
                    onValueChange = { verticalPosition = it },
                )
            }
        },
        confirmButton = {
            FocusedButton(
                enabled = !isCropping && imageSize != IntSize.Zero,
                onClick = {
                    isCropping = true
                    cropSelectedImage(
                        image = image,
                        crop = SquareImageCrop(zoom, horizontalPosition, verticalPosition),
                        onCropped = onCropped,
                        onError = {
                            isCropping = false
                            onError(it)
                        },
                    )
                },
            ) {
                if (isCropping) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Use crop")
                }
            }
        },
        dismissButton = {
            FocusedTextButton(enabled = !isCropping, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CircularLogoCropPreview(
    model: Any,
    imageSize: IntSize,
    zoom: Float,
    horizontalPosition: Float,
    verticalPosition: Float,
    onImageSize: (IntSize) -> Unit,
    onPositionChange: (horizontal: Float, vertical: Float) -> Unit,
) {
    val viewport = 240.dp
    val minimumDimension = min(imageSize.width, imageSize.height).coerceAtLeast(1)
    val renderedWidth = viewport * imageSize.width.coerceAtLeast(1) / minimumDimension
    val renderedHeight = viewport * imageSize.height.coerceAtLeast(1) / minimumDimension
    val viewportPixels = with(androidx.compose.ui.platform.LocalDensity.current) { viewport.toPx() }
    val baseScale = viewportPixels / minimumDimension
    val sourceCropSize = minimumDimension / zoom
    val translationX = -horizontalPosition * (imageSize.width - sourceCropSize) / 2f * baseScale * zoom
    val translationY = -verticalPosition * (imageSize.height - sourceCropSize) / 2f * baseScale * zoom
    val maximumTranslationX = (imageSize.width - sourceCropSize) / 2f * baseScale * zoom
    val maximumTranslationY = (imageSize.height - sourceCropSize) / 2f * baseScale * zoom
    val currentHorizontalPosition by rememberUpdatedState(horizontalPosition)
    val currentVerticalPosition by rememberUpdatedState(verticalPosition)

    Box(
        modifier = Modifier
            .size(viewport)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .pointerInput(maximumTranslationX, maximumTranslationY) {
                var dragHorizontal = currentHorizontalPosition
                var dragVertical = currentVerticalPosition
                detectDragGestures(
                    onDragStart = {
                        dragHorizontal = currentHorizontalPosition
                        dragVertical = currentVerticalPosition
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (maximumTranslationX > 0f) {
                            dragHorizontal = (dragHorizontal - dragAmount.x / maximumTranslationX)
                                .coerceIn(-1f, 1f)
                        }
                        if (maximumTranslationY > 0f) {
                            dragVertical = (dragVertical - dragAmount.y / maximumTranslationY)
                                .coerceIn(-1f, 1f)
                        }
                        onPositionChange(dragHorizontal, dragVertical)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Logo crop preview",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .requiredSize(renderedWidth, renderedHeight)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    this.translationX = translationX
                    this.translationY = translationY
                },
            onSuccess = { state ->
                val loadedImage = state.result.image
                if (loadedImage.width > 0 && loadedImage.height > 0) {
                    onImageSize(IntSize(loadedImage.width, loadedImage.height))
                }
            },
        )
    }
}

@Composable
private fun CropSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

@Composable
fun TournamentLogoLibraryDialog(
    tournaments: List<Tournament>,
    onDismiss: () -> Unit,
    onSelect: (Tournament) -> Unit,
) {
    val logos = remember(tournaments) {
        tournaments.filter { !it.associationLogoUrl.isNullOrBlank() }
    }
    val listState = rememberLazyListState()
    val itemFocusRequesters = remember(logos.map { it.id }) { logos.map { FocusRequester() } }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(logos) {
        if (itemFocusRequesters.isNotEmpty()) {
            itemFocusRequesters.first().requestFocus()
        } else {
            cancelFocusRequester.requestFocus()
        }
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text("Reuse tournament logo") },
        text = {
            if (logos.isEmpty()) {
                Text("No saved tournament logos are available.")
            } else {
                LazyColumnWithScrollbar(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(logos, key = { _, tournament -> tournament.id }) { index, tournament ->
                        val previous = if (index == 0) cancelFocusRequester else itemFocusRequesters[index - 1]
                        val next = if (index == logos.lastIndex) cancelFocusRequester else itemFocusRequesters[index + 1]
                        FocusedButton(
                            onClick = { onSelect(tournament) },
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = itemFocusRequesters[index],
                            buttonModifier = Modifier
                                .fillMaxWidth()
                                .focusLoop(previous = previous, next = next),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            AsyncImage(
                                model = tournament.associationLogoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )

                            Spacer(Modifier.width(16.dp))

                            Text(
                                tournament.name,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            FocusedTextButton(
                onClick = onDismiss,
                focusRequester = cancelFocusRequester,
                buttonModifier = if (itemFocusRequesters.isEmpty()) {
                    Modifier.focusLoop(cancelFocusRequester, cancelFocusRequester)
                } else {
                    Modifier.focusLoop(itemFocusRequesters.last(), itemFocusRequesters.first())
                },
            ) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDatePickerDialog(
    selectedDisplayDate: String,
    minimumDisplayDate: String? = null,
    maximumDisplayDate: String? = null,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit,
) {
    val initialMillis = displayDateToEpochMillis(selectedDisplayDate)
    val minimumMillis = minimumDisplayDate?.let(::displayDateToEpochMillis)
    val maximumMillis = maximumDisplayDate
        ?.let(::displayDateToEpochMillis)
        ?.takeIf { minimumMillis == null || it >= minimumMillis }
    val selectableDates = remember(minimumMillis, maximumMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minimumMillis == null || utcTimeMillis >= minimumMillis) &&
                    (maximumMillis == null || utcTimeMillis <= maximumMillis)
        }
    }
    val locale = remember { mondayFirstCalendarLocale() }
    // Counts day clicks, including clicks on the day that is already selected.
    var dayClickCount by remember { mutableIntStateOf(0) }
    val state = remember(initialMillis, minimumMillis, maximumMillis) {
        val delegate = DatePickerState(
            locale = locale,
            initialSelectedDateMillis = (initialMillis ?: minimumMillis)?.coerceIn(
                minimumValue = minimumMillis ?: Long.MIN_VALUE,
                maximumValue = maximumMillis ?: Long.MAX_VALUE,
            ),
            selectableDates = selectableDates,
        )
        DayClickDatePickerState(delegate) { dayClickCount++ }
    }
    val currentOnDateSelected by rememberUpdatedState(onDateSelected)

    // Picking a day confirms it immediately. The OK button stays available.
    LaunchedEffect(state) {
        snapshotFlow { dayClickCount }
            .drop(1)
            .collect {
                state.selectedDateMillis?.let { currentOnDateSelected(epochMillisToDisplayDate(it)) }
            }
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
fun TournamentTimePickerDialog(
    selectedTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit,
) {
    val parsedTime = remember(selectedTime) { selectedTime.toPickerHourAndMinute() }
    val delegate = rememberTimePickerState(
        initialHour = parsedTime.first,
        initialMinute = parsedTime.second,
        is24Hour = true,
    )
    // Counts minute selections, including selecting the minute that is already set.
    var minuteSelectionCount by remember { mutableIntStateOf(0) }
    val state = remember(delegate) { MinuteSelectionTimePickerState(delegate) { minuteSelectionCount++ } }
    val currentOnTimeSelected by rememberUpdatedState(onTimeSelected)
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        confirmFocusRequester.requestFocus()
    }

    // Picking a minute confirms the time immediately. The OK button stays available.
    // The short delay lets a dial drag settle before confirming.
    LaunchedEffect(state) {
        snapshotFlow { minuteSelectionCount }
            .drop(1)
            .collectLatest {
                delay(350)
                currentOnTimeSelected(
                    "${state.hour.toString().padStart(2, '0')}:" +
                        state.minute.toString().padStart(2, '0'),
                )
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        title = { Text("Select time") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            FocusedTextButton(
                onClick = {
                    onTimeSelected(
                        "${state.hour.toString().padStart(2, '0')}:" +
                            state.minute.toString().padStart(2, '0'),
                    )
                },
                focusRequester = confirmFocusRequester,
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

/** Reports every day click made in the picker, even when the clicked day is already selected. */
@OptIn(ExperimentalMaterial3Api::class)
private class DayClickDatePickerState(
    private val delegate: DatePickerState,
    private val onDayClick: () -> Unit,
) : DatePickerState by delegate {
    override var selectedDateMillis: Long?
        get() = delegate.selectedDateMillis
        set(value) {
            delegate.selectedDateMillis = value
            // Typed dates in input mode must not confirm the dialog while the user is still typing.
            if (delegate.displayMode == DisplayMode.Picker) onDayClick()
        }
}

/** Reports every minute selection made on the dial, even when the minute is already set. */
@OptIn(ExperimentalMaterial3Api::class)
private class MinuteSelectionTimePickerState(
    private val delegate: TimePickerState,
    private val onMinuteSelected: () -> Unit,
) : TimePickerState by delegate {
    override var minuteInput: Int
        get() = delegate.minuteInput
        set(value) {
            delegate.minuteInput = value
            onMinuteSelected()
        }
}

private fun String.toPickerHourAndMinute(): Pair<Int, Int> {
    val parts = trim().split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val minute = parts.getOrNull(1)?.toIntOrNull()
    return if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
        hour to minute
    } else {
        9 to 0
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

/** Switch for a tournament option, with a label and an information tooltip. */
@Composable
fun TournamentOptionSwitch(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                interactionSource = interactionSource,
            )
            Text(text = label)
            InfoTooltipIcon(
                description = description,
                contentDescription = "Show $label information",
            )
        }
    }
}
