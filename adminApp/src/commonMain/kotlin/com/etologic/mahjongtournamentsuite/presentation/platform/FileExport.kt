package com.etologic.mahjongtournamentsuite.presentation.platform

/** [dialogTitle] is the title of the save dialog on the desktop. Other platforms ignore it.
 * Saves text as a local file and returns false when the user cancels. */
expect fun saveTextFile(fileName: String, content: String, dialogTitle: String = ""): Boolean

/** Saves binary content as a local file and returns false when the user cancels. */
expect fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String, dialogTitle: String = ""): Boolean
