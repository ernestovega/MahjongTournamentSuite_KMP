package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.SavedCredentials
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class MemorySessionStore(var stored: StoredAuthSession?) : AuthSessionStore {
    override suspend fun load(): StoredAuthSession? = stored
    override suspend fun save(session: StoredAuthSession?) {
        stored = session
    }
}

private class NoCredentials : CredentialStore {
    override suspend fun load(): SavedCredentials? = null
    override suspend fun save(credentials: SavedCredentials) = Unit
    override suspend fun clear() = Unit
}

/** A screen sends its requests at the same time. When the token expired, they must share one refresh. */
class DefaultAuthRepositoryRefreshTest {
    private var refreshCalls = 0
    private var failRefresh = false
    private val sessionStore = MemorySessionStore(StoredAuthSession("u1", "old-id", "refresh-1"))

    private fun repository() = DefaultAuthRepository(
        backendApi = mockBackendApi { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh")) {
                refreshCalls++
                if (failRefresh) {
                    respond(ByteReadChannel("down"), HttpStatusCode.InternalServerError)
                } else {
                    jsonResponse("""{"idToken":"new-id-$refreshCalls","refreshToken":"refresh-${refreshCalls + 1}"}""")
                }
            } else {
                respond(ByteReadChannel("unexpected"), HttpStatusCode.NotFound)
            }
        },
        sessionStore = sessionStore,
        logger = Logger.withTag("auth-test"),
        credentialStore = NoCredentials(),
        cache = SessionRepositoryCache(logger = Logger.withTag("cache")),
    )

    @Test
    fun refreshesAtTheSameTimeShareOneRequest() = runTest {
        val auth = repository()

        val results = List(5) { async { auth.refreshSession() } }.awaitAll()

        assertEquals(1, refreshCalls)
        assertEquals(setOf("new-id-1"), results.map { assertIs<AppResult.Success<AuthSession>>(it).value.idToken }.toSet())
        assertEquals("new-id-1", auth.currentSession()?.idToken)
        assertEquals("new-id-1", sessionStore.stored?.idToken)
    }

    @Test
    fun aFailedRefreshIsNotReused() = runTest {
        val auth = repository()
        failRefresh = true
        assertIs<AppResult.Failure>(auth.refreshSession())

        failRefresh = false
        assertIs<AppResult.Success<*>>(auth.refreshSession())

        assertEquals(2, refreshCalls)
        assertEquals("new-id-2", auth.currentSession()?.idToken)
    }

    @Test
    fun aSignedOutSessionIsNotRefreshedFromTheSharedResult() = runTest {
        val auth = repository()
        auth.refreshSession()

        auth.signOut()

        assertIs<AppResult.Failure>(auth.refreshSession())
    }
}
