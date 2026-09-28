package com.etologic.mahjongtournamentsuite.presentation.platform

/** Saves text as a local file and returns false when the user cancels. */
expect fun saveTextFile(fileName: String, content: String): Boolean

/** Saves binary content as a local file and returns false when the user cancels. */
expect fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String): Boolean
