package com.etologic.mahjongtournamentsuite.presentation.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The save dialog of the system: the chosen file, or null when it was cancelled. Tests replace it. */
internal var chooseSaveFile: (title: String, fileName: String) -> File? = { title, fileName ->
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = fileName
    dialog.isVisible = true
    val directory = dialog.directory
    val file = dialog.file
    if (directory == null || file == null) null else File(directory, file)
}

actual fun saveTextFile(fileName: String, content: String, dialogTitle: String): Boolean {
    val target = chooseSaveFile(dialogTitle.ifBlank { "Save file" }, fileName) ?: return false
    target.writeText(content, Charsets.UTF_8)
    return true
}

actual fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String, dialogTitle: String): Boolean {
    val target = chooseSaveFile(dialogTitle.ifBlank { "Save file" }, fileName) ?: return false
    target.writeBytes(content)
    return true
}
