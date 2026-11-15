package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The Android app holds both applications of the suite and sets this while it shows them. The two applications switch
 * inside that app, and the admin part has no sign-in of its own, like on the web: it goes back to the player part with
 * [SuiteAppHost.switchTo].
 */
object SuiteAppHost {
    /** Shows [SuiteApp]. With `signIn` true the player part opens its sign-in form. */
    var switchTo: ((app: SuiteApp, signIn: Boolean) -> Unit)? = null
}

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = remember { { app -> SuiteAppHost.switchTo?.invoke(app, false) } }

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean {
    val host = SuiteAppHost.switchTo ?: return false
    host(SuiteApp.PLAYER, signIn)
    return true
}
