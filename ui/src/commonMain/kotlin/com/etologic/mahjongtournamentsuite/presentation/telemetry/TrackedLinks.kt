package com.etologic.mahjongtournamentsuite.presentation.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam

/**
 * Opens a web page, a map or an email outside the app and counts `link_opened` with [kind], one word of
 * `TelemetryLink`. Every link of both apps goes through here: `AnalyticsCoverageTest` fails on another use of
 * `LocalUriHandler`. The address itself is never sent.
 */
@Composable
fun rememberOpenLink(kind: String): (url: String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val telemetry = LocalTelemetry.current
    return { url ->
        if (runCatching { uriHandler.openUri(url) }.isSuccess) telemetry.linkOpened(kind)
    }
}

/** Counts a link that the app opened in its own way, for example the Green Book that Android downloads first. */
fun Telemetry.linkOpened(kind: String) = event(TelemetryEvent.LINK_OPENED, mapOf(TelemetryParam.KIND to kind))
