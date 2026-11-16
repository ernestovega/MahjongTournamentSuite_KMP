package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController

// The JVM target only runs the tests. It opens the PDF like the web does.
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    val edition = greenBookEdition(LocalLanguageController.current.language)
    return { runCatching { uriHandler.openUri(greenBookAddress(edition)) } }
}
