package com.etologic.mahjongtournamentsuite

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext
import com.etologic.mahjongtournamentsuite.data.session.storedSessionUid
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.player.PlayerApp
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalSuiteAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteAppHost

/** The two parts of the app. The names are saved, so they must not change. */
enum class SuiteSection {
    PLAYER,
    ADMIN,
}

/** Where the app starts. [signIn] opens the sign-in form of the player part. */
data class SuiteStart(val section: SuiteSection, val signIn: Boolean = false)

/**
 * Holds the player application and the admin application, one at a time, like the two apps of the web site.
 * - The player part opens first. Its top bar shows the admin button only to admins and editors.
 * - The two parts share the session, which is saved in a file of the app, so one sign-in is enough.
 * - Each part keeps its screens while the other one is open. Leaving the admin part after a sign-out, or without the
 *   rights, starts the player part again, because its screens belong to the old session. The admin part keeps its
 *   screens only for the same account.
 * - The admin part has no sign-in of its own: it sends the user to the sign-in of the player part.
 * - Back on the first screen of the admin part returns to the player part.
 */
@Composable
fun SuiteRoot(
    /** Where a new activity starts. Null opens the player part. */
    initial: SuiteStart?,
    /** A start request that came later, with a new intent. */
    startRequest: SuiteStart?,
    onStartHandled: () -> Unit,
    /** Runs a change of part on the main thread. Some screens leave the admin part from a background coroutine. */
    post: (() -> Unit) -> Unit,
) {
    // The first part is known before the first frame, so the other part never starts for nothing.
    var section by rememberSaveable { mutableStateOf(initial?.section ?: SuiteSection.PLAYER) }
    var playerSignIn by remember { mutableStateOf(initial?.signIn == true) }
    // A new number starts the player part again, without the screens of the old session.
    var playerRun by rememberSaveable { mutableIntStateOf(0) }
    // The account whose screens the admin part keeps.
    var adminAccount by rememberSaveable { mutableStateOf(if (initial?.section == SuiteSection.ADMIN) storedSessionUid() else null) }
    val savedParts = rememberSaveableStateHolder()

    fun show(target: SuiteSection, signIn: Boolean, fresh: Boolean) {
        if (target == SuiteSection.PLAYER && (fresh || signIn)) {
            savedParts.removeState(SuiteSection.PLAYER.name + playerRun)
            playerRun++
        }
        if (target == SuiteSection.ADMIN) {
            val account = storedSessionUid()
            if (account != adminAccount) {
                savedParts.removeState(SuiteSection.ADMIN.name + adminAccount)
                adminAccount = account
            }
        }
        playerSignIn = signIn
        section = target
    }

    LaunchedEffect(startRequest) {
        val request = startRequest ?: return@LaunchedEffect
        show(request.section, request.signIn, fresh = request.signIn)
        onStartHandled()
    }

    DisposableEffect(Unit) {
        // The admin part leaves for the player part after a sign-out or without the rights of an admin or an editor.
        val host: (SuiteApp, Boolean) -> Unit = { app, signIn ->
            post { show(app.toSection(), signIn, fresh = app == SuiteApp.PLAYER) }
        }
        SuiteAppHost.switchTo = host
        // A new activity, after a rotation for example, may have set its own already.
        onDispose { if (SuiteAppHost.switchTo === host) SuiteAppHost.switchTo = null }
    }

    // The buttons of the top bars switch between the two parts and keep the screens of each one.
    val switcher: (SuiteApp) -> Unit = remember { { app -> show(app.toSection(), signIn = false, fresh = false) } }

    BackHandler(enabled = section == SuiteSection.ADMIN) { show(SuiteSection.PLAYER, signIn = false, fresh = false) }

    CompositionLocalProvider(LocalSuiteAppSwitcher provides switcher) {
        when (section) {
            SuiteSection.PLAYER -> key(playerRun) {
                savedParts.SaveableStateProvider(SuiteSection.PLAYER.name + playerRun) {
                    // The telemetry of each part starts with the name of the part. See AndroidTelemetry.
                    AndroidPlatformContext.currentApp = "player"
                    // The app has one version for both parts: the admin version. See docs/versioning.md.
                    PlayerApp(appVersion = AppVersion.admin, openSignIn = playerSignIn)
                }
            }
            SuiteSection.ADMIN -> key(adminAccount) {
                savedParts.SaveableStateProvider(SuiteSection.ADMIN.name + adminAccount) {
                    AndroidPlatformContext.currentApp = "admin"
                    App()
                }
            }
        }
    }
}

private fun SuiteApp.toSection(): SuiteSection = when (this) {
    SuiteApp.PLAYER -> SuiteSection.PLAYER
    SuiteApp.ADMIN -> SuiteSection.ADMIN
}
