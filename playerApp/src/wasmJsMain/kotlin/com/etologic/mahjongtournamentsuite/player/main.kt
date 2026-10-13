package com.etologic.mahjongtournamentsuite.player

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

/** Reads the `token` of an invitation link such as `/invite?token=...`. */
private fun inviteTokenFromUrl(): String? {
    if (!window.location.pathname.trimEnd('/').endsWith("/invite")) return null
    return window.location.search.removePrefix("?").split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.firstOrNull() == "token" }
        ?.getOrNull(1)
        ?.let { decodeURIComponent(it) }
        ?.takeIf { it.isNotBlank() }
}

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
    ComposeViewport {
        PlayerApp(inviteToken = inviteTokenFromUrl(), openTournamentId = tournamentFromUrl())
    }
}
