package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? = null

actual fun canRememberSuiteApp(): Boolean = false

actual fun loadPreferredSuiteApp(): SuiteApp? = null

actual fun savePreferredSuiteApp(app: SuiteApp?) = Unit

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean = false
