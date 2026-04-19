package com.etologic.mahjongtournamentsuite.data.session

import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import java.io.File
import kotlinx.serialization.json.Json

actual class PlatformAuthSessionStore actual constructor(
    private val json: Json,
) : AuthSessionStore {
    private fun file(): File {
        val dir = AndroidPlatformContext.requireContext().filesDir
        return File(dir, "auth_session.json")
    }

    override suspend fun load(): StoredAuthSession? {
        val file = file()
        if (!file.exists()) return null
        val text = file.readText()
        if (text.isBlank()) return null
        return runCatching { json.decodeFromString(StoredAuthSession.serializer(), text) }.getOrNull()
    }

    override suspend fun save(session: StoredAuthSession?) {
        val file = file()
        if (session == null) {
            file.delete()
            return
        }
        file.writeText(json.encodeToString(StoredAuthSession.serializer(), session))
    }
}

