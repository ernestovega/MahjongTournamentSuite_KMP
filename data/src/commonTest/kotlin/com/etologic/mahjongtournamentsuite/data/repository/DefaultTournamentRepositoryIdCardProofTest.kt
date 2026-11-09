package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.telemetry.RecordingTelemetry
import com.etologic.mahjongtournamentsuite.data.telemetry.TelemetryLogWriter
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardProofRequest
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The proof of the ID cards is a request like any other: a failure must reach the log, so Analytics counts it. */
class DefaultTournamentRepositoryIdCardProofTest {
    private class NoCache : RepositoryCache {
        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()
        override suspend fun markStale(vararg keyPrefixes: String) = Unit
        override suspend fun clear() = Unit
    }

    private val telemetry = RecordingTelemetry()

    private fun repository(status: HttpStatusCode) = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            assertEquals("/functions/tournaments/id-card-proof", request.url.encodedPath)
            respond(ByteReadChannel("pdf"), status, headersOf(HttpHeaders.ContentType, "application/pdf"))
        },
        authRepository = FakeAuthRepository(),
        logger = Logger(StaticConfig(logWriterList = listOf(TelemetryLogWriter(telemetry))), "proof"),
        cache = NoCache(),
        json = Json,
    )

    private val request = IdCardProofRequest(shortName = "OPEN", year = "2026", primaryColor = "#112233")

    @Test
    fun aProofReturnsTheFileAndSendsNoFailure() = runTest {
        val bytes = assertIs<AppResult.Success<ByteArray>>(repository(HttpStatusCode.OK).generateIdCardProof(request)).value

        assertContentEquals("pdf".encodeToByteArray(), bytes)
        assertEquals(emptyList(), telemetry.events)
    }

    @Test
    fun aFailedProofIsAFailureAndCountsAsActionFailed() = runTest {
        val result = repository(HttpStatusCode.InternalServerError).generateIdCardProof(request)

        assertIs<AppResult.Failure>(result)
        val event = telemetry.events.single()
        assertEquals(TelemetryEvent.ACTION_FAILED, event.first)
        assertEquals("server_error", event.second[TelemetryParam.REASON])
        assertTrue(event.second[TelemetryParam.ACTION].toString().contains("ID card proof"), event.second.toString())
        // A server error is a bug, so it also goes to the error log.
        assertEquals(1, telemetry.errors.size)
    }
}
