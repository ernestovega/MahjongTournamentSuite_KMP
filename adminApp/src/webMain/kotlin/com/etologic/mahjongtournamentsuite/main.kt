package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.presentation.platform.BrowserSessionStorage
import kotlinx.browser.window

private external fun decodeURIComponent(value: String): String

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
    ComposeViewport {
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
