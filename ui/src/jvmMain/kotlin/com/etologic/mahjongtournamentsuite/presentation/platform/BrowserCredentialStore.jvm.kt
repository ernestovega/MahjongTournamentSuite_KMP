package com.etologic.mahjongtournamentsuite.presentation.platform

actual fun saveBrowserCredentials(email: String, password: String) = Unit

actual fun loadBrowserCredentials(onLoaded: (email: String, password: String) -> Unit) = Unit
