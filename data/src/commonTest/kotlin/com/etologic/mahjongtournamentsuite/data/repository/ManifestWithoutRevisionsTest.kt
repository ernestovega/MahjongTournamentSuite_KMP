package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A server whose manifest has no revision of a list (an older server, or a list it does not count yet) still gives
 * the list: its revision counts as 0.
 */
class ManifestWithoutRevisionsTest {
    private val requests = mutableListOf<String>()

    private val backend = mockBackendApi { request ->
        val path = request.url.encodedPath.removePrefix("/functions")
        requests += path
        when (path) {
            "/sync/manifest" -> jsonResponse("""{"resources":{}}""")
            "/admin/users" -> jsonResponse("""{"users":[]}""")
            "/ema-player-registry" -> jsonResponse("""{"players":[{"emaId":"1","firstName":"Ada","lastName":"L"}]}""")
            else -> respond(ByteReadChannel("unexpected $path"), HttpStatusCode.NotFound)
        }
    }

    private fun cache() = SessionRepositoryCache(logger = Logger.withTag("cache"))

    @Test
    fun theUsersComeWithoutTheirRevision() = runTest {
        val admin = DefaultAdminRepository(backend, FakeAuthRepository(), Logger.withTag("admin"), cache())

        assertEquals(emptyList<Any>(), assertIs<AppResult.Success<*>>(admin.listUsers(RefreshMode.IF_CHANGED)).value)
        assertTrue("/sync/manifest" in requests)
    }

    @Test
    fun theEmaPlayersComeWithoutTheirRevision() = runTest {
        val players = DefaultPlayerRepository(backend, FakeAuthRepository(), Logger.withTag("players"), cache())

        assertEquals("Ada", assertIs<AppResult.Success<List<Player>>>(players.listPlayers(RefreshMode.IF_CHANGED)).value.single().firstName)
        assertTrue("/sync/manifest" in requests)
    }

    @Test
    fun theEmaPlayersOfAnAccountNeedASession() = runTest {
        val signedOut = object : AuthRepository by FakeAuthRepository() {
            override suspend fun currentSession(): AuthSession? = null
        }
        val players = DefaultPlayerRepository(backend, signedOut, Logger.withTag("players"), cache())

        assertIs<AppResult.Failure>(players.listPlayers(RefreshMode.IF_CHANGED))
        assertEquals(emptyList<String>(), requests)
    }
}
