package com.etologic.mahjongtournamentsuite.player

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

internal actual fun ImageBitmap.encodePng(): ByteArray {
    // A bitmap drawn by the GPU cannot be read directly, so it is copied to memory first.
    val bitmap = asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    return ByteArrayOutputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "The image cannot be encoded." }
        out.toByteArray()
    }
}

/**
 * The share sheet of Android with the PNG. The image is kept in the cache of the app, and the FileProvider of the
 * manifest of playerApp (the one of the Green Book, folder "shared/") lets the chosen app read it.
 */
@Composable
internal actual fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit {
    val context = LocalContext.current
    val currentOnError = rememberUpdatedState(onError)
    return remember(context) {
        { fileName, png, title ->
            runCatching {
                val folder = File(context.cacheDir, SHARED_IMAGES_FOLDER).apply { mkdirs() }
                // Only the last image is kept.
                folder.listFiles()?.forEach { it.delete() }
                val file = File(folder, fileName).apply { writeBytes(png) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.greenbook", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(send, title)
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }.onFailure { currentOnError.value() }
        }
    }
}

private const val SHARED_IMAGES_FOLDER = "shared"
