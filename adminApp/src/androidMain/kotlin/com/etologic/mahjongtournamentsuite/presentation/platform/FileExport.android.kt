package com.etologic.mahjongtournamentsuite.presentation.platform

import android.content.ContentValues
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun saveTextFile(fileName: String, content: String): Boolean {
    val context = AndroidPlatformContext.requireContext()
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Files.getContentUri("external")
    }
    val uri = context.contentResolver.insert(collection, values) ?: return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open the export file")
        true
    } catch (error: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
}

actual fun saveBinaryFile(fileName: String, content: ByteArray, mimeType: String): Boolean {
    val context = AndroidPlatformContext.requireContext()
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Files.getContentUri("external")
    }
    val uri = context.contentResolver.insert(collection, values) ?: return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { output -> output.write(content) }
            ?: error("Unable to open the export file")
        true
    } catch (error: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
}
