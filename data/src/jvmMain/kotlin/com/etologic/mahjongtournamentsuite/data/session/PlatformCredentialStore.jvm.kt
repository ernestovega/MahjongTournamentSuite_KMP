package com.etologic.mahjongtournamentsuite.data.session

import java.io.ByteArrayOutputStream

actual class PlatformCredentialStore actual constructor() : CredentialStore {
    private val enabled = System.getProperty("os.name")?.contains("mac", ignoreCase = true) == true

    override suspend fun load(): SavedCredentials? {
        if (!enabled) return null
        val email = runSecurity("find-generic-password", "-s", EMAIL_SERVICE, "-w") ?: return null
        val password = runSecurity("find-generic-password", "-s", PASSWORD_SERVICE, "-a", email, "-w")
            ?: return null
        return SavedCredentials(email = email, password = password)
    }

    override suspend fun save(credentials: SavedCredentials) {
        if (!enabled) return
        runSecurity(
            "add-generic-password",
            "-s",
            EMAIL_SERVICE,
            "-a",
            ACCOUNT,
            "-w",
            credentials.email,
            "-U",
        )
        runSecurity(
            "add-generic-password",
            "-s",
            PASSWORD_SERVICE,
            "-a",
            credentials.email,
            "-w",
            credentials.password,
            "-U",
        )
    }

    override suspend fun clear() {
        if (!enabled) return
        val saved = load()
        runSecurity("delete-generic-password", "-s", EMAIL_SERVICE)
        saved?.let { runSecurity("delete-generic-password", "-s", PASSWORD_SERVICE, "-a", it.email) }
    }

    private fun runSecurity(vararg arguments: String): String? = runCatching {
        val process = ProcessBuilder("/usr/bin/security", *arguments)
            .redirectErrorStream(true)
            .start()
        val output = ByteArrayOutputStream()
        process.inputStream.copyTo(output)
        if (process.waitFor() != 0) return@runCatching null
        output.toString(Charsets.UTF_8.name()).trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private companion object {
        const val ACCOUNT = "default"
        const val EMAIL_SERVICE = "MahjongTournamentSuite.email"
        const val PASSWORD_SERVICE = "MahjongTournamentSuite.password"
    }
}
