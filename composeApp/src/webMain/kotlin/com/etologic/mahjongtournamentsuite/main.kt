package com.etologic.mahjongtournamentsuite

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
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
            "timer" -> TimerApp(parameters["initialRound"]?.toIntOrNull() ?: 1)
            "rankings" -> RankingApp(parameters["tournamentId"].orEmpty())
            else -> App()
        }
    }
}
