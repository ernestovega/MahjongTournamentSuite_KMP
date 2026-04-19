package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.serialization.json.Json

actual class PlatformAuthSessionStore actual constructor(
    json: Json,
) : AuthSessionStore {
    // Kotlin/Wasm does not provide the same browser helpers as Kotlin/JS.
    // Keep this as an in-memory store for now (persists only while the page is open).
    @Suppress("unused")
    private val unusedJson: Json = json

    override suspend fun load(): StoredAuthSession? {
        return cached
    }

    override suspend fun save(session: StoredAuthSession?) {
        cached = session
    }

    private companion object {
        private var cached: StoredAuthSession? = null
    }
}
