package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.etologic.mahjongtournamentsuite.presentation.platform.IosViewControllers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodePng(): ByteArray = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("The image cannot be encoded.")

/** The share sheet of iOS with the PNG: Photos, Messages, WhatsApp... */
@Composable
internal actual fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        { fileName, png, _ ->
            runCatching { IosViewControllers.shareFile(fileName, png) }.onFailure { currentOnError.value() }
        }
    }
}
