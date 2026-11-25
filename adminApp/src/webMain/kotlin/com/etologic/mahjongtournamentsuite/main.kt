package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.presentation.platform.BrowserSessionStorage
import kotlinx.browser.localStorage
import kotlinx.browser.window

private external fun decodeURIComponent(value: String): String

/**
 * True when the page asked for the accessibility tree of Compose in the DOM: the web E2E tests (`e2e/web`) set
 * `mts.a11y` to `on` in localStorage, so they find the texts and buttons by their roles and names.
 */
private fun accessibilityTreeRequested(): Boolean = runCatching { localStorage.getItem("mts.a11y") == "on" }.getOrDefault(false)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZones()
    val parameters = window.location.search
        .removePrefix("?")
        .split('&')
        .mapNotNull { value ->
            val parts = value.split('=', limit = 2)
            parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { it to parts.getOrElse(1) { "" } }
        }
        .toMap()
    ComposeViewport(configure = { isA11YEnabled = accessibilityTreeRequested() }) {
        when (parameters["standalone"]) {
            "timer" -> TimerApp(parameters["tournamentId"]?.ifEmpty { null }, parameters["initialRound"]?.toIntOrNull() ?: 1)
            "rankings" -> RankingApp(parameters["tournamentId"].orEmpty())
            "table-qr" -> TableQrApp(
                tournamentName = decodeURIComponent(parameters["name"].orEmpty()),
                link = decodeURIComponent(parameters["link"].orEmpty()),
                logoUrl = parameters["logo"]?.let(::decodeURIComponent)?.ifBlank { null },
                organizerName = decodeURIComponent(parameters["organizer"].orEmpty()),
                organizerColor = decodeURIComponent(parameters["color"].orEmpty()),
            )
            else -> App(remember { BrowserSessionStorage("adminApp") }, appVersion = AppVersion.web)
        }
    }
}
