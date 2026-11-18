package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.Composable
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryLink
import com.etologic.mahjongtournamentsuite.presentation.language.LocalLanguageController
import com.etologic.mahjongtournamentsuite.presentation.telemetry.rememberOpenLink

// The JVM target only runs the tests. It opens the PDF like the web does.
@Composable
internal actual fun rememberOpenGreenBook(): () -> Unit {
    val open = rememberOpenLink(TelemetryLink.GREEN_BOOK)
    val edition = greenBookEdition(LocalLanguageController.current.language)
    return { open(greenBookAddress(edition)) }
}
