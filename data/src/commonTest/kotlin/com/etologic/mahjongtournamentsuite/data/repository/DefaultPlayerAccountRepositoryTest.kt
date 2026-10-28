package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class DefaultPlayerAccountRepositoryTest {
    private fun repository(cache: AccountCache = AccountCache(), handler: (HttpRequestData) -> String?) =
        DefaultPlayerAccountRepository(
            backendApi = mockBackendApi { request ->
                handler(request)?.let { jsonResponse(it) } ?: respond("{}", HttpStatusCode.InternalServerError)
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("DefaultPlayerAccountRepositoryTest"),
            cache = cache,
        )

    @Test
    fun sendPostsThePlayerIdsMapsOutcomesAndReasonsAndMarksPlayersStale() = runTest {
        var request: HttpRequestData? = null
        val cache = AccountCache()
        val repository = repository(cache) {
            request = it
            """{"results":[
                {"playerId":1,"outcome":"INVITED"},
                {"playerId":2,"outcome":"RESET_SENT"},
                {"playerId":3,"outcome":"FAILED","reason":"NO_EMAIL"},
                {"playerId":4,"outcome":"FAILED","reason":"FROM_THE_FUTURE"},
                {"playerId":5,"outcome":"FROM_THE_FUTURE"}
            ]}"""
        }

        val results = assertIs<AppResult.Success<List<PlayerAccountActionResult>>>(repository.send("t1", listOf(1, 2, 3, 4, 5))).value

        assertEquals("/functions/tournaments/t1/player-accounts/send", request?.url?.encodedPath)
        assertEquals(HttpMethod.Post, request?.method)
        val body = (request?.body as TextContent).text
        assertEquals(listOf(1, 2, 3, 4, 5), Regex("\"playerId\":(\\d+)").findAll(body).map { it.groupValues[1].toInt() }.toList())
        assertEquals(
            listOf(
                PlayerAccountOutcome.INVITED, PlayerAccountOutcome.RESET_SENT, PlayerAccountOutcome.FAILED,
                PlayerAccountOutcome.FAILED, PlayerAccountOutcome.FAILED,
            ),
            results.map { it.outcome },
        )
        assertEquals(listOf(null, null, PlayerAccountFailure.NO_EMAIL, PlayerAccountFailure.UNEXPECTED, null), results.map { it.reason })
        assertEquals(listOf("tournament:t1:players"), cache.stale)
    }

    @Test
    fun aBackendErrorBecomesAFailureAndTheCacheStaysValid() = runTest {
        val cache = AccountCache()
        val repository = repository(cache) { null }

        assertIs<AppResult.Failure>(repository.send("t1", listOf(1)))
        assertEquals(emptyList(), cache.stale)
    }
}

private class AccountCache : RepositoryCache {
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
