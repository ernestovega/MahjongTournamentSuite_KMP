package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.io.encoding.Base64

data class SelectedImage(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val dataUrl: String
        get() = "data:$contentType;base64,${Base64.Default.encode(bytes)}"
}

data class SquareImageCrop(
    val zoom: Float,
    val horizontalPosition: Float,
    val verticalPosition: Float,
)

class ImagePicker internal constructor(
    private val launchAction: () -> Unit,
) {
    fun launch() = launchAction()
}

/**
 * Tests give here the image that a picker returns, in place of the file chooser of the system, which a test cannot
 * use. Null (the default) opens the chooser. Only the desktop picker reads it, because the UI tests run there.
 */
val LocalPickedImageForTests = staticCompositionLocalOf<(() -> SelectedImage)?> { null }

@Composable
expect fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
): ImagePicker

expect fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
)

internal fun selectedImageFromBase64(
    fileName: String,
    contentType: String,
    base64Data: String,
): SelectedImage = SelectedImage(
    fileName = fileName,
    contentType = contentType.ifBlank { "image/jpeg" },
    bytes = Base64.Default.decode(base64Data),
)
