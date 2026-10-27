package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.etologic.mahjongtournamentsuite.presentation.platform.BrowserSessionStorage
import kotlinx.browser.window

private external fun decodeURIComponent(value: String): String

/** Reads the `tournament` of a registration link such as `/?tournament=...`. */
private fun tournamentFromUrl(): String? = window.location.search.removePrefix("?").split('&')
    .map { it.split('=', limit = 2) }
    .firstOrNull { it.firstOrNull() == "tournament" }
    ?.getOrNull(1)
    ?.let { decodeURIComponent(it) }
    ?.takeIf { it.isNotBlank() }

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZones()
    ComposeViewport {
        PlayerApp(
            openTournamentId = tournamentFromUrl(),
            sessionStorage = remember { BrowserSessionStorage("playerApp") },
        )
    }
}
