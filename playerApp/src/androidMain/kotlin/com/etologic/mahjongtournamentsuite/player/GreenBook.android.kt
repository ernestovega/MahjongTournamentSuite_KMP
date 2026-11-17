package com.etologic.mahjongtournamentsuite.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.FileProvider
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.green_book_downloading
import mahjongtournamentsuite.playerapp.generated.resources.green_book_offline
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val SITE = "https://mts.mahjongmadrid.com"

/**
 * The download does not belong to the button: in the menu of the top bar, the button leaves the screen as soon as
 * the menu closes, and a scope of the composition would cancel the download at that moment.
 */
private val greenBookScope = MainScope()

/**
 * The first time, the app downloads the edition of its language and keeps it on the phone, so it opens without
 * internet after that. Without internet the first time, it opens the short English edition inside the app.
 * Other apps cannot read the files of the app, so the FileProvider of the manifest of playerApp shares the PDF
 * with the PDF app of the phone.
 */
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val edition = greenBookEdition(LocalLanguageController.current.language)
    val downloadingText = stringResource(Res.string.green_book_downloading)
    val offlineText = stringResource(Res.string.green_book_offline)
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            greenBookScope.launch {
                try {
                    val kept = keptEdition(context, edition)
                    val file = if (kept.exists()) {
                        kept
                    } else {
                        Toast.makeText(context, downloadingText, Toast.LENGTH_SHORT).show()
                        withContext(Dispatchers.IO) { runCatching { download(edition, kept) }.getOrNull() }
                            ?: run {
                                Toast.makeText(context, offlineText, Toast.LENGTH_LONG).show()
                                withContext(Dispatchers.IO) { bundledCopy(context) }
                            }
                    }
                    val opened = runCatching { openPdf(context, file) }
                    if (opened.exceptionOrNull() is ActivityNotFoundException) {
                        // A phone without a PDF app: the browser downloads it from the site.
                        runCatching { uriHandler.openUri(greenBookUrl(edition)) }
                    }
                } finally {
                    busy = false
                }
            }
        }
    }
}

private fun greenBookUrl(edition: String): String = greenBookAddress(edition).let { if (it.startsWith("/")) SITE + it else it }

private fun keptEdition(context: Context, edition: String): File = File(File(context.filesDir, "greenbook").apply { mkdirs() }, greenBookFileName(edition))

/** Downloads to a temporary file first, so a cut download never stays as the kept edition. */
private fun download(edition: String, target: File): File {
    val connection = URL(greenBookUrl(edition)).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    try {
        check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
        val partial = File(target.parentFile, target.name + ".part")
        connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        // A missing file can come back as the web page of the site, so the file must start like a PDF.
        val isPdf = partial.inputStream().use { input -> ByteArray(5).also { input.read(it) }.decodeToString() == "%PDF-" }
        if (!isPdf) {
            partial.delete()
            error("Not a PDF")
        }
        check(partial.renameTo(target)) { "Cannot keep ${target.name}" }
        return target
    } finally {
        connection.disconnect()
    }
}

private suspend fun bundledCopy(context: Context): File {
    val file = File(File(context.cacheDir, "greenbook").apply { mkdirs() }, "GreenBook_EMA_EN.pdf")
    val bytes = Res.readBytes(GREEN_BOOK_BUNDLED_FILE)
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    return file
}

private fun openPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.greenbook", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Its own class, so its manifest entry never clashes with the FileProvider of another library. */
class GreenBookFileProvider : FileProvider()
