package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.image_chooser_filter
import mahjongtournamentsuite.ui.generated.resources.image_chooser_title
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
): ImagePicker {
    val currentOnImageSelected = rememberUpdatedState(onImageSelected)
    val currentOnError = rememberUpdatedState(onError)
    val chooserTitle = stringResource(Res.string.image_chooser_title)
    val chooserFilter = stringResource(Res.string.image_chooser_filter)
    return remember(chooserTitle, chooserFilter) {
        ImagePicker {
            val chooser = JFileChooser().apply {
                dialogTitle = chooserTitle
                fileFilter = FileNameExtensionFilter(chooserFilter, "jpg", "jpeg", "png", "webp", "gif")
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
                    .onFailure { currentOnError.value(it.toImageUiText(ImageReadError)) }
            }
        }
    }
}

actual fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
) {
    runCatching {
        val source = ImageIO.read(ByteArrayInputStream(image.bytes))
            ?: throw ImageException(ImageDecodeError)
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
        .onFailure { onError(it.toImageUiText(ImageCropError)) }
}

private fun contentTypeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
