package com.etologic.mahjongtournamentsuite.data.telemetry

import co.touchlab.kermit.Severity
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryLogWriterTest {
    private val telemetry = RecordingTelemetry()
    private val writer = TelemetryLogWriter(telemetry)

    private fun warn(error: Throwable?, severity: Severity = Severity.Warn) =
        writer.log(severity, "Sign in failed.", "tag", error)

    private fun reasons() = telemetry.events.map { it.second[TelemetryParam.REASON] }

    @Test
    fun serverErrorGoesToAnalyticsAndCrashlytics() {
        warn(BackendHttpException(HttpStatusCode.InternalServerError, "secret body"))
        assertEquals(listOf("server_error"), reasons())
        assertEquals("Sign in failed.", telemetry.errors.single().second)
    }

    @Test
    fun expectedFailuresGoOnlyToAnalytics() {
        warn(BackendHttpException(HttpStatusCode.Unauthorized, ""))
        warn(BackendHttpException(HttpStatusCode.Conflict, ""))
        warn(BackendHttpException(HttpStatusCode.BadRequest, ""))
        warn(RuntimeException("Failed to fetch"))
        assertEquals(listOf("auth", "conflict", "client_error", "network"), reasons())
        assertTrue(telemetry.errors.isEmpty())
    }

    @Test
    fun unknownErrorIsABug() {
        warn(IllegalStateException("boom"))
        assertEquals(listOf("unexpected"), reasons())
        assertEquals(1, telemetry.errors.size)
    }

    @Test
    fun cancellationInfoAndMissingErrorAreIgnored() {
        warn(CancellationException("job cancelled"))
        warn(null)
        warn(IllegalStateException("boom"), Severity.Info)
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun noEventCarriesTheResponseBody() {
        warn(BackendHttpException(HttpStatusCode.InternalServerError, "user@example.com"))
        assertTrue(telemetry.events.none { it.first == TelemetryEvent.ACTION_FAILED && "user@example.com" in it.second.values.toString() })
    }
}
