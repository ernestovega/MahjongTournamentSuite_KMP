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

/** The image that the desktop open dialog chooses is read, a cancel does nothing, and a file that cannot be read is an error. */
@OptIn(ExperimentalTestApi::class)
class ImagePickerJvmTest {
    private val system = chooseImage
    private val folder = createTempDirectory("image").toFile()
    private val picked = mutableListOf<SelectedImage>()
    private val errors = mutableListOf<UiText>()

    @AfterTest
    fun restore() {
        chooseImage = system
        folder.deleteRecursively()
    }

    private fun pick() = runComposeUiTest {
        var picker: ImagePicker? = null
        setContent { picker = rememberImagePicker(onImageSelected = { picked += it }, onError = { errors += it }) }
        waitForIdle()
        picker!!.launch()
    }

    @Test
    fun theChosenImageIsRead() {
        val file = File(folder, "me.png").apply { writeBytes(byteArrayOf(1, 2)) }
        chooseImage = { _, _ -> file }
        pick()
        assertEquals("me.png", picked.single().fileName)
        assertEquals("image/png", picked.single().contentType)
        assertContentEquals(byteArrayOf(1, 2), picked.single().bytes)
    }

    @Test
    fun aCancelledDialogPicksNothing() {
        chooseImage = { _, _ -> null }
        pick()
        assertTrue(picked.isEmpty() && errors.isEmpty())
    }

    @Test
    fun aFileThatCannotBeReadIsAnError() {
        chooseImage = { _, _ -> File(folder, "missing.png") }
        pick()
        assertEquals(listOf(ImageReadError), errors)
    }

    @Test
    fun theContentTypeFallsBackToTheFileEnding() {
        assertEquals("image/png", contentTypeFromFileName("a.PNG"))
        assertEquals("image/webp", contentTypeFromFileName("a.webp"))
        assertEquals("image/gif", contentTypeFromFileName("a.gif"))
        assertEquals("image/jpeg", contentTypeFromFileName("a"))
    }
}
