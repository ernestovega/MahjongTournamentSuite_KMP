package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A spreadsheet of the desktop app gets its .xlsx ending, a cancelled dialog writes nothing and a failed write says so. */
@OptIn(ExperimentalTestApi::class)
class SpreadsheetSaverJvmTest {
    private val system = chooseSpreadsheetFile
    private val folder = createTempDirectory("sheets").toFile()
    private val errors = mutableListOf<UiText>()

    @AfterTest
    fun restore() {
        chooseSpreadsheetFile = system
        folder.deleteRecursively()
    }

    private fun save(fileName: String) = runComposeUiTest {
        var saver: SpreadsheetSaver? = null
        setContent { saver = rememberSpreadsheetSaver { errors += it } }
        waitForIdle()
        saver!!.save(fileName, byteArrayOf(7, 8))
    }

    @Test
    fun aNameWithoutItsEndingGetsXlsx() {
        chooseSpreadsheetFile = { _, _ -> File(folder, "players") }
        save("players.xlsx")
        assertContentEquals(byteArrayOf(7, 8), File(folder, "players.xlsx").readBytes())
    }

    @Test
    fun aNameWithItsEndingKeepsIt() {
        chooseSpreadsheetFile = { _, name -> File(folder, name) }
        save("ranking.XLSX")
        assertTrue(File(folder, "ranking.XLSX").exists())
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aCancelledDialogWritesNothing() {
        chooseSpreadsheetFile = { _, _ -> null }
        save("players.xlsx")
        assertEquals(0, folder.listFiles()?.size)
    }

    @Test
    fun aFileThatCannotBeWrittenSaysSo() {
        chooseSpreadsheetFile = { _, _ -> File(File(folder, "missing-folder"), "players.xlsx") }
        save("players.xlsx")
        assertEquals(listOf(SpreadsheetSaveError), errors)
    }
}
