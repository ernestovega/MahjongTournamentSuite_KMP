package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_error_read

/** A CSV or Excel file that the user chose. */
class SelectedSpreadsheet(
    val fileName: String,
    val bytes: ByteArray,
)

class SpreadsheetPicker(
    private val launchAction: () -> Unit,
) {
    fun launch() = launchAction()
}

/** Opens the file chooser of the platform for CSV and Excel files. */
@Composable
expect fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
): SpreadsheetPicker

internal val SpreadsheetReadError: UiText = UiText.of(Res.string.spreadsheet_error_read)
