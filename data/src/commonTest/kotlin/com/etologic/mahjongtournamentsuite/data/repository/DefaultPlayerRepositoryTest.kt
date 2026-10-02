package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class DefaultPlayerRepositoryTest {
    @Test
    fun createPlayerMapsResponseAndMarksPlayersCacheStale() = runTest {
        var request: HttpRequestData? = null
        val cache = RecordingRepositoryCache()
        val repository = repository(
            cache = cache,
            apiHandler = { received ->
                request = received
                jsonResponse(
                    """
                    {
                      "emaId": "123",
                      "firstName": "Ada",
                      "lastName": "Lovelace",
                      "country": "EU",
                      "photoUrl": null
                    }
                    """.trimIndent(),
                )
            },
        )

        val result = repository.createPlayer(
            Player(
                emaId = "123",
                firstName = "Ada",
                lastName = "Lovelace",
                country = "EU",
            ),
        )

        val player = assertIs<AppResult.Success<Player>>(result).value
        assertEquals("123", player.emaId)
        assertEquals("Ada", player.firstName)
        assertEquals("", player.country)
        assertEquals(HttpMethod.Post, request?.method)
        assertEquals("Bearer id-token", request?.headers?.get(HttpHeaders.Authorization))
        assertEquals(listOf("global:emaPlayers"), cache.stalePrefixes)
    }

    @Test
    fun createPlayerConvertsBackendFailureToFailureResult() = runTest {
        val repository = repository(
            apiHandler = {
                respond(
                    content = ByteReadChannel("invalid player"),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                )
            },
        )

        val result = repository.createPlayer(
            Player("123", "Ada", "Lovelace"),
        )

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Unexpected>(failure.error)
        assertEquals("Backend error 400: invalid player", error.message)
    }

    private fun repository(
        cache: RepositoryCache = RecordingRepositoryCache(),
        apiHandler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): DefaultPlayerRepository = DefaultPlayerRepository(
        backendApi = mockBackendApi(apiHandler),
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultPlayerRepositoryTest"),
        cache = cache,
    )
}

private class FakeAuthRepository : AuthRepository {
    override suspend fun currentSession(): AuthSession = AuthSession(
        uid = "user-1",
        idToken = "id-token",
        refreshToken = "refresh-token",
    )

    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> =
        error("Not used in this test")

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        error("Not used in this test")

    override suspend fun savedCredentials(): SavedCredentials? = null

    override suspend fun clearSavedCredentials() = Unit

    override suspend fun refreshSession(): AppResult<AuthSession> =
        error("Not used in this test")

    override suspend fun signOut() = Unit

    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> =
        error("Not used in this test")
}

private class RecordingRepositoryCache : RepositoryCache {
    val stalePrefixes = mutableListOf<String>()

    override suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T = load()

    override suspend fun markStale(vararg keyPrefixes: String) {
        stalePrefixes += keyPrefixes
    }

    override suspend fun clear() = Unit
}
