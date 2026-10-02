package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentAgendaItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.TournamentRoundScheduleDto
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
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
    fun tournamentMappingTrimsValuesAndUsesLegacyFallbacks() {
        val tournament = TournamentDto(
            id = "tournament-1",
            name = "Spring Open",
            isTeams = false,
            numPlayers = 16,
            numRounds = 4,
            associationLogoUrl = "  https://example.test/logo.png  ",
            eventStartDate = " 2026-04-01 ",
            createdByUid = " ",
            createdBy = "legacy-user",
            createdAt = null,
            created = "legacy-created",
            roundSchedules = listOf(
                TournamentRoundScheduleDto(1, " 2026-04-01 ", " 09:30 "),
            ),
            agendaItems = listOf(
                TournamentAgendaItemDto(" Registration ", " 2026-04-01 ", " 08:30 ", " 09:00 "),
            ),
        ).toDomain()

        assertEquals("https://example.test/logo.png", tournament.associationLogoUrl)
        assertEquals("2026-04-01", tournament.eventStartDate)
        assertEquals("legacy-user", tournament.createdByUid)
        assertEquals("legacy-created", tournament.createdAt)
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
    }
}
