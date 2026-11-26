package com.etologic.mahjongtournamentsuite.data.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The files of the app on iOS, on the simulator: folders, text and bytes. */
class IosFilesTest {
    private val folder = IosFiles.child(IosFiles.cachesDirectory(), "ios-files-test")

    @AfterTest
    fun cleanUp() = IosFiles.delete(folder)

    @Test
    fun theFoldersOfTheAppExist() {
        assertTrue(IosFiles.exists(IosFiles.supportDirectory()))
        assertTrue(IosFiles.exists(IosFiles.cachesDirectory()))
    }

    @Test
    fun childJoinsWithOneSlash() {
        assertEquals("/a/b", IosFiles.child("/a", "b"))
        assertEquals("/a/b", IosFiles.child("/a/", "b"))
    }

    @Test
    fun textGoesAndComesBackWithAccentsAndTiles() {
        IosFiles.ensureDirectory(folder)
        val file = IosFiles.child(folder, "text.txt")
        val text = "Ronda 3 · Señora Muñoz · 🀄 東"
        assertTrue(IosFiles.writeText(file, text))
        assertEquals(text, IosFiles.readText(file))
    }

    @Test
    fun bytesGoAndComeBackWhole() {
        IosFiles.ensureDirectory(folder)
        val file = IosFiles.child(folder, "bytes.bin")
        val bytes = ByteArray(1000) { (it % 256).toByte() }
        assertTrue(IosFiles.writeBytes(file, bytes))
        assertContentEquals(bytes, IosFiles.readBytes(file))
    }

    @Test
    fun anEmptyFileIsEmptyNotMissing() {
        IosFiles.ensureDirectory(folder)
        val file = IosFiles.child(folder, "empty.bin")
        assertTrue(IosFiles.writeBytes(file, ByteArray(0)))
        assertContentEquals(ByteArray(0), IosFiles.readBytes(file))
        assertEquals("", IosFiles.readText(file))
    }

    @Test
    fun aMissingFileReadsAsNull() {
        val file = IosFiles.child(folder, "missing.txt")
        assertFalse(IosFiles.exists(file))
        assertNull(IosFiles.readText(file))
        assertNull(IosFiles.readBytes(file))
    }

    @Test
    fun deleteRemovesTheFileAndAFolderWithItsFiles() {
        IosFiles.ensureDirectory(IosFiles.child(folder, "inner"))
        val file = IosFiles.child(IosFiles.child(folder, "inner"), "a.txt")
        IosFiles.writeText(file, "a")
        IosFiles.delete(file)
        assertFalse(IosFiles.exists(file))
        IosFiles.writeText(file, "a")
        IosFiles.delete(folder)
        assertFalse(IosFiles.exists(folder))
    }

    @Test
    fun writingAgainReplacesTheWholeFile() {
        IosFiles.ensureDirectory(folder)
        val file = IosFiles.child(folder, "again.txt")
        IosFiles.writeText(file, "a long first text")
        IosFiles.writeText(file, "short")
        assertEquals("short", IosFiles.readText(file))
    }
}
