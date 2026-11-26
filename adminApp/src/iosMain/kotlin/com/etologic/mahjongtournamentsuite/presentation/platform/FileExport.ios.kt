package com.etologic.mahjongtournamentsuite.presentation.platform

/** The share sheet of iOS: the user saves the file in Files or sends it to an app. */
actual fun saveTextFile(fileName: String, content: String, dialogTitle: String): Boolean {
    IosViewControllers.shareFile(fileName, content.encodeToByteArray())
    return true
}

actual fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String, dialogTitle: String): Boolean {
    IosViewControllers.shareFile(fileName, content)
    return true
}
