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
import org.jetbrains.compose.resources.stringResource

/** The save dialog of a spreadsheet: the chosen file, or null when it was cancelled. Tests replace it. */
internal var chooseSpreadsheetFile: (filterName: String, fileName: String) -> File? = ::systemSpreadsheetSaveDialog

@NativeDesktopWindow
private fun systemSpreadsheetSaveDialog(filterName: String, fileName: String): File? {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter(filterName, "xlsx")
        isAcceptAllFileFilterUsed = false
        selectedFile = File(fileName)
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
actual fun rememberSpreadsheetSaver(onError: (UiText) -> Unit): SpreadsheetSaver {
    val currentOnError = rememberUpdatedState(onError)
    val chooserFilter = stringResource(Res.string.spreadsheet_chooser_filter)
    return remember(chooserFilter) {
        SpreadsheetSaver { fileName, bytes ->
            val chosen = chooseSpreadsheetFile(chooserFilter, fileName) ?: return@SpreadsheetSaver
            runCatching {
                val target = if (chosen.name.endsWith(".xlsx", ignoreCase = true)) chosen else File(chosen.path + ".xlsx")
                target.writeBytes(bytes)
            }.onFailure { currentOnError.value(SpreadsheetSaveError) }
        }
    }
}
