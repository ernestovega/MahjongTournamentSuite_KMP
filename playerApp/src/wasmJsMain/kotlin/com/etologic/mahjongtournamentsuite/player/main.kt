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

/**
 * True for `/?signin=1`: the admin app sends there a user without a session, because the site has one sign-in.
 * The parameter leaves the address, so a page refresh does not open the sign-in again.
 */
private fun signInFromUrl(): Boolean {
    val parameters = window.location.search.removePrefix("?").split('&')
    if ("signin=1" !in parameters) return false
    val rest = parameters.filter { it.isNotBlank() && it != "signin=1" }
    val search = if (rest.isEmpty()) "" else rest.joinToString("&", prefix = "?")
    window.history.replaceState(null, "", window.location.pathname + search + window.location.hash)
    return true
}

/**
 * True for `/support`: the public address of the support form, linked from the privacy policy.
 * The address goes back to `/`, so Back of the browser returns to the tournaments.
 */
private fun supportFromUrl(): Boolean {
    if (window.location.pathname.trim('/') != "support") return false
    window.history.replaceState(null, "", "/" + window.location.search + window.location.hash)
    return true
}

/**
 * The public link of a tournament, `/<slug>` (such as `/6thmmc`), or the link of the emails, `/registration?t=&k=`
 * (also the old `/inscripcion/...`). See registrationLinkOf. The token of the emails leaves the address at once, so it
 * is not kept in the history of the browser, and the address goes back to `/`.
 */
private fun registrationFromUrl(): RegistrationLink? {
    val query = window.location.search.removePrefix("?").split('&')
        .filter { it.isNotEmpty() }
        .map { it.split('=', limit = 2) }
        .associate { it[0] to decodeURIComponent(it.getOrElse(1) { "" }) }
    val link = registrationLinkOf(decodeURIComponent(window.location.pathname), query) ?: return null
    window.history.replaceState(null, "", "/" + window.location.hash)
    return link
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZones()
    val openSignIn = signInFromUrl()
    val openSupport = supportFromUrl()
    val openRegistration = registrationFromUrl()
    ComposeViewport {
        PlayerApp(
            openTournamentId = tournamentFromUrl(),
            sessionStorage = remember { BrowserSessionStorage("playerApp") },
            appVersion = AppVersion.web,
            openSignIn = openSignIn,
            openSupport = openSupport,
            openRegistration = openRegistration,
        )
    }
}
