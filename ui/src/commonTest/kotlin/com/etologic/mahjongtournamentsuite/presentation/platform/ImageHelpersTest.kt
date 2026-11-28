package com.etologic.mahjongtournamentsuite.presentation.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The image errors from the browser code and the images that it sends as Base64. */
class ImageHelpersTest {
    @Test
    fun eachBrowserErrorCodeHasItsMessage() {
        assertEquals(ImageDecodeError, imageErrorFromCode("decode"))
        assertEquals(ImageInvalidError, imageErrorFromCode("invalid"))
        assertEquals(ImageCropError, imageErrorFromCode("crop"))
        assertEquals(ImageReadError, imageErrorFromCode("other"))
    }

    @Test
    fun anImageInBase64WithoutTypeIsAJpeg() {
        val image = selectedImageFromBase64("me", "", "AQI=")
        assertEquals("image/jpeg", image.contentType)
        assertContentEquals(byteArrayOf(1, 2), image.bytes)
        assertEquals("image/png", selectedImageFromBase64("me.png", "image/png", "").contentType)
    }
}
