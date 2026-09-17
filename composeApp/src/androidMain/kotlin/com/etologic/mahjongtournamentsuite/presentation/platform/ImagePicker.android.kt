package com.etologic.mahjongtournamentsuite.presentation.platform

import android.provider.OpenableColumns
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
