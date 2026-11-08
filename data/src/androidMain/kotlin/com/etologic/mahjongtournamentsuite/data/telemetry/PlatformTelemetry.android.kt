package com.etologic.mahjongtournamentsuite.data.telemetry

import android.content.pm.ApplicationInfo
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun providePlatformTelemetry(): PlatformTelemetry {
    val context = AndroidPlatformContext.requireContext()
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return createAndroidTelemetry(context, debuggable)
}

actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        onCrash(throwable)
        previous?.uncaughtException(thread, throwable)
    }
}
