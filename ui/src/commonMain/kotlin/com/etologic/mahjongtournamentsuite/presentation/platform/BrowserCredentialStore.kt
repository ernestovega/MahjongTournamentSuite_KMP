package com.etologic.mahjongtournamentsuite.presentation.platform

expect fun saveBrowserCredentials(email: String, password: String)

expect fun loadBrowserCredentials(onLoaded: (email: String, password: String) -> Unit)

/**
 * After a sign-out the browser must not hand the saved password again without asking.
 * Otherwise the sign-in screen, which signs in by itself with the saved password, would sign the user back in.
 */
expect fun preventBrowserAutoSignIn()
