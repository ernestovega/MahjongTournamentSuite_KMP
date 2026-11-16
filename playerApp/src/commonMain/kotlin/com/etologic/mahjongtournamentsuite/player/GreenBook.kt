package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.language.AppLanguage
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.menu_green_book
import org.jetbrains.compose.resources.stringResource

/*
 * The Green Book: the official MCR rules. There are editions in English, Spanish and Russian.
 * They are big (1 to 5 MB), so the site serves them and the app does not carry them. The site has them in
 * playerApp/src/wasmJsMain/resources/green-book, so they are at /green-book/ next to the web app.
 * The app carries only the short English edition of the EMA (mahjong-europe.org/portal/images/docs/mcr_EN.pdf, 366 KB),
 * so the button works without internet the first time too.
 */

/** The edition of the Green Book for [language]: Spanish and Russian have their own, the other languages use English. */
internal fun greenBookEdition(language: AppLanguage): String = when (language) {
    AppLanguage.Spanish -> "ES"
    AppLanguage.Russian -> "RU"
    else -> "EN"
}

/** The file name of an edition, the same on the site and on the phone. */
internal fun greenBookFileName(edition: String): String = "GreenBook_$edition.pdf"

/** Where the site serves an edition. It starts at the root, because the page can be at a deeper route. */
internal fun greenBookWebPath(edition: String): String = "/green-book/${greenBookFileName(edition)}"

/** The short English edition of the EMA, inside the app. */
internal const val GREEN_BOOK_BUNDLED_FILE = "files/GreenBook_EN.pdf"

/** Opens the Green Book in the language of the app: in a PDF app on Android, in a new tab on the web. */
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
