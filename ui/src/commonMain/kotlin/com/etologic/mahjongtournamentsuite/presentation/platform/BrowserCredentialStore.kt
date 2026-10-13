package com.etologic.mahjongtournamentsuite.presentation.platform

expect fun saveBrowserCredentials(email: String, password: String)

expect fun loadBrowserCredentials(onLoaded: (email: String, password: String) -> Unit)
