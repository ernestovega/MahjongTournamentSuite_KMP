package com.etologic.mahjongtournamentsuite.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

/**
 * The old player app. The player part now lives in the one app of the suite (`com.etologic.mahjongtournamentsuite`),
 * so this app only gets a last version that asks the user to install that one. See docs/android-release.md.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)
        setContent { PlayerApp(onInstallSuiteApp = ::openSuiteApp) }
    }

    /** Opens the new app when it is installed, or else its page in the Play Store app, or in the browser. */
    private fun openSuiteApp() {
        val candidates = listOfNotNull(
            packageManager.getLaunchIntentForPackage(SUITE_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SUITE_PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SUITE_PACKAGE")),
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // The next way.
            }
        }
    }

    private companion object {
        const val SUITE_PACKAGE = "com.etologic.mahjongtournamentsuite"
    }
}
