package com.etologic.mahjongtournamentsuite.presentation.platform

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The desktop crop of a photo: a square of the chosen part, at most 512 pixels, saved as PNG. */
class CropSelectedImageTest {
    /** A wide image: the left half is red and the right half is blue. */
    private fun halves(width: Int, height: Int): SelectedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width / 2, height)
        graphics.color = Color.BLUE
        graphics.fillRect(width / 2, 0, width - width / 2, height)
        graphics.dispose()
        val bytes = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
        return SelectedImage("me.jpg", "image/png", bytes)
    }

    private fun crop(image: SelectedImage, crop: SquareImageCrop): Pair<SelectedImage?, UiText?> {
        var cropped: SelectedImage? = null
        var error: UiText? = null
        cropSelectedImage(image, crop, onCropped = { cropped = it }, onError = { error = it })
        return cropped to error
    }

    private fun SelectedImage.decoded(): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun theLeftEndKeepsTheLeftSquareAndTheRightEndTheRightSquare() {
        val image = halves(200, 100)

        val left = assertNotNull(crop(image, SquareImageCrop(zoom = 1f, horizontalPosition = -1f, verticalPosition = 0f)).first).decoded()
        val right = assertNotNull(crop(image, SquareImageCrop(zoom = 1f, horizontalPosition = 1f, verticalPosition = 0f)).first).decoded()

        assertEquals(100, left.width)
        assertEquals(100, left.height)
        assertEquals(Color.RED.rgb, left.getRGB(50, 50))
        assertEquals(Color.BLUE.rgb, right.getRGB(50, 50))
    }

    @Test
    fun theZoomTakesASmallerSquareAndPositionsOutOfRangeStayInside() {
        val image = halves(200, 100)

        val zoomed = assertNotNull(crop(image, SquareImageCrop(zoom = 2f, horizontalPosition = -5f, verticalPosition = 9f)).first).decoded()
        val tooMuchZoom = assertNotNull(crop(image, SquareImageCrop(zoom = 10f, horizontalPosition = 0f, verticalPosition = 0f)).first).decoded()

        assertEquals(50, zoomed.width)
        assertEquals(Color.RED.rgb, zoomed.getRGB(25, 25))
        assertEquals(33, tooMuchZoom.width, "the zoom stops at 3")
    }

    @Test
    fun aBigPhotoIsSavedAt512PixelsAsPng() {
        val (cropped, error) = crop(halves(1600, 1200), SquareImageCrop(zoom = 1f, horizontalPosition = 0f, verticalPosition = 0f))

        assertNull(error)
        val result = assertNotNull(cropped)
        assertEquals("image/png", result.contentType)
        assertEquals("me-cropped.png", result.fileName)
        assertEquals(512, result.decoded().width)
        assertEquals(512, result.decoded().height)
    }

    @Test
    fun aFileThatIsNotAnImageGivesAnError() {
        val (cropped, error) = crop(SelectedImage("notes.txt", "image/png", "hello".encodeToByteArray()), SquareImageCrop(1f, 0f, 0f))

        assertNull(cropped)
        assertNotNull(error)
    }
}
