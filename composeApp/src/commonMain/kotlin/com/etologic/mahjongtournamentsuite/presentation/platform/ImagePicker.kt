package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import kotlin.io.encoding.Base64

data class SelectedImage(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val dataUrl: String
        get() = "data:$contentType;base64,${Base64.Default.encode(bytes)}"
}

class ImagePicker internal constructor(
    private val launchAction: () -> Unit,
) {
    fun launch() = launchAction()
}

@Composable
expect fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
): ImagePicker

internal fun selectedImageFromBase64(
    fileName: String,
    contentType: String,
    base64Data: String,
): SelectedImage = SelectedImage(
    fileName = fileName,
    contentType = contentType.ifBlank { "image/jpeg" },
    bytes = Base64.Default.decode(base64Data),
)
