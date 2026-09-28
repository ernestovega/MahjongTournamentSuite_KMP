package com.etologic.mahjongtournamentsuite.presentation.platform

import kotlin.io.encoding.Base64

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, content) => {
        const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }""",
)
private external fun downloadTextFile(fileName: String, content: String)

actual fun saveTextFile(fileName: String, content: String): Boolean {
    downloadTextFile(fileName, content)
    return true
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, base64, mimeType) => {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
        const blob = new Blob([bytes], { type: mimeType });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }""",
)
private external fun downloadBase64File(fileName: String, base64: String, mimeType: String)

actual fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String): Boolean {
    downloadBase64File(fileName, Base64.Default.encode(content), mimeType)
    return true
}
