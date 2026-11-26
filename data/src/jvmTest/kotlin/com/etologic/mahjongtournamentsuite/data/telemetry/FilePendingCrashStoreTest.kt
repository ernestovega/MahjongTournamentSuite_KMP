package com.etologic.mahjongtournamentsuite.data.telemetry

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The crash of the desktop app waits in a file of the home folder until the next start sends it once. */
class FilePendingCrashStoreTest {
    private val dir: File = Files.createTempDirectory("crash").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aSavedCrashIsTakenOnce() {
        val file = File(dir, "nested/pending-crash.json")
        val store = FilePendingCrashStore(file)
        assertNull(store.take())

        store.save("""{"crash":1}""")

        assertEquals("""{"crash":1}""", store.take())
        assertFalse(file.exists())
        assertNull(store.take())
    }

    @Test
    fun aFileThatCannotBeWrittenIsNoCrash() {
        val blocker = File(dir, "blocker").apply { writeText("a file, not a folder") }
        val store = FilePendingCrashStore(File(blocker, "pending-crash.json"))

        store.save("""{"crash":1}""")

        assertNull(store.take())
    }

    @Test
    fun theCrashHandlerRecordsTheCrashAndCallsThePreviousOne() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val seen = mutableListOf<String>()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> seen += "previous ${error.message}" }
            installCrashHandler { error -> seen += "recorded ${error.message}" }
            val boom = IllegalStateException("boom")

            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)

            assertEquals(listOf("recorded boom", "previous boom"), seen)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
        assertSame(original, Thread.getDefaultUncaughtExceptionHandler())
    }
}
