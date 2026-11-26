package com.etologic.mahjongtournamentsuite

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
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.player.PlayerApp
import com.etologic.mahjongtournamentsuite.player.RegistrationLink
import com.etologic.mahjongtournamentsuite.player.SiteLink
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformBackHandler
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalSuiteAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteAppHost

/** The two parts of the app. The names are saved, so they must not change. */
enum class SuiteSection {
    PLAYER,
    ADMIN,
}

/**
 * Where the app starts. [signIn] opens the sign-in form of the player part. [link] is a link of the web that the
 * player part opens, such as the QR of a table. [tournamentId] opens a tournament, like `/t/<id>` on the web, and
 * [support] the support form, like `/support`.
 */
data class SuiteStart(
    val section: SuiteSection,
    val signIn: Boolean = false,
    val link: RegistrationLink? = null,
    val tournamentId: String? = null,
    val support: Boolean = false,
) {
    /** True when the player part opens something of a link of the web, so it starts again over the tournaments. */
    val opensSomething: Boolean get() = link != null || tournamentId != null || support
}

/**
 * Holds the player application and the admin application, one at a time, like the two apps of the web site. The
 * Android app and the iOS app show it.
 * - The player part opens first. Its top bar shows the admin button only to admins and editors.
 * - The two parts share the session, which is saved in a file of the app, so one sign-in is enough.
 * - Each part keeps its screens while the other one is open. Leaving the admin part after a sign-out, or without the
 *   rights, starts the player part again, because its screens belong to the old session. The admin part keeps its
 *   screens only for the same account.
 * - Each part has its own Koin graph, because the player part reads the public data with other repositories.
 * - The admin part has no sign-in of its own: it sends the user to the sign-in of the player part.
 * - Back on the first screen of the admin part returns to the player part.
 */
@Composable
fun SuiteRoot(
    /** Where the app starts. Null opens the player part. */
    initial: SuiteStart?,
    /** A start request that came later: a link that opened the running app. */
    startRequest: SuiteStart?,
    onStartHandled: () -> Unit,
    /** Runs a change of part on the main thread. Some screens leave the admin part from a background coroutine. */
    post: (() -> Unit) -> Unit,
    /** The account of the saved session, or null without one. */
    storedSessionUid: () -> String?,
    /** Tells the platform which part is on the screen (`player` or `admin`), for the telemetry of that part. */
    onPartShown: (String) -> Unit,
) {
    // The first part is known before the first frame, so the other part never starts for nothing.
    var section by rememberSaveable { mutableStateOf(initial?.section ?: SuiteSection.PLAYER) }
    var playerSignIn by remember { mutableStateOf(initial?.signIn == true) }
    // A link of the web that the player part opens when it starts. It is used once.
    var playerEntry by remember { mutableStateOf(initial?.takeIf { it.opensSomething }) }
    // A new number starts the player part again, without the screens of the old session.
    var playerRun by rememberSaveable { mutableIntStateOf(0) }
    // The account whose screens the admin part keeps.
    var adminAccount by rememberSaveable { mutableStateOf(if (initial?.section == SuiteSection.ADMIN) storedSessionUid() else null) }
    val savedParts = rememberSaveableStateHolder()

    fun show(target: SuiteSection, signIn: Boolean, fresh: Boolean, entry: SuiteStart? = null) {
        playerEntry = entry
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
        // A link starts the player part again, so it opens the link over the tournaments, like the web.
        show(request.section, request.signIn, fresh = request.signIn || request.opensSomething, entry = request.takeIf { it.opensSomething })
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

    PlatformBackHandler(enabled = section == SuiteSection.ADMIN) { show(SuiteSection.PLAYER, signIn = false, fresh = false) }

    CompositionLocalProvider(LocalSuiteAppSwitcher provides switcher) {
        when (section) {
            SuiteSection.PLAYER -> key(playerRun) {
                savedParts.SaveableStateProvider(SuiteSection.PLAYER.name + playerRun) {
                    // The telemetry of each part starts with the name of the part. See AndroidTelemetry.
                    onPartShown("player")
                    // The app has one version for both parts: the admin version. See docs/versioning.md.
                    // Phones and tablets type the score of a hand on the numpad of the app, not on the system keyboard.
                    PlayerApp(
                        appVersion = AppVersion.admin,
                        openSignIn = playerSignIn,
                        openTournamentId = playerEntry?.tournamentId,
                        openSupport = playerEntry?.support == true,
                        openRegistration = playerEntry?.link,
                        numpadEntry = true,
                        isolatedKoin = true,
                    )
                }
            }
            SuiteSection.ADMIN -> key(adminAccount) {
                savedParts.SaveableStateProvider(SuiteSection.ADMIN.name + adminAccount) {
                    onPartShown("admin")
                    App(isolatedKoin = true)
                }
            }
        }
    }
}

private fun SuiteApp.toSection(): SuiteSection = when (this) {
    SuiteApp.PLAYER -> SuiteSection.PLAYER
    SuiteApp.ADMIN -> SuiteSection.ADMIN
}

/**
 * Where a link of the web opens (docs/table-qr.md): the tournaments, a tournament, its registration, the table of the
 * player, the support form or the management part. Null for a page that is not the app, which goes to the browser.
 */
fun suiteStartOf(link: SiteLink): SuiteStart? = when (link) {
    SiteLink.Browser -> null
    SiteLink.Home -> SuiteStart(SuiteSection.PLAYER)
    SiteLink.Admin -> SuiteStart(SuiteSection.ADMIN)
    SiteLink.Support -> SuiteStart(SuiteSection.PLAYER, support = true)
    is SiteLink.Tournament -> SuiteStart(SuiteSection.PLAYER, tournamentId = link.tournamentId)
    is SiteLink.Registration -> SuiteStart(SuiteSection.PLAYER, link = link.link)
}
