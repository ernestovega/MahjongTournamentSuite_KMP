package com.etologic.mahjongtournamentsuite.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mahjongtournamentsuite.playerapp.generated.resources.Res

/** The copy of the web, for a phone without a PDF app. The browser downloads it. */
private const val GREEN_BOOK_WEB_URL =
    "https://mts.mahjongmadrid.com$GREEN_BOOK_WEB_PATH"

/**
 * Other apps cannot read the files inside the app, so the PDF is copied to the cache and shared through the
 * FileProvider of the manifest of playerApp. The PDF app of the phone shows it.
 */
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    return remember(context) {
        {
            scope.launch {
                val opened = runCatching {
                    val file = withContext(Dispatchers.IO) { greenBookCopy(context) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.greenbook", file)
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/pdf")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                if (opened.exceptionOrNull() is ActivityNotFoundException) {
                    runCatching { uriHandler.openUri(GREEN_BOOK_WEB_URL) }
                }
            }
        }
    }
}

private suspend fun greenBookCopy(context: Context): File {
    val file = File(File(context.cacheDir, "greenbook").apply { mkdirs() }, "GreenBook_EN.pdf")
    val bytes = Res.readBytes(GREEN_BOOK_FILE)
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    return file
}

/** Its own class, so its manifest entry never clashes with the FileProvider of another library. */
class GreenBookFileProvider : FileProvider()
