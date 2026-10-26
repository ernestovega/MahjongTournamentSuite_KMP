package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_error_save

class SpreadsheetSaver(
    private val saveAction: (fileName: String, bytes: ByteArray) -> Unit,
) {
    fun save(fileName: String, bytes: ByteArray) = saveAction(fileName, bytes)
}

/** Saves an Excel file with the save dialog or the download of the platform. */
@Composable
expect fun rememberSpreadsheetSaver(onError: (UiText) -> Unit): SpreadsheetSaver

internal val SpreadsheetSaveError: UiText = UiText.of(Res.string.spreadsheet_error_save)

internal const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
