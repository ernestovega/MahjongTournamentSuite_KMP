package com.etologic.mahjongtournamentsuite.presentation.platform

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
