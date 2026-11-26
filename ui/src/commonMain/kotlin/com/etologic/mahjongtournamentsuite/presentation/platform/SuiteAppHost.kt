package com.etologic.mahjongtournamentsuite.presentation.platform

/**
 * The Android and iOS apps hold both applications of the suite and set this while they show them. The two applications
 * switch inside that app, and the admin part has no sign-in of its own, like on the web: it goes back to the player
 * part with [SuiteAppHost.switchTo]. The web and the desktop never set it.
 */
object SuiteAppHost {
    /** Shows [SuiteApp]. With `signIn` true the player part opens its sign-in form. */
    var switchTo: ((app: SuiteApp, signIn: Boolean) -> Unit)? = null
}
