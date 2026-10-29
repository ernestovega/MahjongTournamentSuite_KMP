package com.etologic.mahjongtournamentsuite.data.telemetry

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics. It needs the default Firebase app, which the Google services plugin makes from
 * `google-services.json`. Without that file the app has no default Firebase app and this class is not used.
 * Like the other platforms, errors go to Analytics as the events `app_error` and `app_crash`.
 */
internal class AndroidTelemetry(context: Context) : Telemetry {
    private val analytics = FirebaseAnalytics.getInstance(context)

    init {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        userProperty(TelemetryProperty.PLATFORM, "android")
        userProperty(TelemetryProperty.ENVIRONMENT, if (debuggable) "debug" else "prod")
        userProperty(TelemetryProperty.LANGUAGE, java.util.Locale.getDefault().language)
        installCrashHandler()
    }

    override fun event(name: String, params: Map<String, Any>) {
        analytics.logEvent(name, params.toBundle())
    }

    override fun screen(name: String) {
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }

    override fun error(throwable: Throwable, area: String) {
        event(TelemetryEvent.APP_ERROR, errorParams(throwable, area))
    }

    override fun userProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { event(TelemetryEvent.APP_CRASH, mapOf(TelemetryParam.KIND to (throwable::class.simpleName ?: "Error"))) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun Map<String, Any>.toBundle() = Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString().take(MAX_VALUE_LENGTH))
            }
        }
    }

    private companion object {
        const val MAX_VALUE_LENGTH = 100
    }
}

internal fun createAndroidTelemetry(context: Context): Telemetry {
    val hasDefaultApp = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
    return if (hasDefaultApp) runCatching { AndroidTelemetry(context) }.getOrDefault(NoTelemetry) else NoTelemetry
}
