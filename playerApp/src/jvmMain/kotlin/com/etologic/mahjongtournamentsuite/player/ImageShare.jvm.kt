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

/** The save dialog of a shared image: the chosen file, or null when it was cancelled. Tests replace it. */
internal var chooseImageFile: (title: String, fileName: String) -> File? = { title, fileName ->
    val chooser = JFileChooser().apply {
        dialogTitle = title
        selectedFile = File(fileName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

// The JVM target only runs the tests. A computer has no share sheet, so it saves the image where the user chooses.
@Composable
internal actual fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        share@{ fileName, png, title ->
            val chosen = chooseImageFile(title, fileName) ?: return@share
            runCatching {
                val target = if (chosen.name.endsWith(".png", ignoreCase = true)) chosen else File(chosen.path + ".png")
                target.writeBytes(png)
            }.onFailure { currentOnError.value() }
        }
    }
}
