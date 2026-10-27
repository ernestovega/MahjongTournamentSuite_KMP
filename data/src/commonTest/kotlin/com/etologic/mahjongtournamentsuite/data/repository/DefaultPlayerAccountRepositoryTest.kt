package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
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
    fun runActionSendsTheActionInThePathMapsOutcomesAndMarksPlayersStale() = runTest {
        var request: HttpRequestData? = null
        val cache = AccountCache()
        val repository = repository(cache) {
            request = it
            """{"results":[
                {"playerId":1,"outcome":"INVITED"},
                {"playerId":2,"outcome":"FAILED","message":"No email for this player"},
                {"playerId":3,"outcome":"FROM_THE_FUTURE"}
            ]}"""
        }

        val results = assertIs<AppResult.Success<List<PlayerAccountActionResult>>>(
            repository.runAction("t1", PlayerAccountAction.RESEND, listOf(PlayerAccountActionItem(1, "a@b.es"))),
        ).value

        assertEquals("/functions/tournaments/t1/player-accounts/resend", request?.url?.encodedPath)
        assertEquals(HttpMethod.Post, request?.method)
        assertEquals(
            listOf(PlayerAccountOutcome.INVITED, PlayerAccountOutcome.FAILED, PlayerAccountOutcome.FAILED),
            results.map { it.outcome },
        )
        assertEquals("No email for this player", results[1].message)
        assertEquals(listOf("tournament:t1:players"), cache.stale)
    }

    @Test
    fun saveEmailsPutsThePlayersMapsTheOutcomesAndMarksPlayersStale() = runTest {
        var request: HttpRequestData? = null
        val cache = AccountCache()
        val repository = repository(cache) {
            request = it
            """{"results":[
                {"playerId":4,"outcome":"CREATED"},
                {"playerId":5,"outcome":"KEPT_ACCOUNT_EMAIL","message":"old@b.es"},
                {"playerId":6,"outcome":"FAILED","message":"Email already in use"},
                {"playerId":7,"outcome":"FROM_THE_FUTURE"}
            ]}"""
        }

        val results = assertIs<AppResult.Success<List<PlayerEmailResult>>>(
            repository.saveEmails("t1", listOf(PlayerAccountActionItem(4, "d@b.es"))),
        ).value

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals("/functions/tournaments/t1/player-accounts/emails", request?.url?.encodedPath)
        val body = (request?.body as TextContent).text
        assertEquals(true, body.contains("\"playerId\":4") && body.contains("d@b.es"))
        assertEquals(
            listOf(PlayerEmailOutcome.CREATED, PlayerEmailOutcome.KEPT_ACCOUNT_EMAIL, PlayerEmailOutcome.FAILED, PlayerEmailOutcome.FAILED),
            results.map { it.outcome },
        )
        assertEquals("old@b.es", results[1].message)
        assertEquals(listOf("tournament:t1:players"), cache.stale)
    }

    @Test
    fun aBackendErrorBecomesAFailureAndTheCacheStaysValid() = runTest {
        val cache = AccountCache()
        val repository = repository(cache) { null }

        assertIs<AppResult.Failure>(repository.runAction("t1", PlayerAccountAction.SEND, emptyList()))
        assertIs<AppResult.Failure>(repository.saveEmails("t1", emptyList()))
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
