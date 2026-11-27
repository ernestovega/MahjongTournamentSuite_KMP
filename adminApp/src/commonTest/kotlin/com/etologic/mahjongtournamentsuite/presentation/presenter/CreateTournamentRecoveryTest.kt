package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A large tournament takes long to create, and the connection can drop before the answer comes. The tournament may
 * be on the server anyway: the presenter looks for it in the list before it reports the error.
 */
class CreateTournamentRecoveryTest {
    private val big = Tournament(id = "new", name = "Big Open", isTeams = false, numPlayers = 1000, numRounds = 1)
    private val old = Tournament(id = "old", name = "Big Open", isTeams = false, numPlayers = 1000, numRounds = 1)

    private class Server(private val lists: List<AppResult<List<Tournament>>>, private val created: AppResult<Tournament>) :
        TournamentRepository by FakeTournamentRepository() {
        var listCalls = 0
        val requests = mutableListOf<CreateTournamentRequest>()

        override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = lists[listCalls++.coerceAtMost(lists.lastIndex)]

        override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> {
            requests += request
            return created
        }
    }

    private fun drop(message: String): AppResult<Tournament> = AppResult.Failure(AppError.Unexpected(message))

    private suspend fun create(server: Server, players: Int = 1000) = CreateTournamentPresenter(
        tournamentRepository = server,
        logger = Logger.withTag("CreateTournamentRecoveryTest"),
        generateSchedule = DrawTournamentScheduleUseCase(),
    ).createTournament(
        name = "Big Open",
        shortName = "BIG",
        organizerIds = listOf("org-1"),
        eventStartDate = "2026-10-01",
        eventEndDate = "2026-10-01",
        hostCountry = "ES",
        hostCity = "Madrid",
        venue = TournamentVenue(name = "Hall", address = "Calle 1"),
        isTeams = false,
        countBestHands = false,
        countChickenHands = false,
        numPlayers = players,
        numRounds = 1,
        roundSchedules = listOf(TournamentRoundSchedule(1, "2026-10-01", "10:00")),
        agendaItems = emptyList(),
        matchDurationMinutes = 120,
        matchEndMode = MatchEndMode.FINISH_HAND,
        matchExtraMinutes = 0,
        timeZone = "Europe/Madrid",
        computeMode = CreateTournamentPresenter.ComputeMode.LIGHT,
        onProgress = {},
    )

    @Test
    fun aTournamentThatAppearsAfterADroppedConnectionIsTheResult() = runTest {
        val server = Server(listOf(AppResult.Success(listOf(old)), AppResult.Success(listOf(old, big))), drop("Connection dropped"))

        assertEquals(AppResult.Success(big), create(server))
        assertEquals(2, server.listCalls)
    }

    @Test
    fun withoutOneNewMatchTheErrorStays() = runTest {
        val none = Server(listOf(AppResult.Success(listOf(old)), AppResult.Success(listOf(old))), drop("Stream was prematurely closed"))
        assertIs<AppResult.Failure>(create(none))

        val two = Server(listOf(AppResult.Success(emptyList()), AppResult.Success(listOf(big, big.copy(id = "new2")))), drop("Failed to parse HTTP response"))
        assertIs<AppResult.Failure>(create(two))

        val other = Server(listOf(AppResult.Success(emptyList()), AppResult.Success(listOf(big.copy(numRounds = 2), big.copy(name = "Other")))), drop("connection dropped"))
        assertIs<AppResult.Failure>(create(other))
    }

    @Test
    fun aListThatFailsBeforeOrAfterLeavesTheError() = runTest {
        val before = Server(listOf(AppResult.Failure(AppError.Network)), drop("connection dropped"))
        assertIs<AppResult.Failure>(create(before))
        assertEquals(1, before.listCalls)

        val after = Server(listOf(AppResult.Success(emptyList()), AppResult.Failure(AppError.Network)), drop("connection dropped"))
        assertIs<AppResult.Failure>(create(after))
        assertEquals(2, after.listCalls)
    }

    @Test
    fun anotherErrorOrASmallTournamentDoesNotLookForIt() = runTest {
        val network = Server(listOf(AppResult.Success(emptyList())), AppResult.Failure(AppError.Network))
        assertIs<AppResult.Failure>(create(network))
        assertEquals(1, network.listCalls)

        val otherMessage = Server(listOf(AppResult.Success(emptyList())), drop("Bad request"))
        assertIs<AppResult.Failure>(create(otherMessage))
        assertEquals(1, otherMessage.listCalls)

        val small = Server(listOf(AppResult.Success(emptyList())), drop("connection dropped"))
        assertIs<AppResult.Failure>(create(small, players = 8))
        assertEquals(0, small.listCalls)
    }
}
