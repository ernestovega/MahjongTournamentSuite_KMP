package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = null

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean = false
