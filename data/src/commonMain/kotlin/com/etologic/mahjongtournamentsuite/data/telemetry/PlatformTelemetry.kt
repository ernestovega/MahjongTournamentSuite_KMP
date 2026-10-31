package com.etologic.mahjongtournamentsuite.data.telemetry

import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry

/**
 * What a platform gives to the telemetry: the Analytics part and the facts that go into every error report.
 * [analytics] sends nothing until the Firebase settings of the platform are filled in. See docs/telemetry.md.
 */
class PlatformTelemetry(
    val analytics: Telemetry,
    /** `admin` or `player`. */
    val app: String,
    /** `android`, `web` or `desktop`. */
    val platform: String,
    /** `prod`, `dev` or `debug`. */
    val env: String,
    val language: String,
    /** Holds a crash until the next start, because the app cannot send it while it dies. Null if not needed. */
    val pendingCrashes: PendingCrashStore?,
)

interface PendingCrashStore {
    fun save(json: String)

    /** Returns the saved crash and removes it. */
    fun take(): String?
}

expect fun providePlatformTelemetry(): PlatformTelemetry

/** Calls [onCrash] when a thread ends with an error that nobody handled. The web does this in `telemetry.js`. */
expect fun installCrashHandler(onCrash: (Throwable) -> Unit)
