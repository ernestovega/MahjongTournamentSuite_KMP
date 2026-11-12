package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** The two applications of the suite. Everyone uses the player app. Admins and editors also use the admin app. */
enum class SuiteApp {
    PLAYER,
    ADMIN,
}

/**
 * Opens the other application of the suite. On the web both live on the same site: the player app at `/` and the
 * admin app at `/admin/`, with one shared session. On Android it opens the other app, or its Google Play page when it
 * is not installed. Null on the desktop, where the two applications are separate programs.
 */
@Composable
expect fun rememberAppSwitcher(): ((SuiteApp) -> Unit)?

/** Replaces the switcher of the platform. The UI tests use it, because the desktop has none. */
val LocalSuiteAppSwitcher = staticCompositionLocalOf<((SuiteApp) -> Unit)?> { null }

/** The switcher of [LocalSuiteAppSwitcher], or else the one of the platform. */
@Composable
fun currentAppSwitcher(): ((SuiteApp) -> Unit)? = LocalSuiteAppSwitcher.current ?: rememberAppSwitcher()

/**
 * The admin app on the web has no sign-in of its own: the site signs in through the player app. Goes to the player
 * app, to its sign-in when [signIn] is true, and returns true. Returns false where the admin app keeps its own sign-in:
 * Android, the desktop, and the local dev server, which serves the admin app at the root.
 */
expect fun leaveAdminForPlayerApp(signIn: Boolean): Boolean
