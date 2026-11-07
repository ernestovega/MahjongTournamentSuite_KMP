package com.etologic.mahjongtournamentsuite.data.cache

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(name) => {
    try { return window.localStorage.getItem(name); }
    catch (_) { return null; }
}""")
private external fun readLocalStorage(name: String): String?

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(name, value) => {
    try { window.localStorage.setItem(name, value); return true; }
    catch (_) { return false; }
}""")
private external fun writeLocalStorage(name: String, value: String): Boolean

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(prefix) => {
    try {
        const storage = window.localStorage;
        for (let i = storage.length - 1; i >= 0; i--) {
            const key = storage.key(i);
            if (key && key.startsWith(prefix)) storage.removeItem(key);
        }
    } catch (_) {}
}""")
private external fun removeLocalStorage(prefix: String)

/**
 * The local storage of the browser. It holds about 5 MB for each site. When it is full, the store removes all
 * its values and tries once more, so the newest answer wins.
 */
private class LocalStoragePersistentCacheStore : PersistentCacheStore {
    override suspend fun read(name: String): String? = readLocalStorage(PREFIX + name)

    override suspend fun write(name: String, value: String) {
        if (writeLocalStorage(PREFIX + name, value)) return
        removeLocalStorage(PREFIX)
        writeLocalStorage(PREFIX + name, value)
    }

    override suspend fun clear() = removeLocalStorage(PREFIX)

    private companion object {
        const val PREFIX = "mahjongTournamentSuite.cache."
    }
}

actual fun createPlatformPersistentCacheStore(): PersistentCacheStore = LocalStoragePersistentCacheStore()
