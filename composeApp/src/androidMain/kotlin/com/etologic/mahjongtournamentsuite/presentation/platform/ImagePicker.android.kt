package com.etologic.mahjongtournamentsuite.presentation.platform

import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
): ImagePicker {
    val context = AndroidPlatformContext.requireContext()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val resolver = context.contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "player-photo"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("The selected photo could not be opened.")
            SelectedImage(
                fileName = fileName,
                contentType = resolver.getType(uri) ?: "image/jpeg",
                bytes = bytes,
            )
        }.onSuccess(onImageSelected)
            .onFailure { onError(it.message ?: "The photo could not be read.") }
    }
    return remember(launcher) { ImagePicker { launcher.launch("image/*") } }
}

actual fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
) {
    runCatching {
        val source = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: error("The selected logo could not be decoded.")
        val cropSize = minOf(source.width, source.height) / crop.zoom.coerceIn(1f, 3f)
        val left = ((source.width - cropSize) * (crop.horizontalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.width - cropSize.toInt())
        val top = ((source.height - cropSize) * (crop.verticalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.height - cropSize.toInt())
        val side = cropSize.toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val outputSize = minOf(512, side)
        val output = if (side == outputSize) cropped else {
            Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
        }
        val bytes = java.io.ByteArrayOutputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
            stream.toByteArray()
        }
        if (output !== cropped) output.recycle()
        cropped.recycle()
        source.recycle()
        SelectedImage(
            fileName = image.fileName.substringBeforeLast('.', image.fileName) + "-cropped.png",
            contentType = "image/png",
            bytes = bytes,
        )
    }.onSuccess(onCropped)
        .onFailure { onError(it.message ?: "The logo could not be cropped.") }
}
