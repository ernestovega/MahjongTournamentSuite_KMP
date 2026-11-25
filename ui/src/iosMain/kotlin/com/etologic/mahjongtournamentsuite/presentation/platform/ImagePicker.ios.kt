package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** UIKit keeps a weak reference to the delegate of a picker, so the open one lives here. */
private var openImagePicker: ImagePickerDelegate? = null

/**
 * The photo picker of iOS. It needs no permission: the user only gives the photo that they choose. Photos of an
 * iPhone are HEIC, so the picker turns them into JPEG, which the server and the crop understand.
 */
@Composable
actual fun rememberImagePicker(
    onImageSelected: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
): ImagePicker {
    val selected = rememberUpdatedState(onImageSelected)
    val error = rememberUpdatedState(onError)
    return remember {
        ImagePicker {
            val configuration = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration)
            val delegate = ImagePickerDelegate(
                onImage = { selected.value(it) },
                onError = { error.value(it) },
            )
            openImagePicker = delegate
            picker.delegate = delegate
            IosViewControllers.present(picker)
        }
    }
}

private class ImagePickerDelegate(
    private val onImage: (SelectedImage) -> Unit,
    private val onError: (UiText) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        openImagePicker = null
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _ ->
            val jpeg = data?.let { UIImage(data = it) }?.let { UIImageJPEGRepresentation(it, JPEG_QUALITY) }
            dispatch_async(dispatch_get_main_queue()) {
                if (jpeg == null) {
                    onError(ImageReadError)
                } else {
                    val name = result.itemProvider.suggestedName ?: "player-photo"
                    onImage(SelectedImage(fileName = "$name.jpg", contentType = "image/jpeg", bytes = jpeg.toByteArray()))
                }
            }
        }
    }
}

private const val JPEG_QUALITY = 0.9

/** The square of the crop, at most 512 pixels, as a PNG. The same steps as the other platforms, with Skia. */
actual fun cropSelectedImage(
    image: SelectedImage,
    crop: SquareImageCrop,
    onCropped: (SelectedImage) -> Unit,
    onError: (UiText) -> Unit,
) {
    runCatching {
        val source = runCatching { Image.makeFromEncoded(image.bytes) }.getOrNull()
            ?: throw ImageException(ImageDecodeError)
        val cropSize = minOf(source.width, source.height) / crop.zoom.coerceIn(1f, 3f)
        val side = cropSize.toInt().coerceAtLeast(1)
        val left = ((source.width - cropSize) * (crop.horizontalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.width - side)
        val top = ((source.height - cropSize) * (crop.verticalPosition.coerceIn(-1f, 1f) + 1f) / 2f)
            .toInt().coerceIn(0, source.height - side)
        val outputSize = minOf(512, side)
        val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
        surface.canvas.drawImageRect(
            source,
            Rect.makeXYWH(left.toFloat(), top.toFloat(), side.toFloat(), side.toFloat()),
            Rect.makeWH(outputSize.toFloat(), outputSize.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
            null,
            true,
        )
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw ImageException(ImageCropError)
        SelectedImage(
            fileName = image.fileName.substringBeforeLast('.', image.fileName) + "-cropped.png",
            contentType = "image/png",
            bytes = bytes,
        )
    }.onSuccess(onCropped)
        .onFailure { onError(it.toImageUiText(ImageCropError)) }
}
