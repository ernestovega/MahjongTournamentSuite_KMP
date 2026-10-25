package com.etologic.mahjongtournamentsuite.data.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * App Check for Android, with Play Integrity. The app manifest holds the settings of the Firebase app as
 * `mts.appCheck.appId` and `mts.appCheck.apiKey`. Without them App Check stays off and the app sends no token.
 * A debuggable build uses the debug provider, whose token must be registered in the Firebase console.
 */
object AndroidAppCheck {
    private const val APP_NAME = "mts-app-check"
    private const val PROJECT_ID = "mahjong-tournament-suite"

    @Volatile
    private var appCheck: FirebaseAppCheck? = null

    fun install(context: Context) {
        if (appCheck != null) return
        runCatching {
            val metaData = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData ?: return
            val appId = metaData.getString("mts.appCheck.appId").orEmpty()
            val apiKey = metaData.getString("mts.appCheck.apiKey").orEmpty()
            if (appId.isBlank() || apiKey.isBlank()) return
            val app = runCatching { FirebaseApp.getInstance(APP_NAME) }.getOrElse {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder().setApplicationId(appId).setApiKey(apiKey).setProjectId(PROJECT_ID).build(),
                    APP_NAME,
                )
            }
            val instance = FirebaseAppCheck.getInstance(app)
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            instance.installAppCheckProviderFactory(
                if (debuggable) DebugAppCheckProviderFactory.getInstance() else PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
            appCheck = instance
        }
    }

    suspend fun token(): String? {
        val instance = appCheck ?: return null
        return suspendCoroutine { continuation ->
            instance.getAppCheckToken(false)
                .addOnSuccessListener { continuation.resume(it.token) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}
