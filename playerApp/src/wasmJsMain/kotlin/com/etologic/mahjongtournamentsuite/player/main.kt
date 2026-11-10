package com.etologic.mahjongtournamentsuite.player

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.presentation.platform.BrowserSessionStorage
import kotlinx.browser.window

private external fun decodeURIComponent(value: String): String

/**
 * Reads the tournament of a shared link such as `/t/<id>`, or of a registration link such as `/?tournament=<id>`.
 * Everything is public, so the link opens the tournament without an account.
 */
private fun tournamentFromUrl(): String? {
    val fromPath = window.location.pathname.trim('/').split('/')
        .takeIf { it.size == 2 && it[0] == "t" }
        ?.get(1)
    val fromQuery = window.location.search.removePrefix("?").split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.firstOrNull() == "tournament" }
        ?.getOrNull(1)
    return (fromPath ?: fromQuery)?.let { decodeURIComponent(it) }?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZones()
    ComposeViewport {
        PlayerApp(
            openTournamentId = tournamentFromUrl(),
            sessionStorage = remember { BrowserSessionStorage("playerApp") },
            appVersion = AppVersion.web,
        )
    }
}
