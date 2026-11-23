package com.etologic.mahjongtournamentsuite.data.backend

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FunctionsBackendApiTest {
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
    fun listTournamentsAsksForAllTournamentsByDefault() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            jsonResponse("{\"tournaments\":[]}")
        }

        api.listTournaments(idToken = "token-123")

        assertEquals("https://backend.test/functions/tournaments", request?.url.toString())
    }

    @Test
    fun listTournamentsWithPlayerScopeSendsTheScopeParameter() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            jsonResponse("{\"tournaments\":[]}")
        }

        api.listTournaments(idToken = "token-123", playerScope = true)

        assertEquals("player", request?.url?.parameters?.get("scope"))
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

        val error = assertFailsWith<BackendHttpException> { api.featureFlags() }

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

    @Test
    fun idCardsSendTheBacksTheExtraSheetsAndTheStaffNames() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            respond(content = byteArrayOf(1), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/pdf"))
        }

        api.generateTournamentIdCards(
            "token-123",
            "tournament-1",
            IdCardSheetLayout(backs = false, backSameAsFront = true, substituteSheets = 2, staffSheets = 1, staffNames = listOf(" Ana ", "", "Luis")),
        )

        val parameters = request!!.url.parameters
        assertEquals("false", parameters["backs"])
        assertEquals("true", parameters["backSameAsFront"])
        assertEquals("2", parameters["substituteSheets"])
        assertEquals("1", parameters["staffSheets"])
        assertEquals(listOf("Ana", "Luis"), parameters.getAll("staffName"))
    }

    @Test
    fun staffNamesAreSavedWithAPutOnTheTournament() = runTest {
        var request: HttpRequestData? = null
        val api = mockBackendApi { received ->
            request = received
            respond(
                content = ByteReadChannel("{\"names\":[\"Ana\"]}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertEquals(listOf("Ana"), api.saveIdCardStaffNames("token-123", "tournament-1", listOf("Ana")).names)
        assertEquals(HttpMethod.Put, request?.method)
        assertTrue(request?.url?.encodedPath.orEmpty().endsWith("/tournaments/tournament-1/id-cards/staff-names"))
    }
}
