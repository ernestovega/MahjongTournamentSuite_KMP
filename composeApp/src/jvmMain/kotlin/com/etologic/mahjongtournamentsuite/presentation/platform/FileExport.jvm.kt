package com.etologic.mahjongtournamentsuite.presentation.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun saveTextFile(fileName: String, content: String): Boolean {
    val dialog = FileDialog(null as Frame?, "Save EMA results", FileDialog.SAVE)
    dialog.file = fileName
    dialog.isVisible = true
    val selectedDirectory = dialog.directory ?: return false
    val selectedFile = dialog.file ?: return false
    File(selectedDirectory, selectedFile).writeText(content, Charsets.UTF_8)
    return true
}
