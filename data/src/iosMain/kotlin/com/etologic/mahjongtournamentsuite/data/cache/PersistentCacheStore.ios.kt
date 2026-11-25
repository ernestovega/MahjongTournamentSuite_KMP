package com.etologic.mahjongtournamentsuite.data.cache

import com.etologic.mahjongtournamentsuite.data.platform.IosFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** The cache folder of the app. iOS may empty it when the device needs space, and that is fine. */
actual fun createPlatformPersistentCacheStore(): PersistentCacheStore = IosPersistentCacheStore

/** One file for each value, written whole (atomically), like the Android store. */
private object IosPersistentCacheStore : PersistentCacheStore {
    private fun folder(): String = IosFiles.child(IosFiles.cachesDirectory(), "repository-cache")

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { IosFiles.readText(IosFiles.child(folder(), name)) }.getOrNull()
    }

    override suspend fun write(name: String, value: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = folder().also(IosFiles::ensureDirectory)
                IosFiles.writeText(IosFiles.child(folder, name), value)
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { runCatching { IosFiles.delete(folder()) } }
    }
}
