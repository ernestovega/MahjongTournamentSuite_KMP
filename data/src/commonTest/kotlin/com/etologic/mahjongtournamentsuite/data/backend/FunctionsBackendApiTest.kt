package com.etologic.mahjongtournamentsuite.data.backend

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class FunctionsBackendApiTest {
    @Test
    fun versionUsesTheConfiguredBaseUrlAndDecodesJson() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            jsonResponse("{\"version\":\"2026.10\"}")
        }

        assertEquals("2026.10", api.version().version)
        assertEquals(HttpMethod.Get, request?.method)
        assertEquals("https://backend.test/functions/version", request?.url.toString())
    }

    @Test
    fun authenticatedLookupUsesTournamentPathAndEmailParameter() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            jsonResponse("{\"uid\":\"uid-1\",\"email\":\"player@example.test\",\"alias\":\"Player\"}")
        }

        val profile = api.lookupUser(
            idToken = "token-123",
            identifier = "player@example.test",
            tournamentId = "tournament-1",
        )

        assertEquals("uid-1", profile.uid)
        assertEquals(HttpMethod.Get, request?.method)
        assertEquals(
            "https://backend.test/functions/tournaments/tournament-1/users/lookup",
            request?.url?.toString()?.substringBefore("?"),
        )
        assertEquals("player@example.test", request?.url?.parameters?.get("email"))
        assertEquals("Bearer token-123", request?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun backendErrorsKeepStatusAndResponseBody() = runTest {
        val api = mockBackendApi {
            respond(
                content = ByteReadChannel("backend failure"),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }

        val error = assertFailsWith<BackendHttpException> { api.version() }

        assertEquals(HttpStatusCode.BadRequest, error.status)
        assertEquals("backend failure", error.responseBody)
    }

    @Test
    fun binaryResponsesAreReturnedWithoutJsonDecoding() = runTest {
        val expected = byteArrayOf(1, 2, 3, 4)
        val api = mockBackendApi {
            respond(
                content = expected,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/pdf"),
            )
        }

        assertEquals(expected.toList(), api.generateTournamentIdCards("tournament-1", "token-123").toList())
    }
}
