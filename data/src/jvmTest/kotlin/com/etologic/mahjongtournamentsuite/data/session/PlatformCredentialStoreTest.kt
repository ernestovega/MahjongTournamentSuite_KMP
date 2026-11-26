package com.etologic.mahjongtournamentsuite.data.session

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The desktop app keeps the email and the password in the macOS Keychain through the `security` tool. A fake tool keeps
 * them in files, so the test runs on any computer, and records its command lines to check that the password never goes
 * in the arguments of a process.
 */
class PlatformCredentialStoreTest {
    private val dir: File = Files.createTempDirectory("keychain").toFile()
    private val calls = File(dir, "calls.txt")
    private val security = File(dir, "security").apply {
        writeText(
            """
            |#!/bin/bash
            |store="${'$'}(dirname "${'$'}0")/store"
            |mkdir -p "${'$'}store"
            |echo "${'$'}*" >> "${'$'}(dirname "${'$'}0")/calls.txt"
            |if [ "${'$'}1" = "-i" ]; then read -r line; eval "set -- ${'$'}line"; fi
            |cmd=${'$'}1; shift; s=; a=; w=
            |while [ ${'$'}# -gt 0 ]; do
            |  case ${'$'}1 in
            |    -s) s=${'$'}2; shift 2;;
            |    -a) a=${'$'}2; shift 2;;
            |    -w) if [ ${'$'}# -ge 2 ] && [ "${'$'}2" != "-U" ]; then w=${'$'}2; shift 2; else shift; fi;;
            |    *) shift;;
            |  esac
            |done
            |case ${'$'}cmd in
            |  add-generic-password) mkdir -p "${'$'}store/${'$'}s"; printf '%s' "${'$'}w" > "${'$'}store/${'$'}s/${'$'}a";;
            |  find-generic-password)
            |    if [ -n "${'$'}a" ]; then f="${'$'}store/${'$'}s/${'$'}a"; else f=${'$'}(ls -d "${'$'}store/${'$'}s"/* 2>/dev/null | head -1); fi
            |    [ -n "${'$'}f" ] && [ -f "${'$'}f" ] || exit 44
            |    cat "${'$'}f";;
            |  delete-generic-password)
            |    if [ -n "${'$'}a" ]; then rm -f "${'$'}store/${'$'}s/${'$'}a"; else rm -rf "${'$'}store/${'$'}s"; fi;;
            |esac
            """.trimMargin() + "\n",
        )
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun keepsTheCredentialsAndForgetsThem() = runTest {
        val store = PlatformCredentialStore(enabled = true, security = security.path)
        assertNull(store.load())

        store.save(SavedCredentials(email = "a@b.c", password = "se cret\"1"))

        assertEquals(SavedCredentials(email = "a@b.c", password = "se cret\"1"), store.load())
        assertFalse(calls.readText().contains("se cret"), "the password goes through the standard input, never the arguments")

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun anEmailWithoutItsPasswordIsNoCredentials() = runTest {
        val store = PlatformCredentialStore(enabled = true, security = security.path)
        store.save(SavedCredentials(email = "a@b.c", password = "x"))
        File(dir, "store/MahjongTournamentSuite.password").deleteRecursively()

        assertNull(store.load())
    }

    @Test
    fun withoutTheKeychainNothingIsSavedOrRead() = runTest {
        val store = PlatformCredentialStore(enabled = false, security = security.path)

        store.save(SavedCredentials(email = "a@b.c", password = "x"))
        store.clear()

        assertNull(store.load())
        assertFalse(calls.exists(), "the tool is never called")
    }

    @Test
    fun aMissingToolReadsAsNoCredentials() = runTest {
        val store = PlatformCredentialStore(enabled = true, security = File(dir, "missing").path)

        store.save(SavedCredentials(email = "a@b.c", password = "x"))

        assertNull(store.load())
    }
}
