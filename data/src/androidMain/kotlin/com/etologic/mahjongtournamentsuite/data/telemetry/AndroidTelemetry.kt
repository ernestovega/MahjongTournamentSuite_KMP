package com.etologic.mahjongtournamentsuite.data.telemetry

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Analytics and Crashlytics of Firebase. Both need the default Firebase app, which the Google services plugin makes
 * from `google-services.json`. Without that file the app has no default Firebase app and this class is not used.
 * Crashlytics finds the crashes of the app by itself. [error] adds the errors that the app handled.
 */
internal class AndroidTelemetry(context: Context) : Telemetry {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    init {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        userProperty(TelemetryProperty.PLATFORM, "android")
        userProperty(TelemetryProperty.ENVIRONMENT, if (debuggable) "debug" else "prod")
        userProperty(TelemetryProperty.LANGUAGE, java.util.Locale.getDefault().language)
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
        crashlytics.setCustomKey("area", area)
        crashlytics.recordException(throwable)
    }

    override fun userProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
        if (value != null) crashlytics.setCustomKey(name, value)
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
