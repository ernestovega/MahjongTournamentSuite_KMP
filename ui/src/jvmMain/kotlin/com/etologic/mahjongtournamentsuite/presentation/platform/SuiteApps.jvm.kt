package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The desktop app of the suite (`:suite`) holds both parts, like the Android app, and sets [isSuite] before it shows
 * them. Without it (`:adminApp:run`, the UI tests) the admin part runs on its own and keeps its own sign-in.
 */
object DesktopSuite {
    @Volatile
    var isSuite: Boolean = false
}

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = remember {
    if (DesktopSuite.isSuite) { app -> SuiteAppHost.switchTo?.invoke(app, false) } else null
}

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean {
    if (!DesktopSuite.isSuite) return false
    val host = SuiteAppHost.switchTo ?: return false
    host(SuiteApp.PLAYER, signIn)
    return true
}

actual val appSwitchLeavesPage: Boolean = false
