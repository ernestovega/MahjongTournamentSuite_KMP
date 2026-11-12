package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

/** Where each application lives on the site. */
private fun SuiteApp.path(): String = when (this) {
    SuiteApp.PLAYER -> "/"
    SuiteApp.ADMIN -> "/admin/"
}

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = remember {
    { app -> window.location.assign(app.path()) }
}

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean {
    if (!window.location.pathname.startsWith("/admin")) return false
    window.location.replace(if (signIn) "/?signin=1" else "/")
    return true
}
