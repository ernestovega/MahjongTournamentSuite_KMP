package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On a computer a shared image is saved where the user chooses, with its .png ending. */
@OptIn(ExperimentalTestApi::class)
class ImageShareJvmTest {
    private val system = chooseImageFile
    private val folder = createTempDirectory("shared").toFile()
    private var errors = 0

    @AfterTest
    fun restore() {
        chooseImageFile = system
        folder.deleteRecursively()
    }

    private fun share(fileName: String) = runComposeUiTest {
        var share: ((String, ByteArray, String) -> Unit)? = null
        setContent { share = rememberShareImage { errors++ } }
        waitForIdle()
        share!!(fileName, byteArrayOf(9), "Share the ranking")
    }

    @Test
    fun aNameWithoutItsEndingGetsPng() {
        chooseImageFile = { _, _ -> File(folder, "ranking") }
        share("ranking.png")
        assertContentEquals(byteArrayOf(9), File(folder, "ranking.png").readBytes())
    }

    @Test
    fun aNameWithItsEndingKeepsIt() {
        chooseImageFile = { _, name -> File(folder, name) }
        share("card.PNG")
        assertTrue(File(folder, "card.PNG").exists())
    }

    @Test
    fun aCancelledDialogSavesNothingAndAFailedWriteSaysSo() {
        chooseImageFile = { _, _ -> null }
        share("ranking.png")
        assertEquals(0, folder.listFiles()?.size)

        chooseImageFile = { _, _ -> File(File(folder, "missing"), "ranking.png") }
        share("ranking.png")
        assertEquals(1, errors)
    }
}
