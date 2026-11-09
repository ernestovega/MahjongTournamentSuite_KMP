package com.etologic.mahjongtournamentsuite.data.cache

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(name) => {
        try { return window.localStorage.getItem(name); }
        catch (_) { return null; }
    }""",
)
private external fun readLocalStorage(name: String): String?

/**
 * Writes one cached value and keeps all cached values under [budget] characters. The other values of the app (the
 * session, the theme, the language) share the same storage, so the cache must never fill it: when the budget would
 * be passed, the cache removes its own values first. A value larger than the budget is not saved.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(prefix, name, value, budget) => {
        try {
            if (value.length > budget) return false;
            const storage = window.localStorage;
            let used = 0;
            for (let i = 0; i < storage.length; i++) {
                const key = storage.key(i);
                if (key && key.startsWith(prefix) && key !== name) used += key.length + (storage.getItem(key) || "").length;
            }
            if (used + name.length + value.length > budget) {
                for (let i = storage.length - 1; i >= 0; i--) {
                    const key = storage.key(i);
                    if (key && key.startsWith(prefix)) storage.removeItem(key);
                }
            }
            storage.setItem(name, value);
            return true;
        } catch (_) { return false; }
    }""",
)
private external fun writeLocalStorage(prefix: String, name: String, value: String, budget: Int): Boolean

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(prefix) => {
        try {
            const storage = window.localStorage;
            for (let i = storage.length - 1; i >= 0; i--) {
                const key = storage.key(i);
                if (key && key.startsWith(prefix)) storage.removeItem(key);
            }
        } catch (_) {}
    }""",
)
private external fun removeLocalStorage(prefix: String)

/**
 * The local storage of the browser. It holds about 5 MB for each site, shared with the session of the app.
 * The cache uses at most [BUDGET] characters of it. When a write fails, the cache removes all its values.
 */
private class LocalStoragePersistentCacheStore : PersistentCacheStore {
    override suspend fun read(name: String): String? = readLocalStorage(PREFIX + name)

    override suspend fun write(name: String, value: String) {
        if (!writeLocalStorage(PREFIX, PREFIX + name, value, BUDGET)) removeLocalStorage(PREFIX)
    }

    override suspend fun clear() = removeLocalStorage(PREFIX)

    private companion object {
        const val PREFIX = "mahjongTournamentSuite.cache."

        /** About 3 MB of text, so the session and the settings always find room. */
        const val BUDGET = 1_500_000
    }
}

actual fun createPlatformPersistentCacheStore(): PersistentCacheStore = LocalStoragePersistentCacheStore()
