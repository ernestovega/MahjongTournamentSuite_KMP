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

/** The spreadsheet that the desktop open dialog chooses is read, a cancel says so, and a file that cannot be read is an error. */
@OptIn(ExperimentalTestApi::class)
class SpreadsheetPickerJvmTest {
    private val system = chooseOpenSpreadsheet
    private val folder = createTempDirectory("picked").toFile()
    private val picked = mutableListOf<SelectedSpreadsheet>()
    private val errors = mutableListOf<UiText>()
    private var cancels = 0

    @AfterTest
    fun restore() {
        chooseOpenSpreadsheet = system
        folder.deleteRecursively()
    }

    private fun pick() = runComposeUiTest {
        var picker: SpreadsheetPicker? = null
        setContent { picker = rememberSpreadsheetPicker(onSelected = { picked += it }, onError = { errors += it }, onCancelled = { cancels++ }) }
        waitForIdle()
        picker!!.launch()
    }

    @Test
    fun theChosenFileIsRead() {
        val file = File(folder, "players.csv").apply { writeBytes(byteArrayOf(1, 2)) }
        chooseOpenSpreadsheet = { _, _ -> file }
        pick()
        assertEquals("players.csv", picked.single().fileName)
        assertContentEquals(byteArrayOf(1, 2), picked.single().bytes)
    }

    @Test
    fun aCancelledDialogSaysSo() {
        chooseOpenSpreadsheet = { _, _ -> null }
        pick()
        assertEquals(1, cancels)
    }

    @Test
    fun aFileThatCannotBeReadIsAnError() {
        chooseOpenSpreadsheet = { _, _ -> File(folder, "missing.csv") }
        pick()
        assertEquals(listOf(SpreadsheetReadError), errors)
    }
}
