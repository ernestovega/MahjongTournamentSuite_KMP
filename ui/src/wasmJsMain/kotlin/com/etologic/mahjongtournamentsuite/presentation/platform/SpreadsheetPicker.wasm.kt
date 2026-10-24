package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.io.encoding.Base64

@Composable
actual fun rememberSpreadsheetPicker(
    onSelected: (SelectedSpreadsheet) -> Unit,
    onError: (UiText) -> Unit,
): SpreadsheetPicker {
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        SpreadsheetPicker {
            launchBrowserSpreadsheetPicker(
                onSelected = { fileName, base64Data ->
                    runCatching { SelectedSpreadsheet(fileName, Base64.Default.decode(base64Data)) }
                        .onSuccess(currentOnSelected.value)
                        .onFailure { currentOnError.value(SpreadsheetReadError) }
                },
                onError = { currentOnError.value(SpreadsheetReadError) },
            )
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onSelected, onError) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onerror = () => onError('read');
            reader.onload = () => {
                const result = String(reader.result || '');
                const separator = result.indexOf(',');
                if (separator < 0) {
                    onError('invalid');
                    return;
                }
                onSelected(file.name, result.substring(separator + 1));
            };
            reader.readAsDataURL(file);
        };
        input.click();
    }""",
)
private external fun launchBrowserSpreadsheetPicker(
    onSelected: (String, String) -> Unit,
    onError: (String) -> Unit,
)
