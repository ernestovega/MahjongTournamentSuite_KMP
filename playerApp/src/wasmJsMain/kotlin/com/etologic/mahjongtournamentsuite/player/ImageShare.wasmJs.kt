package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.io.encoding.Base64
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodePng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("The image cannot be encoded.")

/**
 * The share sheet of the browser when it can share files (phones and most tablets), otherwise a download of the PNG.
 * A share that the user closes is not an error, so it does not download the image either.
 */
@Composable
internal actual fun rememberShareImage(onError: () -> Unit): (fileName: String, png: ByteArray, title: String) -> Unit {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        { fileName, png, title ->
            runCatching { shareOrDownloadImage(fileName, Base64.Default.encode(png), title) }
                .onFailure { currentOnError.value() }
        }
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, base64, title) => {
        const binary = atob(base64);
        const data = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) data[i] = binary.charCodeAt(i);
        const blob = new Blob([data], { type: 'image/png' });
        const download = () => {
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        };
        let file = null;
        try { file = new File([blob], fileName, { type: 'image/png' }); } catch (e) { file = null; }
        if (file && navigator.canShare && navigator.share && navigator.canShare({ files: [file] })) {
            navigator.share({ files: [file], title: title }).catch((error) => {
                if (!error || error.name !== 'AbortError') download();
            });
        } else {
            download();
        }
    }""",
)
private external fun shareOrDownloadImage(fileName: String, base64: String, title: String)
