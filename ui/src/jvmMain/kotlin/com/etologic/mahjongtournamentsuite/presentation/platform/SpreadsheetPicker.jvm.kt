package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_chooser_filter
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_chooser_title
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
): SpreadsheetPicker {
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    val chooserTitle = stringResource(Res.string.spreadsheet_chooser_title)
    val chooserFilter = stringResource(Res.string.spreadsheet_chooser_filter)
    return remember(chooserTitle, chooserFilter) {
        SpreadsheetPicker {
            val chooser = JFileChooser().apply {
                dialogTitle = chooserTitle
                fileFilter = FileNameExtensionFilter(chooserFilter, "csv", "xlsx")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val file = chooser.selectedFile
                    SelectedSpreadsheet(file.name, file.readBytes())
                }.onSuccess(currentOnSelected.value)
                    .onFailure { currentOnError.value(SpreadsheetReadError) }
            }
        }
    }
}
