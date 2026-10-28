package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
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

class DefaultAdminRepositoryPasswordResetsTest {
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

    private fun repository(handler: (HttpRequestData) -> String?) = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            handler(request)?.let { jsonResponse(it) } ?: respond("{}", HttpStatusCode.Forbidden)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultAdminRepositoryPasswordResetsTest"),
        cache = NoCache(),
    )

    @Test
    fun postsTheUidsAndMapsOutcomesAndReasons() = runTest {
        var request: HttpRequestData? = null
        val repository = repository {
            request = it
            """{"results":[
                {"uid":"a","outcome":"SENT"},
                {"uid":"b","outcome":"FAILED","reason":"DISABLED"},
                {"uid":"c","outcome":"FAILED","reason":"FROM_THE_FUTURE"},
                {"uid":"d","outcome":"FROM_THE_FUTURE"}
            ]}"""
        }

        val results = assertIs<AppResult.Success<List<PasswordResetResult>>>(repository.sendPasswordResets(listOf("a", "b", "c", "d"))).value

        assertEquals(HttpMethod.Post, request?.method)
        assertEquals("/functions/admin/users/password-resets", request?.url?.encodedPath)
        assertEquals(true, (request?.body as TextContent).text.contains("\"uids\":[\"a\",\"b\",\"c\",\"d\"]"))
        assertEquals(
            listOf(PasswordResetOutcome.SENT, PasswordResetOutcome.FAILED, PasswordResetOutcome.FAILED, PasswordResetOutcome.FAILED),
            results.map { it.outcome },
        )
        assertEquals(listOf(null, PasswordResetFailure.DISABLED, PasswordResetFailure.UNKNOWN, null), results.map { it.reason })
    }

    @Test
    fun aRefusalOfTheServerBecomesAFailure() = runTest {
        assertIs<AppResult.Failure>(repository { null }.sendPasswordResets(listOf("a")))
    }
}
