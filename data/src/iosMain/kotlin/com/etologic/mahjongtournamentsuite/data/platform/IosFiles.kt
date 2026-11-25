package com.etologic.mahjongtournamentsuite.data.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** Files of the app on iOS. The paths are plain strings, like the files of the other platforms. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosFiles {
    /** Data the app keeps: the session. iOS never empties it. */
    fun supportDirectory(): String = directory(NSApplicationSupportDirectory)

    /** Data the app can download again. iOS may empty it when the device needs space, and that is fine. */
    fun cachesDirectory(): String = directory(NSCachesDirectory)

    fun child(folder: String, name: String): String = folder.trimEnd('/') + "/" + name

    fun ensureDirectory(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    fun readText(path: String): String? {
        if (!exists(path)) return null
        return NSString.create(contentsOfFile = path, encoding = NSUTF8StringEncoding, error = null)?.toString()
    }

    fun readBytes(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    /** Writes to a new file first and then moves it, so a crash never leaves half a file. */
    fun writeText(path: String, text: String): Boolean = writeBytes(path, text.encodeToByteArray())

    fun writeBytes(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, atomically = true)

    fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun directory(kind: NSSearchPathDirectory): String {
        val path = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("No folder for $kind")
        ensureDirectory(path)
        return path
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
