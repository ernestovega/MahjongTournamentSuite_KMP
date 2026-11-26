package com.etologic.mahjongtournamentsuite.data.session

import java.io.ByteArrayOutputStream

actual class PlatformCredentialStore internal constructor(
    /** Only macOS has the Keychain. On Windows and Linux nothing is saved. */
    private val enabled: Boolean,
    /** The Keychain tool. A test gives a fake one. */
    private val security: String,
) : CredentialStore {
    actual constructor() : this(
        enabled = System.getProperty("os.name")?.contains("mac", ignoreCase = true) == true,
        security = "/usr/bin/security",
    )

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
        // The password never goes in the arguments of the process, which any user of the Mac can read in the
        // list of processes: `security -i` reads the command from its standard input.
        runSecurityInteractive(
            listOf("add-generic-password", "-s", PASSWORD_SERVICE, "-a", credentials.email, "-w", credentials.password, "-U"),
        )
    }

    override suspend fun clear() {
        if (!enabled) return
        val saved = load()
        runSecurity("delete-generic-password", "-s", EMAIL_SERVICE)
        saved?.let { runSecurity("delete-generic-password", "-s", PASSWORD_SERVICE, "-a", it.email) }
    }

    private fun runSecurity(vararg arguments: String): String? = runCatching {
        val process = ProcessBuilder(security, *arguments)
            .redirectErrorStream(true)
            .start()
        val output = ByteArrayOutputStream()
        process.inputStream.copyTo(output)
        if (process.waitFor() != 0) return@runCatching null
        output.toString(Charsets.UTF_8.name()).trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun runSecurityInteractive(arguments: List<String>): Boolean = runCatching {
        val process = ProcessBuilder(security, "-i")
            .redirectErrorStream(true)
            .start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { input ->
            input.write(securityCommandLine(arguments))
            input.write("\n")
        }
        process.inputStream.copyTo(ByteArrayOutputStream())
        process.waitFor() == 0
    }.getOrDefault(false)

    private companion object {
        const val ACCOUNT = "default"
        const val EMAIL_SERVICE = "MahjongTournamentSuite.email"
        const val PASSWORD_SERVICE = "MahjongTournamentSuite.password"
    }
}

/** One line for `security -i`: every argument in double quotes, with `\` and `"` escaped. */
internal fun securityCommandLine(arguments: List<String>): String = arguments.joinToString(" ") { argument ->
    "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
