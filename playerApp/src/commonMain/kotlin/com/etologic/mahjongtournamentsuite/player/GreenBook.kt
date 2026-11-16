package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.menu_green_book
import org.jetbrains.compose.resources.stringResource

/**
 * The Green Book of the EMA, the official MCR rules. The app carries the PDF, so it opens without internet at a
 * tournament. It is in English for every language of the app. Source: mahjong-europe.org/portal/images/docs/mcr_EN.pdf.
 */
internal const val GREEN_BOOK_FILE = "files/GreenBook_EN.pdf"

/** Where the web serves it: the folder of the resources of the app, at the root of the site. */
internal const val GREEN_BOOK_WEB_PATH = "/composeResources/mahjongtournamentsuite.playerapp.generated.resources/$GREEN_BOOK_FILE"

/** Opens the PDF of the Green Book: in a PDF app on Android, in a new tab on the web. */
@Composable
internal expect fun rememberOpenGreenBook(): () -> Unit

/** The Green Book button, for the actions of an AppScaffold. */
@Composable
fun GreenBookButton() {
    val openGreenBook = rememberOpenGreenBook()
    AppTopBarButton(
        text = stringResource(Res.string.menu_green_book),
        icon = Icons.Default.Book,
        onClick = openGreenBook,
    )
}
