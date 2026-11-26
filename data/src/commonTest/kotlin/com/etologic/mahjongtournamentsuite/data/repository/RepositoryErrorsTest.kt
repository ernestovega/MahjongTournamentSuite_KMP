package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** How an error of a call becomes the error that a screen shows, and when a call tries again with a new token. */
class RepositoryErrorsTest {
    private class ConnectException(message: String) : Exception(message)

    @Test
    fun aTimeoutAndAMissingConnectionHaveTheirOwnErrors() {
        assertEquals(
            AppError.Unexpected("Request timed out contacting the backend. Check VPN/firewall and try again."),
            HttpRequestTimeoutException("https://backend.test", 1_000).toRepositoryAppError(),
        )
        assertEquals(AppError.Network, IllegalStateException("wrapped", ConnectException("refused")).toRepositoryAppError())
        assertEquals(AppError.Network, IllegalStateException("TypeError: Failed to fetch").toRepositoryAppError())
    }

    @Test
    fun anyOtherErrorSaysItsKindAndMessage() {
        assertEquals(AppError.Unexpected("IllegalStateException: broken"), IllegalStateException(" broken ").toRepositoryAppError())
        assertEquals(AppError.Unexpected("Could not save"), IllegalStateException("  ").toRepositoryAppError("Could not save"))
        assertEquals(AppError.Unexpected("IllegalStateException"), IllegalStateException().toRepositoryAppError())
        assertEquals(AppError.Unexpected("Error"), object : Exception() {}.toRepositoryAppError(), "a class without a name")
    }

    @Test
    fun aCancelledCallStaysCancelled() {
        val cancelled = CancellationException("gone")

        assertSame(cancelled, assertFailsWith<CancellationException> { cancelled.toRepositoryAppError() })
    }

    @Test
    fun aConflictNeedsAJsonBodyWithDetailsAndAKnownCode() {
        fun conflict(body: String) = BackendHttpException(HttpStatusCode.Conflict, body).codedConflictOrNull()

        assertNull(BackendHttpException(HttpStatusCode.BadRequest, "{}").codedConflictOrNull())
        assertNull(IllegalStateException().codedConflictOrNull())
        assertNull(conflict("not json"))
        assertNull(conflict("""{"details":"text"}"""))
        assertNull(conflict("""{"details":{}}"""))
        assertNull(conflict("""{"details":{"code":["ALL_HANDS_DONE"]}}"""))
    }

    @Test
    fun aConflictWithoutItsDetailsHasEmptyOrDefaultArguments() {
        fun conflict(body: String) = BackendHttpException(HttpStatusCode.Conflict, body).codedConflictOrNull()

        assertEquals(AppError.Conflict("", code = ConflictCode.ASSIGNMENTS_MISSING, args = emptyList()), conflict("""{"details":{"code":"ASSIGNMENTS_MISSING"}}"""))
        assertEquals(
            AppError.Conflict("Round 2 is in play", code = ConflictCode.PREVIOUS_ROUND_IN_PLAY, args = listOf("", "", "true")),
            conflict("""{"message":"Round 2 is in play","details":{"code":"PREVIOUS_ROUND_IN_PLAY","tables":"3"}}"""),
        )
        assertEquals(AppError.Conflict("", code = ConflictCode.ALL_HANDS_DONE, args = emptyList()), conflict("""{"message":[1],"details":{"code":"ALL_HANDS_DONE"}}"""))
    }

    @Test
    fun aCallTriesAgainOnlyWhenANewTokenComes() = runTest {
        val noSession = object : AuthRepository by FakeAuthRepository() {
            override suspend fun currentSession(): AuthSession? = null
        }
        val noRefresh = object : AuthRepository by FakeAuthRepository() {
            override suspend fun refreshSession(): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
        }
        val expired = BackendHttpException(HttpStatusCode.Unauthorized, "expired")
        val refused = BackendHttpException(HttpStatusCode.Forbidden, "no")

        assertFailsWith<IllegalStateException> { noSession.withFreshIdToken { it } }
        assertSame(expired, assertFailsWith<BackendHttpException> { noRefresh.withFreshIdToken { throw expired } })
        assertSame(refused, assertFailsWith<BackendHttpException> { FakeAuthRepository().withFreshIdToken { throw refused } })
    }
}
