package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (String) -> Unit,
): ImagePicker {
    val currentOnImageSelected = rememberUpdatedState(onImageSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        ImagePicker {
            launchBrowserImagePicker(
                onSelected = { fileName, contentType, base64Data ->
                    runCatching { selectedImageFromBase64(fileName, contentType, base64Data) }
                        .onSuccess(currentOnImageSelected.value)
                        .onFailure { currentOnError.value(it.message ?: "The photo could not be read.") }
                },
                onError = { currentOnError.value(it) },
            )
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onSelected, onError) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onerror = () => onError('The photo could not be read.');
            reader.onload = () => {
                const result = String(reader.result || '');
                const separator = result.indexOf(',');
                if (separator < 0) {
                    onError('The selected file is not a valid image.');
                    return;
                }
                onSelected(file.name, file.type || 'image/jpeg', result.substring(separator + 1));
            };
            reader.readAsDataURL(file);
        };
        input.click();
    }""",
)
private external fun launchBrowserImagePicker(
    onSelected: (String, String, String) -> Unit,
    onError: (String) -> Unit,
)
