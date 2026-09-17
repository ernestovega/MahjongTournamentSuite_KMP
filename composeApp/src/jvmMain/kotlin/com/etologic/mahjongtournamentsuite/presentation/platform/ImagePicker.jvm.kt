package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.nio.file.Files
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

private fun contentTypeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
