package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeSpreadsheet
import platform.darwin.NSObject

/** UIKit keeps a weak reference to the delegate of a picker, so the open one lives here. */
private var openSpreadsheetPicker: SpreadsheetPickerDelegate? = null

/** The file picker of iOS (Files, iCloud Drive and the apps that share files) for CSV and Excel files. */
@Composable
actual fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
    onCancelled: () -> Unit,
): SpreadsheetPicker {
    val selected = rememberUpdatedState(onSelected)
    val error = rememberUpdatedState(onError)
    val cancelled = rememberUpdatedState(onCancelled)
    // The UI tests run on the iOS simulator too, where the document picker of the system cannot be used.
    LocalPickedSpreadsheetForTests.current?.let { picked ->
        return remember(picked) {
            SpreadsheetPicker {
                val file = picked()
                if (file == null) cancelled.value() else selected.value(file)
            }
        }
    }
    return remember {
        SpreadsheetPicker {
            val types = listOfNotNull(
                UTTypeCommaSeparatedText,
                UTTypeSpreadsheet,
                UTTypePlainText,
                UTType.typeWithFilenameExtension("xlsx"),
                UTType.typeWithFilenameExtension("xls"),
            )
            // As a copy: the app reads a copy in its own folder and needs no access to the place of the file.
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            val delegate = SpreadsheetPickerDelegate(
                onSelected = { selected.value(it) },
                onError = { error.value(SpreadsheetReadError) },
                onCancelled = { cancelled.value() },
            )
            openSpreadsheetPicker = delegate
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            IosViewControllers.present(picker)
        }
    }
}

private class SpreadsheetPickerDelegate(
    private val onSelected: (SelectedSpreadsheet) -> Unit,
    private val onError: () -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        openSpreadsheetPicker = null
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onCancelled()
            return
        }
        val data: NSData? = NSData.dataWithContentsOfURL(url)
        if (data == null) {
            onError()
            return
        }
        onSelected(SelectedSpreadsheet(url.lastPathComponent ?: "players.csv", data.toByteArray()))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        openSpreadsheetPicker = null
        onCancelled()
    }
}
