package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import mahjongtournamentsuite.playerapp.generated.resources.Res

// The JVM target only runs the tests. It opens the file like the web does.
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return { runCatching { uriHandler.openUri(Res.getUri(GREEN_BOOK_FILE)) } }
}
