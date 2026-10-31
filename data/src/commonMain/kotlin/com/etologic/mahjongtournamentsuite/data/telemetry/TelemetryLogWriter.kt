package com.etologic.mahjongtournamentsuite.data.telemetry

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.repository.isConnectionFailure
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * Every repository logs a failed request as a warning with the error. This writer counts each of them as
 * `action_failed` and reports the ones that point to a bug as `app_error`. So all requests are traced
 * and no repository needs its own code for it.
 */
class TelemetryLogWriter(private val telemetry: Telemetry) : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (throwable == null || severity < Severity.Warn) return
        val failure = throwable.classify() ?: return
        val action = message.take(MAX_ACTION_LENGTH)
        val params = buildMap<String, Any> {
            put(TelemetryParam.ACTION, action)
            put(TelemetryParam.REASON, failure.reason)
        }
        telemetry.event(TelemetryEvent.ACTION_FAILED, params)
        if (failure.isBug) telemetry.error(throwable, action)
    }
}

internal data class FailureKind(val reason: String, val isBug: Boolean)

/**
 * Sorts an error. Offline users, timeouts, wrong passwords and edit conflicts are normal, so only the count goes
 * to Analytics. A server error or an unknown error is a bug and goes to Analytics as `app_error` too.
 * Returns null for a cancelled job, which is not a failure.
 */
internal fun Throwable.classify(): FailureKind? = when {
    this is CancellationException -> null
    this is HttpRequestTimeoutException -> FailureKind("timeout", isBug = false)
    this is BackendHttpException -> when (status.value) {
        401, 403 -> FailureKind("auth", isBug = false)
        409 -> FailureKind("conflict", isBug = false)
        in 400..499 -> FailureKind("client_error", isBug = false)
        else -> FailureKind("server_error", isBug = true)
    }
    isConnectionFailure() -> FailureKind("network", isBug = false)
    else -> FailureKind("unexpected", isBug = true)
}

private const val MAX_ACTION_LENGTH = 80
