package com.etologic.mahjongtournamentsuite.presentation.platform

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import com.etologic.mahjongtournamentsuite.presentation.util.UiText

private val spreadsheetMimeTypes = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/csv",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
)

@Composable
actual fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
): SpreadsheetPicker {
    val context = AndroidPlatformContext.requireContext()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val resolver = context.contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "players.csv"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The file cannot be opened.")
            SelectedSpreadsheet(fileName, bytes)
        }.onSuccess(onSelected)
            .onFailure { onError(SpreadsheetReadError) }
    }
    return remember(launcher) { SpreadsheetPicker { launcher.launch(spreadsheetMimeTypes) } }
}
