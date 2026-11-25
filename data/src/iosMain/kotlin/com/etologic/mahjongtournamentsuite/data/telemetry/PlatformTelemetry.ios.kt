package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.data.platform.IosAnalytics
import com.etologic.mahjongtournamentsuite.data.platform.IosPlatform
import com.etologic.mahjongtournamentsuite.domain.AppVersion
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryProperty
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import kotlin.experimental.ExperimentalNativeApi

private const val CONSENT_KEY = "mts.telemetry.consent"
private const val PENDING_CRASH_KEY = "mts.telemetry.pendingCrash"

/**
 * Firebase Analytics through the Swift side ([IosPlatform.analytics]). The Info.plist turns the collection off. It
 * starts only after the user accepted, and it stops when the user withdraws. Like AndroidTelemetry.
 */
internal class IosTelemetry(
    private val analytics: IosAnalytics,
    platform: String,
    env: String,
    language: String,
) : Telemetry {
    private val defaults = NSUserDefaults.standardUserDefaults
    private var consent = defaults.boolForKey(CONSENT_KEY)

    init {
        analytics.setCollectionEnabled(consent)
        userProperty(TelemetryProperty.PLATFORM, platform)
        userProperty(TelemetryProperty.ENVIRONMENT, env)
        userProperty(TelemetryProperty.LANGUAGE, language)
    }

    override val requiresConsent: Boolean = true

    override fun hasConsent(): Boolean = consent

    override fun setConsent(granted: Boolean) {
        consent = granted
        defaults.setBool(granted, CONSENT_KEY)
        analytics.setCollectionEnabled(granted)
    }

    override fun event(name: String, params: Map<String, Any>) {
        if (consent) analytics.logEvent(name, params.mapValues { (_, value) -> value.forAnalytics() })
    }

    override fun screen(name: String) {
        if (consent) analytics.logEvent("screen_view", mapOf("screen_name" to name, "screen_class" to name))
    }

    /** The error log of the API takes handled errors. The decorator sends them there and counts them as events. */
    override fun error(throwable: Throwable, area: String) = Unit

    override fun userProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    private fun Any.forAnalytics(): Any = when (this) {
        is Int -> toLong()
        is Long -> this
        else -> toString().take(MAX_VALUE_LENGTH)
    }

    private companion object {
        const val MAX_VALUE_LENGTH = 100
    }
}

internal object IosPendingCrashStore : PendingCrashStore {
    override fun save(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, PENDING_CRASH_KEY)
        // The process ends right after the crash handler.
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    override fun take(): String? = NSUserDefaults.standardUserDefaults.stringForKey(PENDING_CRASH_KEY)?.also {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(PENDING_CRASH_KEY)
    }
}

actual fun providePlatformTelemetry(): PlatformTelemetry {
    val env = if (IosPlatform.isDebug) "debug" else "prod"
    val language = NSLocale.currentLocale.languageCode
    val analytics = IosPlatform.analytics?.let { runCatching { IosTelemetry(it, "ios", env, language) }.getOrNull() } ?: NoTelemetry
    // Like Android: one app with both parts and one version, the admin version. See docs/versioning.md.
    val app = IosPlatform.currentApp ?: "admin"
    return PlatformTelemetry(analytics, app, "ios", env, language, IosPendingCrashStore, AppVersion.admin)
}

/** The callback of the last part that started. The hook goes in one time, so a crash is reported one time. */
private var crashCallback: ((Throwable) -> Unit)? = null

@OptIn(ExperimentalNativeApi::class)
actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    val first = crashCallback == null
    crashCallback = onCrash
    if (!first) return
    // The crash is saved and sent at the next start. Then the app ends, as it would without the hook.
    setUnhandledExceptionHook { throwable ->
        runCatching { crashCallback?.invoke(throwable) }
        terminateWithUnhandledException(throwable)
    }
}
