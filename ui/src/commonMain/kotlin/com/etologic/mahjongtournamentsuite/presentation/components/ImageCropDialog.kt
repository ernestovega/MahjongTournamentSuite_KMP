package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.SquareImageCrop
import com.etologic.mahjongtournamentsuite.presentation.platform.cropSelectedImage
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_cancel
import mahjongtournamentsuite.ui.generated.resources.crop_left_right
import mahjongtournamentsuite.ui.generated.resources.crop_preview
import mahjongtournamentsuite.ui.generated.resources.crop_up_down
import mahjongtournamentsuite.ui.generated.resources.crop_use
import mahjongtournamentsuite.ui.generated.resources.crop_zoom
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** Dialog that crops an image to a square. The circle in the preview shows the area that the app uses. */
@Composable
fun ImageCropDialog(
    image: SelectedImage,
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onCropped: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
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

    TrackedDialog("ImageCrop") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isCropping) onDismiss() },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    CircularCropPreview(
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
                        label = stringResource(Res.string.crop_zoom),
                        value = zoom,
                        valueRange = 1f..3f,
                        onValueChange = { zoom = it },
                        modifier = Modifier.focusRequester(zoomFocusRequester),
                    )
                    CropSlider(
                        label = stringResource(Res.string.crop_left_right),
                        value = horizontalPosition,
                        valueRange = -1f..1f,
                        onValueChange = { horizontalPosition = it },
                    )
                    CropSlider(
                        label = stringResource(Res.string.crop_up_down),
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
                        Text(stringResource(Res.string.crop_use))
                    }
                }
            },
            dismissButton = {
                FocusedTextButton(enabled = !isCropping, onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CircularCropPreview(
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
            contentDescription = stringResource(Res.string.crop_preview),
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
