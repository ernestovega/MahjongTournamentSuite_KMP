package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAgendaItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundScheduleDto
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class RepositorySupportTest {
    @Test
    fun aConflictWithAKnownCodeCarriesTheCodeAndItsCount() {
        val error = BackendHttpException(
            HttpStatusCode.Conflict,
            """{"error":"conflict","message":"Assign every tournament player","details":{"code":"ASSIGNMENTS_MISSING","missing":3}}""",
        ).toRepositoryAppError()

        assertEquals(AppError.Conflict("Assign every tournament player", code = ConflictCode.ASSIGNMENTS_MISSING, args = listOf("3")), error)
    }

    @Test
    fun aPlayRefusedByTheRoundBeforeCarriesItsRoundAndOpenTables() {
        val error = BackendHttpException(
            HttpStatusCode.Conflict,
            """{"message":"Round 1 has open tables","details":{"code":"PREVIOUS_ROUND_OPEN_TABLES","roundId":1,"tables":[3,5],"started":false}}""",
        ).toRepositoryAppError()

        assertEquals(
            AppError.Conflict("Round 1 has open tables", code = ConflictCode.PREVIOUS_ROUND_OPEN_TABLES, args = listOf("1", "3, 5", "false")),
            error,
        )
    }

    @Test
    fun aConflictWithoutAKnownCodeStaysAnUnexpectedError() {
        val unknown = BackendHttpException(HttpStatusCode.Conflict, """{"message":"x","details":{"code":"SOMETHING_NEW"}}""")
        val plain = BackendHttpException(HttpStatusCode.Conflict, """{"message":"x"}""")

        assertIs<AppError.Unexpected>(unknown.toRepositoryAppError())
        assertIs<AppError.Unexpected>(plain.toRepositoryAppError())
    }

    @Test
    fun tournamentMappingTrimsValues() {
        val tournament = TournamentDto(
            id = "tournament-1",
            name = "Spring Open",
            isTeams = false,
            numPlayers = 16,
            numRounds = 4,
            associationLogoUrl = "  https://example.test/logo.png  ",
            eventStartDate = " 2026-04-01 ",
            createdByUid = " user-1 ",
            createdAt = " 2026-03-01T10:00:00Z ",
            roundSchedules = listOf(
                TournamentRoundScheduleDto(1, " 2026-04-01 ", " 09:30 "),
            ),
            agendaItems = listOf(
                TournamentAgendaItemDto(" Registration ", " 2026-04-01 ", " 08:30 "),
            ),
        ).toDomain()

        assertEquals("https://example.test/logo.png", tournament.associationLogoUrl)
        assertEquals("2026-04-01", tournament.eventStartDate)
        assertEquals("user-1", tournament.createdByUid)
        assertEquals("2026-03-01T10:00:00Z", tournament.createdAt)
        assertEquals("2026-04-01", tournament.roundSchedules.single().date)
        assertEquals("09:30", tournament.roundSchedules.single().startTime)
        assertEquals("Registration", tournament.agendaItems.single().title)
    }

    @Test
    fun freshTokenRetriesOnlyAfterUnauthorized() = runTest {
        var calls = 0
        val auth = fakeAuthRepository()

        val token = auth.withFreshIdToken {
            calls++
            if (calls == 1) throw BackendHttpException(HttpStatusCode.Unauthorized, "expired")
            it
        }

        assertEquals("new-token", token)
        assertEquals(2, calls)
    }

    @Test
    fun backendErrorIsLimitedAndKeepsStatus() {
        val error = BackendHttpException(
            status = HttpStatusCode.BadRequest,
            responseBody = "  " + "x".repeat(10_010) + "  ",
        ).toRepositoryAppError()

        val unexpected = assertIs<com.etologic.mahjongtournamentsuite.domain.model.AppError.Unexpected>(error)
        assertEquals("Backend error 400: ${"x".repeat(10_000)}…(truncated)", unexpected.message)
    }

    private fun fakeAuthRepository(): AuthRepository = object : AuthRepository {
        override suspend fun currentSession() = AuthSession("uid", "old-token", "refresh")

        override suspend fun refreshSession() = AppResult.Success(
            AuthSession("uid", "new-token", "refresh"),
        )

        override suspend fun signIn(email: String, password: String) = error("Not used")

        override suspend fun requestPasswordReset(email: String) = error("Not used")

        override suspend fun savedCredentials(): SavedCredentials? = null

        override suspend fun clearSavedCredentials() = Unit

        override suspend fun signOut() = Unit

        override suspend fun getMe(refreshMode: RefreshMode) = error("Not used")

        override suspend fun updateAlias(alias: String) = error("Not used")
        override suspend fun updatePhoto(contentType: String, bytes: ByteArray) = error("Not used")
        override suspend fun removePhoto() = error("Not used")
        override suspend fun deleteAccount() = error("Not used")
    }

    private class FakeConnectException(message: String) : Exception(message)

    @Test
    fun connectionFailuresBecomeNetworkErrors() {
        assertEquals(AppError.Network, FakeConnectException("Connection refused").toRepositoryAppError())
        assertEquals(AppError.Network, IllegalStateException("wrapper", FakeConnectException("down")).toRepositoryAppError())
    }

    @Test
    fun otherFailuresStayUnexpectedErrors() {
        assertIs<AppError.Unexpected>(IllegalStateException("broken").toRepositoryAppError())
    }

    @Test
    fun aFailureWithoutAMessageIsNamedByItsKindOrTheFallback() {
        assertEquals(AppError.Unexpected("IllegalStateException: broken"), IllegalStateException(" broken ").toRepositoryAppError())
        assertEquals(AppError.Unexpected("IllegalStateException"), IllegalStateException("  ").toRepositoryAppError())
        assertEquals(AppError.Unexpected("Could not save"), IllegalStateException().toRepositoryAppError("Could not save"))
    }

    @Test
    fun aConflictWithMissingOrOddFieldsKeepsWhatItCanRead() {
        fun conflict(body: String) = BackendHttpException(HttpStatusCode.Conflict, body).toRepositoryAppError()

        assertEquals(
            AppError.Conflict("", code = ConflictCode.ASSIGNMENTS_MISSING, args = emptyList()),
            conflict("""{"message":{},"details":{"code":"ASSIGNMENTS_MISSING","missing":{}}}"""),
        )
        assertEquals(
            AppError.Conflict("", code = ConflictCode.PREVIOUS_ROUND_IN_PLAY, args = listOf("", "", "true")),
            conflict("""{"details":{"code":"PREVIOUS_ROUND_IN_PLAY","roundId":[],"tables":"3","started":{}}}"""),
        )
        assertEquals(
            AppError.Conflict("", code = ConflictCode.PREVIOUS_ROUND_IN_PLAY, args = listOf("", "", "true")),
            conflict("""{"details":{"code":"PREVIOUS_ROUND_IN_PLAY"}}"""),
        )
        assertIs<AppError.Unexpected>(conflict("""{"details":{"code":{}}}"""))
        assertIs<AppError.Unexpected>(conflict("""{"details":{}}"""))
        assertIs<AppError.Unexpected>(conflict("""{"details":"x"}"""))
        assertIs<AppError.Unexpected>(conflict("""[1]"""))
        assertIs<AppError.Unexpected>(conflict("not json"))
    }
}
