package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.InvitationStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerInvitation
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

class DefaultPlayerInvitationRepositoryTest {
    private fun repository(cache: InvitationCache = InvitationCache(), handler: (HttpRequestData) -> String?) =
        DefaultPlayerInvitationRepository(
            backendApi = mockBackendApi { request ->
                handler(request)?.let { jsonResponse(it) } ?: respond("{}", HttpStatusCode.InternalServerError)
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("DefaultPlayerInvitationRepositoryTest"),
            cache = cache,
        )

    @Test
    fun listInvitationsMapsTheStatusAndFallsBackToInvited() = runTest {
        val repository = repository {
            """{"invitations":[
                {"invitationId":"a","playerId":1,"email":"a@b.es","status":"ACCEPTED","expiresAt":"2026-12-01"},
                {"invitationId":"b","playerId":2,"email":"c@b.es","status":"SOMETHING_NEW"}
            ]}"""
        }

        val list = assertIs<AppResult.Success<List<PlayerInvitation>>>(repository.listInvitations("t1")).value

        assertEquals(listOf(InvitationStatus.ACCEPTED, InvitationStatus.INVITED), list.map { it.status })
        assertEquals("2026-12-01", list[0].expiresAt)
    }

    @Test
    fun runActionSendsTheActionInThePathMapsOutcomesAndMarksPlayersStale() = runTest {
        var request: HttpRequestData? = null
        val cache = InvitationCache()
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
    fun saveEmailsPutsThePlayersAndMarksPlayersStale() = runTest {
        var request: HttpRequestData? = null
        val cache = InvitationCache()
        val repository = repository(cache) {
            request = it
            """{"ok":true}"""
        }

        val result = repository.saveEmails("t1", listOf(PlayerAccountActionItem(4, "d@b.es")))

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals(HttpMethod.Put, request?.method)
        val body = (request?.body as TextContent).text
        assertEquals(true, body.contains("\"playerId\":4") && body.contains("d@b.es"))
        assertEquals(listOf("tournament:t1:players"), cache.stale)
    }

    @Test
    fun aBackendErrorBecomesAFailureAndTheCacheStaysValid() = runTest {
        val cache = InvitationCache()
        val repository = repository(cache) { null }

        assertIs<AppResult.Failure>(repository.runAction("t1", PlayerAccountAction.SEND, emptyList()))
        assertIs<AppResult.Failure>(repository.saveEmails("t1", emptyList()))
        assertEquals(emptyList(), cache.stale)
    }
}

private class InvitationCache : RepositoryCache {
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
