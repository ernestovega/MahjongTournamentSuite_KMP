package com.etologic.mahjongtournamentsuite.data.platform

import android.content.Context
import com.etologic.mahjongtournamentsuite.data.network.AndroidAppCheck

object AndroidPlatformContext {
    private var appContext: Context? = null

    /**
     * `admin` or `player`: the part of the app that is on the screen. The Android app holds both parts and sets it
     * before each part starts, so the telemetry of that part tells them apart. Null in the old player app, which
     * holds only the player part.
     */
    @Volatile
    var currentApp: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        AndroidAppCheck.install(context.applicationContext)
    }

    fun requireContext(): Context =
        appContext ?: error("AndroidPlatformContext not initialized. Call AndroidPlatformContext.init(context) in androidApp.")
}
