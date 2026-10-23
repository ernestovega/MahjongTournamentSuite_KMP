package com.etologic.mahjongtournamentsuite.presentation.platform

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.image_error_crop
import mahjongtournamentsuite.ui.generated.resources.image_error_decode
import mahjongtournamentsuite.ui.generated.resources.image_error_invalid
import mahjongtournamentsuite.ui.generated.resources.image_error_open
import mahjongtournamentsuite.ui.generated.resources.image_error_read

/** An error of the image picker or the crop. [text] is the message for the user. */
class ImageException(val text: UiText) : Exception()

internal val ImageReadError: UiText = UiText.of(Res.string.image_error_read)
internal val ImageOpenError: UiText = UiText.of(Res.string.image_error_open)
internal val ImageDecodeError: UiText = UiText.of(Res.string.image_error_decode)
internal val ImageCropError: UiText = UiText.of(Res.string.image_error_crop)
internal val ImageInvalidError: UiText = UiText.of(Res.string.image_error_invalid)

/** Gives the message of an [ImageException], or [fallback] for any other error. */
internal fun Throwable.toImageUiText(fallback: UiText): UiText = (this as? ImageException)?.text ?: fallback

/** Gives the message for an error code from the browser code. */
internal fun imageErrorFromCode(code: String): UiText = when (code) {
    "decode" -> ImageDecodeError
    "invalid" -> ImageInvalidError
    "crop" -> ImageCropError
    else -> ImageReadError
}
