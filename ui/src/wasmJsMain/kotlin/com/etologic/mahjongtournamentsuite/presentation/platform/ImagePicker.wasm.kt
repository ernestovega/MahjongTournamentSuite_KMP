package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
): ImagePicker {
    val currentOnImageSelected = rememberUpdatedState(onImageSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        ImagePicker {
            launchBrowserImagePicker(
                onSelected = { fileName, contentType, base64Data ->
                    runCatching { selectedImageFromBase64(fileName, contentType, base64Data) }
                        .onSuccess(currentOnImageSelected.value)
                        .onFailure { currentOnError.value(it.toImageUiText(ImageReadError)) }
                },
                onError = { currentOnError.value(imageErrorFromCode(it)) },
            )
        }
    }
}


actual fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
) {
    cropBrowserImage(
        dataUrl = image.dataUrl,
        zoom = crop.zoom.coerceIn(1f, 3f),
        horizontalPosition = crop.horizontalPosition.coerceIn(-1f, 1f),
        verticalPosition = crop.verticalPosition.coerceIn(-1f, 1f),
        onCropped = { base64Data ->
            runCatching {
                selectedImageFromBase64(
                    fileName = image.fileName.substringBeforeLast('.', image.fileName) + "-cropped.png",
                    contentType = "image/png",
                    base64Data = base64Data,
                )
            }.onSuccess(onCropped).onFailure { onError(it.toImageUiText(ImageCropError)) }
        },
        onError = { onError(imageErrorFromCode(it)) },
    )
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(dataUrl, zoom, horizontalPosition, verticalPosition, onCropped, onError) => {
        const image = new Image();
        image.onerror = () => onError('decode');
        image.onload = () => {
            try {
                const cropSize = Math.min(image.naturalWidth, image.naturalHeight) / zoom;
                const left = (image.naturalWidth - cropSize) * (horizontalPosition + 1) / 2;
                const top = (image.naturalHeight - cropSize) * (verticalPosition + 1) / 2;
                const outputSize = Math.min(512, Math.max(1, Math.floor(cropSize)));
                const canvas = document.createElement('canvas');
                canvas.width = outputSize;
                canvas.height = outputSize;
                const context = canvas.getContext('2d');
                context.imageSmoothingEnabled = true;
                context.imageSmoothingQuality = 'high';
                context.drawImage(image, left, top, cropSize, cropSize, 0, 0, outputSize, outputSize);
                onCropped(canvas.toDataURL('image/png').split(',')[1]);
            } catch (error) {
                onError('crop');
            }
        };
        image.src = dataUrl;
    }""",
)
private external fun cropBrowserImage(
    dataUrl: String,
    zoom: Float,
    horizontalPosition: Float,
    verticalPosition: Float,
    onCropped: (String) -> Unit,
    onError: (String) -> Unit,
)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onSelected, onError) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onerror = () => onError('read');
            reader.onload = () => {
                const result = String(reader.result || '');
                const separator = result.indexOf(',');
                if (separator < 0) {
                    onError('invalid');
                    return;
                }
                onSelected(file.name, file.type || 'image/jpeg', result.substring(separator + 1));
            };
            reader.readAsDataURL(file);
        };
        input.click();
    }""",
)
private external fun launchBrowserImagePicker(
    onSelected: (String, String, String) -> Unit,
    onError: (String) -> Unit,
)
