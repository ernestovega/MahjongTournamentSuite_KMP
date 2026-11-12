package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

private const val PREFERRED_APP_KEY = "mahjongTournamentSuite.preferredApp"

/** Where each application lives on the site. The player index.html reads the same key to open the admin app at once. */
private fun SuiteApp.path(): String = when (this) {
    SuiteApp.PLAYER -> "/"
    SuiteApp.ADMIN -> "/admin/"
}

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = remember {
    { app ->
        // A remembered choice follows the last switch, so the site does not send the user back to the other app.
        if (loadPreferredSuiteApp() != null) savePreferredSuiteApp(app)
        window.location.assign(app.path())
    }
}

actual fun canRememberSuiteApp(): Boolean = true

actual fun loadPreferredSuiteApp(): SuiteApp? = runCatching { window.localStorage.getItem(PREFERRED_APP_KEY) }
    .getOrNull()
    ?.let { value -> SuiteApp.entries.firstOrNull { it.name == value } }

actual fun savePreferredSuiteApp(app: SuiteApp?) {
    runCatching {
        if (app == null) window.localStorage.removeItem(PREFERRED_APP_KEY) else window.localStorage.setItem(PREFERRED_APP_KEY, app.name)
    }
}

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean {
    if (!window.location.pathname.startsWith("/admin")) return false
    window.location.replace(if (signIn) "/?signin=1" else "/")
    return true
}
