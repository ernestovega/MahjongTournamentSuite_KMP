package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalPickedImageForTests
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The photo editor: the checks of a picked image, the public notice, the crop and the upload, and the menu of the pencil. */
@OptIn(ExperimentalTestApi::class)
class PhotoEditorUiTest {
    private fun png(): SelectedImage {
        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.BLUE
            fillRect(0, 0, 40, 40)
            dispose()
        }
        val bytes = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
        return SelectedImage("me.png", "image/png", bytes)
    }

    private var next: SelectedImage = SelectedImage("me.bmp", "image/bmp", ByteArray(8))

    @Test
    fun aPickedImageIsCheckedCroppedAndUploaded() = runComposeUiTest {
        val clock = TestTimeSource()
        val uploads = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalPickedImageForTests provides { next }, LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) {
                    PhotoEditor(
                        photoUrl = null,
                        hasOwnPhoto = false,
                        name = "Ada",
                        onUpload = { contentType, _ ->
                            uploads += contentType
                            UiText.Plain("Upload failed")
                        },
                        onRemove = { null },
                        uploadNotice = "Your photo will be public.",
                    )
                }
            }
        }

        clock += 1.seconds
        onNodeWithText("Edit photo").performClick()
        onNodeWithText("Your photo will be public.").assertExists()
        clock += 1.seconds
        onAllNodesWithText("Cancel").fetchSemanticsNodes().let { onAllNodesWithText("Cancel")[it.size - 1].performClick() }
        waitForIdle()

        clock += 1.seconds
        onNodeWithText("Edit photo").performClick()
        clock += 1.seconds
        onNodeWithText("Accept and choose").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose a JPEG, PNG, WebP, or GIF image.").fetchSemanticsNodes().isNotEmpty() }

        next = SelectedImage("big.png", "image/png", ByteArray(5 * 1024 * 1024 + 1))
        clock += 1.seconds
        onNodeWithText("Edit photo").performClick()
        clock += 1.seconds
        onNodeWithText("Accept and choose").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Choose a photo that is 5 MB or smaller.").fetchSemanticsNodes().isNotEmpty() }

        next = png()
        clock += 1.seconds
        onNodeWithText("Edit photo").performClick()
        clock += 1.seconds
        onNodeWithText("Accept and choose").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Use crop") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        clock += 1.seconds
        onNodeWithText("Use crop").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Upload failed").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("image/png"), uploads)
    }

    @Test
    fun thePencilOfAnOwnPhotoOpensEditAndRemove() = runComposeUiTest {
        val clock = TestTimeSource()
        var removes = 0
        next = png()
        setContent {
            CompositionLocalProvider(LocalPickedImageForTests provides { next }, LocalPressTimeSource provides clock) {
                MtsTheme(useDarkTheme = false) {
                    PhotoEditor(
                        photoUrl = "https://example.test/me.png",
                        hasOwnPhoto = true,
                        name = "Ada",
                        onUpload = { _, _ -> null },
                        onRemove = {
                            removes++
                            null
                        },
                        editBadge = true,
                    )
                }
            }
        }

        clock += 1.seconds
        onNodeWithContentDescription("Edit photo").performClick()
        clock += 1.seconds
        onNodeWithText("Edit photo").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Crop photo").fetchSemanticsNodes().isNotEmpty() }
        clock += 1.seconds
        onAllNodesWithText("Cancel").fetchSemanticsNodes().let { onAllNodesWithText("Cancel")[it.size - 1].performClick() }
        waitForIdle()

        clock += 1.seconds
        onNodeWithContentDescription("Edit photo").performClick()
        clock += 1.seconds
        onNodeWithText("Remove photo").performClick()
        waitUntil(timeoutMillis = 5_000) { removes == 1 }
    }
}
