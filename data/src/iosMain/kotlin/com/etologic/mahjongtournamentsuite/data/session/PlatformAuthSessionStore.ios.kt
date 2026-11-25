package com.etologic.mahjongtournamentsuite.data.session

import com.etologic.mahjongtournamentsuite.data.platform.IosFiles
import kotlinx.serialization.json.Json

/** The session in a file of Application Support, like the file of the Android app. */
actual class PlatformAuthSessionStore actual constructor(
    private val json: Json,
) : AuthSessionStore {
    override suspend fun load(): StoredAuthSession? {
        val text = IosFiles.readText(sessionFile())?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString(StoredAuthSession.serializer(), text) }.getOrNull()
    }

    override suspend fun save(session: StoredAuthSession?) {
        val file = sessionFile()
        if (session == null) {
            IosFiles.delete(file)
            return
        }
        IosFiles.writeText(file, json.encodeToString(StoredAuthSession.serializer(), session))
    }
}

private val uidJson = Json { ignoreUnknownKeys = true }

private fun sessionFile(): String = IosFiles.child(IosFiles.supportDirectory(), "auth_session.json")

/** The account of the saved session, or null without one. The suite keeps the screens of its admin part only for it. */
fun storedSessionUid(): String? {
    val text = IosFiles.readText(sessionFile())?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { uidJson.decodeFromString(StoredAuthSession.serializer(), text).uid }.getOrNull()
}
