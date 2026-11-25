package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = remember { { app -> SuiteAppHost.switchTo?.invoke(app, false) } }

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean {
    val host = SuiteAppHost.switchTo ?: return false
    host(SuiteApp.PLAYER, signIn)
    return true
}

actual val appSwitchLeavesPage: Boolean = false
