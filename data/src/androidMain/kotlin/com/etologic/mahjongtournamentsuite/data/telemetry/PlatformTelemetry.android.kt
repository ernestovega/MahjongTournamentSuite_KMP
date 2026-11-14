package com.etologic.mahjongtournamentsuite.data.telemetry

import android.content.pm.ApplicationInfo
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun providePlatformTelemetry(): PlatformTelemetry {
    val context = AndroidPlatformContext.requireContext()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return createAndroidTelemetry(context, debuggable)
}

/** The callback of the last part that started. The handler goes in one time, so a crash is reported one time. */
@Volatile
private var crashCallback: ((Throwable) -> Unit)? = null

actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    // The app of the suite starts the telemetry again each time it switches between the admin and the player part.
    val first = crashCallback == null
    crashCallback = onCrash
    if (!first) return
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        crashCallback?.invoke(throwable)
        previous?.uncaughtException(thread, throwable)
    }
}
