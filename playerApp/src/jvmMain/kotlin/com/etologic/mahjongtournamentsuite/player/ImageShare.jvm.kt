package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import javax.swing.JFileChooser
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodePng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("The image cannot be encoded.")

// The JVM target only runs the tests. A computer has no share sheet, so it saves the image where the user chooses.
@Composable
internal actual fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        { fileName, png, title ->
            val chooser = JFileChooser().apply {
                dialogTitle = title
                selectedFile = File(fileName)
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val chosen = chooser.selectedFile
                    val target = if (chosen.name.endsWith(".png", ignoreCase = true)) chosen else File(chosen.path + ".png")
                    target.writeBytes(png)
                }.onFailure { currentOnError.value() }
            }
        }
    }
}
