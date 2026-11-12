package com.etologic.mahjongtournamentsuite.presentation.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

/** The package names of the two Android apps. Each manifest lists the other one in `<queries>`, so it can be found. */
private fun SuiteApp.packageName(): String = when (this) {
    SuiteApp.PLAYER -> "com.etologic.mahjongtournamentsuite.player"
    SuiteApp.ADMIN -> "com.etologic.mahjongtournamentsuite"
}

@Composable
actual fun rememberAppSwitcher(): ((SuiteApp) -> Unit)? {
    val context = AndroidPlatformContext.requireContext()
    return remember(context) { { app -> openSuiteApp(context, app) } }
}

/** Opens the installed app. Without it, opens its page in the Play Store app, or in the browser without the Play Store. */
private fun openSuiteApp(context: Context, app: SuiteApp) {
    val packageName = app.packageName()
    val installed = context.packageManager.getLaunchIntentForPackage(packageName)
    val candidates = listOfNotNull(
        installed,
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // The next way.
        }
    }
}

actual fun leaveAdminForPlayerApp(signIn: Boolean): Boolean = false
