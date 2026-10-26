package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import com.etologic.mahjongtournamentsuite.presentation.util.UiText

@Composable
actual fun rememberSpreadsheetSaver(onError: (UiText) -> Unit): SpreadsheetSaver {
    val context = AndroidPlatformContext.requireContext()
    // The chooser returns the place of the file. The bytes wait here until it answers.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("The file cannot be opened.")
        }.onFailure { onError(SpreadsheetSaveError) }
    }
    return remember(launcher) {
        SpreadsheetSaver { fileName, bytes ->
            pendingBytes = bytes
            launcher.launch(fileName)
        }
    }
}
