package com.etologic.mahjongtournamentsuite.data.session

import java.io.File
import kotlinx.serialization.json.Json

actual class PlatformAuthSessionStore actual constructor(
    private val json: Json,
) : AuthSessionStore {
    private val file: File = run {
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, ".mahjongtournamentsuite")
        dir.mkdirs()
        File(dir, "auth_session.json")
    }

    override suspend fun load(): StoredAuthSession? {
        if (!file.exists()) return null
        val text = file.readText()
        if (text.isBlank()) return null
        return runCatching { json.decodeFromString(StoredAuthSession.serializer(), text) }.getOrNull()
    }

    override suspend fun save(session: StoredAuthSession?) {
        if (session == null) {
            file.delete()
            return
        }
        file.writeText(json.encodeToString(StoredAuthSession.serializer(), session))
    }
}

