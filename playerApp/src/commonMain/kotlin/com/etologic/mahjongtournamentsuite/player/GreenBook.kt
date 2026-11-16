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
 * The Green Book: the official MCR rules, in English, Spanish and Russian. The editions are big (1 to 5 MB),
 * so the app does not carry them. Our site serves English and Spanish: they are in
 * playerApp/src/wasmJsMain/resources/green-book, so they are at /green-book/ next to the web app.
 * The Russian edition belongs to the Russian Mahjong Federation (all rights reserved), so we only link to their site.
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

/**
 * Where an edition is. Ours are paths of our site that start at the root, because the page can be at a deeper route.
 * The Russian one is on the site of the Russian Mahjong Federation.
 */
internal fun greenBookAddress(edition: String): String = when (edition) {
    "RU" -> "https://www.mahjong.ru/files/Green_Book_rus_2025_FM_RF_fixes.pdf"
    else -> "/green-book/${greenBookFileName(edition)}"
}

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
