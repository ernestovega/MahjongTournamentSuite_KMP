package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText

/** The "Save to Files" picker of iOS, where the user chooses the folder of the Excel file. */
@Composable
actual fun rememberSpreadsheetSaver(onError: (UiText) -> Unit): SpreadsheetSaver {
    val error = rememberUpdatedState(onError)
    return remember {
        SpreadsheetSaver { fileName, bytes ->
            runCatching { IosViewControllers.exportFile(fileName, bytes) }
                .onFailure { error.value(SpreadsheetSaveError) }
        }
    }
}
