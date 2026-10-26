package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.io.encoding.Base64

@Composable
actual fun rememberSpreadsheetSaver(onError: (UiText) -> Unit): SpreadsheetSaver {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        SpreadsheetSaver { fileName, bytes ->
            runCatching { downloadBrowserFile(fileName, XLSX_MIME_TYPE, Base64.Default.encode(bytes)) }
                .onFailure { currentOnError.value(SpreadsheetSaveError) }
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, mimeType, base64) => {
        const binary = atob(base64);
        const data = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) data[i] = binary.charCodeAt(i);
        const url = URL.createObjectURL(new Blob([data], { type: mimeType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }""",
)
private external fun downloadBrowserFile(fileName: String, mimeType: String, base64: String)
