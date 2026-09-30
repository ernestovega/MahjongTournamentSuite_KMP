package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.serialization.json.Json

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try { return window.localStorage.getItem('mahjongTournamentSuite.authSession'); }
    catch (_) { return null; }
}""")
private external fun readStoredSession(): String?

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(value) => {
    try { window.localStorage.setItem('mahjongTournamentSuite.authSession', value); }
    catch (_) {}
}""")
private external fun writeStoredSession(value: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try { window.localStorage.removeItem('mahjongTournamentSuite.authSession'); }
    catch (_) {}
}""")
private external fun clearStoredSession()

actual class PlatformAuthSessionStore actual constructor(
    private val json: Json,
) : AuthSessionStore {
    private val serializer = StoredAuthSession.serializer()

    override suspend fun load(): StoredAuthSession? {
        return runCatching {
            readStoredSession()
                ?.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(serializer, it) }
        }.getOrNull()
    }

    override suspend fun save(session: StoredAuthSession?) {
        runCatching {
            if (session == null) {
                clearStoredSession()
            } else {
                writeStoredSession(json.encodeToString(serializer, session))
            }
        }
    }
}
