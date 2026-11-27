package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_chooser_filter
import mahjongtournamentsuite.ui.generated.resources.spreadsheet_chooser_title
import org.jetbrains.compose.resources.stringResource

/** The open dialog of a spreadsheet: the chosen file, or null when it was cancelled. Tests replace it. */
internal var chooseOpenSpreadsheet: (title: String, filterName: String) -> File? = { title, filterName ->
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(filterName, "csv", "xlsx")
        isAcceptAllFileFilterUsed = false
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
actual fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
    onCancelled: () -> Unit,
): SpreadsheetPicker {
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    val currentOnCancelled = rememberUpdatedState(onCancelled)
    LocalPickedSpreadsheetForTests.current?.let { picked ->
        return remember(picked) {
            SpreadsheetPicker {
                val file = picked()
                if (file == null) currentOnCancelled.value() else currentOnSelected.value(file)
            }
        }
    }
    val chooserTitle = stringResource(Res.string.spreadsheet_chooser_title)
    val chooserFilter = stringResource(Res.string.spreadsheet_chooser_filter)
    return remember(chooserTitle, chooserFilter) {
        SpreadsheetPicker {
            val file = chooseOpenSpreadsheet(chooserTitle, chooserFilter)
            if (file == null) {
                currentOnCancelled.value()
            } else {
                runCatching { SelectedSpreadsheet(file.name, file.readBytes()) }
                    .onSuccess(currentOnSelected.value)
                    .onFailure { currentOnError.value(SpreadsheetReadError) }
            }
        }
    }
}
