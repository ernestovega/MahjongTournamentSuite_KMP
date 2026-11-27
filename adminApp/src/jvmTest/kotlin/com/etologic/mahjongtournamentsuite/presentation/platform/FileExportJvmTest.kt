package com.etologic.mahjongtournamentsuite.presentation.platform

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A file of the desktop app goes where the save dialog says, and a cancelled dialog writes nothing. */
class FileExportJvmTest {
    private val system = chooseSaveFile
    private val folder = createTempDirectory("export").toFile()
    private val asked = mutableListOf<Pair<String, String>>()

    @AfterTest
    fun restore() {
        chooseSaveFile = system
        folder.deleteRecursively()
    }

    private fun chooseInFolder() {
        chooseSaveFile = { title, name ->
            asked += title to name
            File(folder, name)
        }
    }

    @Test
    fun aTextFileIsWrittenWhereTheDialogSays() {
        chooseInFolder()
        assertTrue(saveTextFile("players.csv", "a;b", dialogTitle = ""))
        assertEquals("a;b", File(folder, "players.csv").readText())
        assertEquals(listOf("Save file" to "players.csv"), asked)
    }

    @Test
    fun aBinaryFileIsWrittenWhereTheDialogSays() {
        chooseInFolder()
        assertTrue(saveBinaryFile("sheets.pdf", byteArrayOf(1, 2, 3), "application/pdf", dialogTitle = "Save the sheets"))
        assertContentEquals(byteArrayOf(1, 2, 3), File(folder, "sheets.pdf").readBytes())
        assertEquals(listOf("Save the sheets" to "sheets.pdf"), asked)
    }

    @Test
    fun aCancelledDialogWritesNothing() {
        chooseSaveFile = { _, _ -> null }
        assertFalse(saveTextFile("players.csv", "a;b", dialogTitle = "Save"))
        assertFalse(saveBinaryFile("sheets.pdf", byteArrayOf(1), "application/pdf", dialogTitle = "Save"))
        assertEquals(0, folder.listFiles()?.size)
    }
}
