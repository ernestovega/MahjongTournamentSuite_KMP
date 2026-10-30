package com.etologic.mahjongtournamentsuite.data.telemetry

import android.content.Context
import android.os.Bundle
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

private const val PREFERENCES = "mts_telemetry"
private const val CONSENT_KEY = "consent"
private const val PENDING_CRASH_KEY = "pendingCrash"

/**
 * Firebase Analytics. It needs the default Firebase app, which the Google services plugin makes from
 * `google-services.json`. Without that file the app has no default Firebase app and this class is not used.
 * The manifest turns the collection off. It starts only after the user accepted, and it stops when the user withdraws.
 */
internal class AndroidTelemetry(
    context: Context,
    platform: String,
    env: String,
    language: String,
) : Telemetry {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var consent = preferences.getBoolean(CONSENT_KEY, false)

    init {
        analytics.setAnalyticsCollectionEnabled(consent)
        userProperty(TelemetryProperty.PLATFORM, platform)
        userProperty(TelemetryProperty.ENVIRONMENT, env)
        userProperty(TelemetryProperty.LANGUAGE, language)
    }

    override val requiresConsent: Boolean = true

    override fun hasConsent(): Boolean = consent

    override fun setConsent(granted: Boolean) {
        consent = granted
        preferences.edit().putBoolean(CONSENT_KEY, granted).apply()
        analytics.setAnalyticsCollectionEnabled(granted)
    }

    override fun event(name: String, params: Map<String, Any>) {
        if (consent) analytics.logEvent(name, params.toBundle())
    }

    override fun screen(name: String) {
        if (!consent) return
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }

    /** The error log of the API takes handled errors. The decorator sends them there and counts them as events. */
    override fun error(throwable: Throwable, area: String) = Unit

    override fun userProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
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

internal class AndroidPendingCrashStore(context: Context) : PendingCrashStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    // commit() writes at once. The process ends right after the crash handler.
    override fun save(json: String) {
        preferences.edit().putString(PENDING_CRASH_KEY, json).commit()
    }

    override fun take(): String? = preferences.getString(PENDING_CRASH_KEY, null)?.also {
        preferences.edit().remove(PENDING_CRASH_KEY).apply()
    }
}

internal fun createAndroidTelemetry(context: Context, debuggable: Boolean): PlatformTelemetry {
    val hasDefaultApp = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
    val env = if (debuggable) "debug" else "prod"
    val language = java.util.Locale.getDefault().language
    val analytics = if (hasDefaultApp) {
        runCatching { AndroidTelemetry(context, "android", env, language) }.getOrDefault(NoTelemetry)
    } else {
        NoTelemetry
    }
    // The package name tells the two apps apart.
    val app = if (context.packageName.endsWith(".player")) "player" else "admin"
    return PlatformTelemetry(analytics, app, "android", env, language, AndroidPendingCrashStore(context))
}
