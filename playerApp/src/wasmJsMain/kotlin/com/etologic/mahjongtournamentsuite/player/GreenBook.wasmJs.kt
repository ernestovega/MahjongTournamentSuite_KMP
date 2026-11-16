package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

// The PDF is a file of the site, next to the app, so the browser shows it in a new tab.
// The path starts at the root because the address of the page can be a deeper route, such as /tournament/....
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return { runCatching { uriHandler.openUri(GREEN_BOOK_WEB_PATH) } }
}
