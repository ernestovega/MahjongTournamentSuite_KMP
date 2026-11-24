package com.etologic.mahjongtournamentsuite

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.etologic.mahjongtournamentsuite.player.SiteLink
import com.etologic.mahjongtournamentsuite.player.siteLinkOf

/**
 * The one Android app of the suite. It opens the player part, which everyone uses. Admins and editors switch to the
 * admin part with the button of the top bar, and back. See [SuiteRoot].
 */
class MainActivity : ComponentActivity() {
    /** A start request that came with a new intent: the part to show and whether the player part opens its sign-in. */
    private val startRequest = mutableStateOf<SuiteStart?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A page of the site that is not the app, such as the privacy policy, goes to the browser.
        if (savedInstanceState == null && openPageInBrowser(intent)) {
            finish()
            return
        }
        initAndroidApp(applicationContext)
        useAppThemeWindowBackground()
        // A new activity starts where the intent says. A restored one keeps the part that was open.
        val initial = if (savedInstanceState == null) intent.suiteStart() else null

        setContent {
            SuiteRoot(
                initial = initial,
                startRequest = startRequest.value,
                onStartHandled = { startRequest.value = null },
                post = { action -> Handler(Looper.getMainLooper()).post(action) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openPageInBrowser(intent)) return
        intent.suiteStart()?.let { startRequest.value = it }
    }

    companion object {
        /** `admin` opens the admin part. The UI tests of the admin part start the app with it. */
        const val EXTRA_SECTION = "mts.section"
    }
}

private fun Intent.suiteStart(): SuiteStart? {
    linkStart()?.let { return it }
    return when (getStringExtra(MainActivity.EXTRA_SECTION)) {
        "admin" -> SuiteStart(SuiteSection.ADMIN)
        "player" -> SuiteStart(SuiteSection.PLAYER)
        else -> null
    }
}

/** The address of the site of a VIEW intent (App Links: the app takes every address of mts.mahjongmadrid.com). */
private fun Intent.siteLink(): SiteLink? {
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
    return siteLinkOf(uri.path.orEmpty(), query)
}

/**
 * A link of the web opens where the web opens it (docs/table-qr.md, "Android app"): the tournaments, a tournament,
 * its registration, the table of the player, the support form or the management part.
 */
private fun Intent.linkStart(): SuiteStart? = when (val link = siteLink()) {
    null, SiteLink.Browser -> null
    SiteLink.Home -> SuiteStart(SuiteSection.PLAYER)
    SiteLink.Admin -> SuiteStart(SuiteSection.ADMIN)
    SiteLink.Support -> SuiteStart(SuiteSection.PLAYER, support = true)
    is SiteLink.Tournament -> SuiteStart(SuiteSection.PLAYER, tournamentId = link.tournamentId)
    is SiteLink.Registration -> SuiteStart(SuiteSection.PLAYER, link = link.link)
}

/**
 * Sends a page of the site that is not the app to the browser, and returns true. The browser is named, because the
 * address would come back to this app otherwise.
 */
private fun ComponentActivity.openPageInBrowser(intent: Intent): Boolean {
    if (intent.siteLink() != SiteLink.Browser) return false
    val uri = intent.data ?: return false
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    val browser = packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.packageName }
        .firstOrNull { it != packageName }
    val page = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).apply { browser?.let { setPackage(it) } }
    return try {
        startActivity(page)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
