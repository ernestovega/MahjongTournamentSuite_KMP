package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The hands and penalties that a player enters at the table. */
class DefaultPlayerHandRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val cache = StaleRecorder()

    private fun repository(body: String = PREVIEW, status: HttpStatusCode = HttpStatusCode.OK) = DefaultPlayerHandRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            if (status == HttpStatusCode.OK) jsonResponse(body) else respond(body, status)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger(StaticConfig(logWriterList = emptyList()), "DefaultPlayerHandRepositoryTest"),
        cache = cache,
    )

    private fun HttpRequestData.json(): JsonObject = Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun aPreviewSendsTheHandWithoutAVersionAndKeepsTheCache() = runTest {
        val result = repository().previewHand("t-1", 2, 5, HandProposal(winnerSlotId = 7, loserSlotId = 9, score = 24))

        val preview = assertIs<AppResult.Success<HandPreview>>(result).value
        assertEquals(listOf(7, 9, 11, 13), preview.seatSlotIds)
        assertEquals(42L, preview.tableVersion)
        val request = requests.single()
        assertTrue(request.url.encodedPath.endsWith("/player/tournaments/t-1/tables/2/5/hands/preview"))
        assertEquals("Bearer id-token", request.headers[HttpHeaders.Authorization])
        val json = request.json()
        assertEquals("7", json["playerWinnerId"]?.jsonPrimitive?.content)
        assertEquals("9", json["playerLooserId"]?.jsonPrimitive?.content)
        assertEquals("24", json["handScore"]?.jsonPrimitive?.content)
        // A false value is the default, so it may be left out.
        assertEquals("false", json["isDraw"]?.jsonPrimitive?.content ?: "false")
        assertTrue(json["expectedVersion"] == null || json["expectedVersion"]?.jsonPrimitive?.content == "null")
        assertTrue(cache.stale.isEmpty(), "A preview changes nothing")
    }

    @Test
    fun aRecordedHandSendsTheVersionAndMarksTheTablesStale() = runTest {
        val result = repository().recordHand("t-1", 2, 5, HandProposal(winnerSlotId = 7, loserSlotId = null, score = 30, isLastHand = true), expectedVersion = 41)

        assertIs<AppResult.Success<HandPreview>>(result)
        val json = requests.single().json()
        assertEquals("41", json["expectedVersion"]?.jsonPrimitive?.content)
        assertEquals("", json["playerLooserId"]?.jsonPrimitive?.content)
        assertEquals("true", json["isLastHand"]?.jsonPrimitive?.content)
        assertTrue(requests.single().url.encodedPath.endsWith("/player/tournaments/t-1/tables/2/5/hands"))
        assertEquals(listOf("tournament:t-1:tables"), cache.stale)
    }

    @Test
    fun aHandWithoutAWinnerIsADraw() = runTest {
        repository().previewHand("t-1", 1, 1, HandProposal(winnerSlotId = null, loserSlotId = null, score = 0))

        val json = requests.single().json()
        assertEquals("true", json["isDraw"]?.jsonPrimitive?.content)
        assertEquals("", json["playerWinnerId"]?.jsonPrimitive?.content)
    }

    @Test
    fun penaltiesAreJoinedBySeat() = runTest {
        val result = repository(body = "{\"ok\":true}").addPenalties(
            "t-1",
            2,
            5,
            penalties = listOf(PenaltyEntry(seatIndex = 0, amount = 10, shared = false), PenaltyEntry(seatIndex = 1, amount = 30, shared = true)),
            expectedVersion = 7,
        )

        assertIs<AppResult.Success<Unit>>(result)
        val json = requests.single().json()
        assertTrue(requests.single().url.encodedPath.endsWith("/player/tournaments/t-1/tables/2/5/penalties"))
        // East loses 10 and gets 10 of the shared penalty of South. South loses 30. West and North get 10.
        assertEquals("-10;10", json["playerEastPenalty"]?.jsonPrimitive?.content)
        assertEquals(";-30", json["playerSouthPenalty"]?.jsonPrimitive?.content)
        assertEquals(";10", json["playerWestPenalty"]?.jsonPrimitive?.content)
        assertEquals(";10", json["playerNorthPenalty"]?.jsonPrimitive?.content)
        assertEquals("7", json["expectedVersion"]?.jsonPrimitive?.content)
        assertEquals(listOf("tournament:t-1:tables"), cache.stale)
    }

    @Test
    fun aSeatWithoutPenaltiesSendsAnEmptyText() = runTest {
        repository(body = "{\"ok\":true}").addPenalties("t-1", 2, 5, listOf(PenaltyEntry(0, 5, false)), expectedVersion = 1)

        assertEquals("", requests.single().json()["playerNorthPenalty"]?.jsonPrimitive?.content)
    }

    @Test
    fun aConflictTellsThatTheTableChanged() = runTest {
        val result = repository(body = "{\"message\":\"Another player saved a hand.\"}", status = HttpStatusCode.Conflict)
            .recordHand("t-1", 2, 5, HandProposal(7, 9, 24), expectedVersion = 3)

        val error = assertIs<AppError.Conflict>(assertIs<AppResult.Failure>(result).error)
        assertEquals("Another player saved a hand.", error.message)
        assertTrue(cache.stale.isEmpty())
    }

    @Test
    fun aConflictWithoutAMessageHasAFixedText() = runTest {
        val result = repository(body = "{}", status = HttpStatusCode.Conflict).recordHand("t-1", 2, 5, HandProposal(7, 9, 24), expectedVersion = 3)

        assertEquals("The table changed on the server.", assertIs<AppError.Conflict>(assertIs<AppResult.Failure>(result).error).message)
    }

    @Test
    fun anotherErrorIsNotAConflict() = runTest {
        val result = repository(body = "{}", status = HttpStatusCode.Forbidden).previewHand("t-1", 2, 5, HandProposal(7, 9, 24))

        val failure = assertIs<AppResult.Failure>(result)
        assertTrue(failure.error !is AppError.Conflict)
    }

    private class StaleRecorder : RepositoryCache {
        val stale = mutableListOf<String>()

        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()

        override suspend fun markStale(vararg keyPrefixes: String) {
            stale += keyPrefixes
        }

        override suspend fun clear() = Unit
    }

    private companion object {
        const val PREVIEW = """{"handId":3,"seats":["7","9","11","13"],"handScores":[32,-8,-8,-16],"tableScores":["32","-8","-8","-16"],"tablePoints":["4","2","2","0"],"tableVersion":42}"""
    }
}
