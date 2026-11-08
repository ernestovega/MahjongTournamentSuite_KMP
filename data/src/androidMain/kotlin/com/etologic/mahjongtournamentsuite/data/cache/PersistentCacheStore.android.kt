package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The cache folder of the app. Android may empty it when the device needs space, and that is fine. */
actual fun createPlatformPersistentCacheStore(): PersistentCacheStore = FilePersistentCacheStore { File(AndroidPlatformContext.requireContext().cacheDir, "repository-cache") }

/** One file for each value. A value is written to a new file first, so a crash never leaves half a file. */
private class FilePersistentCacheStore(private val directory: () -> File) : PersistentCacheStore {
    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(directory(), name).takeIf(File::isFile)?.readText() }.getOrNull()
    }

    override suspend fun write(name: String, value: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = directory().apply { mkdirs() }
                val partial = File(folder, "$name.partial")
                partial.writeText(value)
                val target = File(folder, name)
                if (!partial.renameTo(target)) {
                    target.delete()
                    partial.renameTo(target)
                }
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { runCatching { directory().deleteRecursively() } }
    }
}
