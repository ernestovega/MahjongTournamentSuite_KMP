package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.nio.file.Files
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
): ImagePicker {
    val currentOnImageSelected = rememberUpdatedState(onImageSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        ImagePicker {
            val chooser = JFileChooser().apply {
                dialogTitle = "Choose player photo"
                fileFilter = FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "webp", "gif")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val file = chooser.selectedFile
                    SelectedImage(
                        fileName = file.name,
                        contentType = Files.probeContentType(file.toPath()) ?: contentTypeFromFileName(file.name),
                        bytes = file.readBytes(),
                    )
                }.onSuccess(currentOnImageSelected.value)
                    .onFailure { currentOnError.value(it.message ?: "The photo could not be read.") }
            }
        }
    }
}

actual fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
) {
    runCatching {
        val source = ImageIO.read(ByteArrayInputStream(image.bytes))
            ?: error("The selected logo could not be decoded.")
        val cropSize = minOf(source.width, source.height) / crop.zoom.coerceIn(1f, 3f)
        val side = cropSize.toInt().coerceAtLeast(1)
        val left = ((source.width - cropSize) * (crop.horizontalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.width - side)
        val top = ((source.height - cropSize) * (crop.verticalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.height - side)
        val outputSize = minOf(512, side)
        val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(source, 0, 0, outputSize, outputSize, left, top, left + side, top + side, null)
        } finally {
            graphics.dispose()
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            check(ImageIO.write(output, "png", stream))
            stream.toByteArray()
        }
        SelectedImage(
            fileName = image.fileName.substringBeforeLast('.', image.fileName) + "-cropped.png",
            contentType = "image/png",
            bytes = bytes,
        )
    }.onSuccess(onCropped)
        .onFailure { onError(it.message ?: "The logo could not be cropped.") }
}

private fun contentTypeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
