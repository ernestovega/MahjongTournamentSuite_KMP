package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerCountPresenterTest {
    private val individual = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 20, numRounds = 3)

    private fun players(registry: List<Player> = emptyList()): PlayerRepository = Proxy.newProxyInstance(
        PlayerRepository::class.java.classLoader,
        arrayOf(PlayerRepository::class.java),
    ) { _, method, _ ->
        if (method.name == "listPlayers") AppResult.Success(registry) else AppResult.Failure(AppError.Unexpected("unused"))
    } as PlayerRepository

    private fun registrations(list: AdminRegistrations?, calls: MutableList<String> = mutableListOf()): RegistrationRepository = Proxy.newProxyInstance(
        RegistrationRepository::class.java.classLoader,
        arrayOf(RegistrationRepository::class.java),
    ) { _, method, _ ->
        calls += method.name
        if (method.name == "loadAdminList" && list != null) AppResult.Success(list) else AppResult.Failure(AppError.Network)
    } as RegistrationRepository

    private fun presenter(
        tournaments: FakeTournamentRepository,
        registry: List<Player> = emptyList(),
        list: AdminRegistrations? = null,
        calls: MutableList<String> = mutableListOf(),
    ) = PlayerCountPresenter(tournaments, players(registry), registrations(list, calls), DrawTournamentScheduleUseCase(), Logger.withTag("test"))

    private fun slots(count: Int, isTeams: Boolean = false) = (1..count).map { id ->
        TournamentPlayer(id = id, name = "Player $id", team = if (isTeams) (id - 1) / 4 + 1 else 0)
    }

    @Test
    fun loadsTheSlotsWithTheNamesOfTheirPeopleInOrder() = runTest {
        val tournaments = FakeTournamentRepository().apply {
            players = slots(20).reversed().map {
                when (it.id) {
                    1 -> it.copy(assignedEmaId = "10000001", displayName = "ANA LÓPEZ")
                    2 -> it.copy(assignedEmaId = "10000002")
                    else -> it
                }
            }
        }
        val registry = listOf(Player(emaId = "10000002", firstName = "Bo", lastName = "Berg"))
        val data = assertIs<AppResult.Success<PlayerCountData>>(presenter(tournaments, registry).load(individual)).value
        assertEquals((1..20).toList(), data.slots.map { it.id })
        assertEquals("ANA LÓPEZ", data.slots[0].name)
        assertEquals("BO BERG", data.slots[1].name.uppercase())
        assertEquals("Player 3", data.slots[2].name)
        assertNull(data.slots[2].emaId)
        assertEquals(emptyList(), data.teams)
        assertEquals(emptyList(), data.waiting)
    }

    @Test
    fun loadsTheTeamsOfATeamTournamentAndTheWaitingListOfOneWithRegistrations() = runTest {
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val tournaments = FakeTournamentRepository().apply {
            players = slots(20, isTeams = true)
            this.teams = teams.reversed()
        }
        val list = AdminRegistrations(
            settings = RegistrationSettings(),
            isTeams = true,
            drawPending = false,
            registrations = listOf(
                AdminRegistration("20000002", "Second", "ES", RegistrationStatus.WAITING, 2),
                AdminRegistration("20000009", "", "ES", RegistrationStatus.WAITING, 1),
                AdminRegistration("20000003", "Confirmed", "ES", RegistrationStatus.CONFIRMED, 1),
            ),
            teams = emptyList(),
        )
        val tournament = individual.copy(isTeams = true, registration = RegistrationSettings())
        val data = assertIs<AppResult.Success<PlayerCountData>>(presenter(tournaments, list = list).load(tournament)).value
        assertEquals((1..5).toList(), data.teams.map { it.id })
        assertEquals(listOf("20000009" to "20000009", "20000002" to "Second"), data.waiting.map { it.emaId to it.name })
    }

    @Test
    fun aTournamentWithoutRegistrationsDoesNotAskForThem() = runTest {
        val calls = mutableListOf<String>()
        val tournaments = FakeTournamentRepository().apply { players = slots(16) }
        assertIs<AppResult.Success<PlayerCountData>>(presenter(tournaments, calls = calls).load(individual.copy(numPlayers = 16)))
        assertEquals(emptyList(), calls)
    }

    @Test
    fun aFailedLoadGivesItsError() = runTest {
        val tournaments = FakeTournamentRepository().apply { failPlayers = true }
        assertIs<AppResult.Failure>(presenter(tournaments).load(individual))
        val withRegistrations = FakeTournamentRepository().apply { players = slots(16) }
        val failed = presenter(withRegistrations, list = null).load(individual.copy(registration = RegistrationSettings()))
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(failed).error)
    }

    @Test
    fun sendsAValidDrawForTheNewNumberWithTheChoiceOfTheOrganizer() = runTest {
        val tournaments = FakeTournamentRepository().apply { writesSucceed = true }
        var progressCalls = 0
        val result = presenter(tournaments).change(individual, 16, listOf(5, 6, 7, 8), emptyList()) { progressCalls++ }
        assertEquals(16, assertIs<AppResult.Success<PlayerCountOutcome>>(result).value.tournament.numPlayers)
        val request = tournaments.playerCountRequest!!
        assertEquals("t1", request.tournamentId)
        assertEquals((1..16).toList(), request.players.map { it.id }.sorted())
        assertEquals(listOf(5, 6, 7, 8), request.removedSlotIds)
        assertEquals(3 * 4, request.tables.size)
        assertTrue(request.numTries > 0)
        request.tables.groupBy { it.roundId }.values.forEach { round ->
            assertEquals((1..16).toList(), round.flatMap { it.playerIds }.sorted(), "Every player plays once per round")
        }
        val pairs = request.tables.flatMap { table -> table.playerIds.flatMap { a -> table.playerIds.filter { it > a }.map { a to it } } }
        assertEquals(pairs.size, pairs.toSet().size, "Two players never meet twice")
    }

    @Test
    fun aTeamTournamentGetsADrawWithTeamsOfFour() = runTest {
        val tournaments = FakeTournamentRepository().apply { writesSucceed = true }
        val tournament = individual.copy(isTeams = true, numPlayers = 16, numRounds = 2)
        presenter(tournaments).change(tournament, 20, emptyList(), listOf("30000001")) {}
        val request = tournaments.playerCountRequest!!
        assertEquals(listOf("30000001"), request.waitingEmaIds)
        assertEquals(List(5) { 4 }, request.players.groupBy { it.team }.values.map { it.size })
        request.tables.forEach { table ->
            val teams = table.playerIds.map { id -> request.players.first { it.id == id }.team }
            assertEquals(4, teams.toSet().size, "Two players of one team never share a table")
        }
    }

    @Test
    fun refusesAnInvalidNumberWithoutCallingTheServer() = runTest {
        val tournaments = FakeTournamentRepository().apply { writesSucceed = true }
        listOf(20, 18, 12).forEach { target ->
            val result = presenter(tournaments).change(individual, target, emptyList(), emptyList()) {}
            assertEquals(RegistrationErrorCode.BAD_PLAYER_COUNT, (assertIs<AppResult.Failure>(result).error as AppError.Registration).code)
        }
        assertNull(tournaments.playerCountRequest)
    }

    @Test
    fun givesTheErrorOfTheServer() = runTest {
        val refused = AppError.Registration(RegistrationErrorCode.TOURNAMENT_STARTED)
        val tournaments = FakeTournamentRepository().apply { playerCountError = refused }
        val result = presenter(tournaments).change(individual, 16, listOf(1, 2, 3, 4), emptyList()) {}
        assertEquals(refused, assertIs<AppResult.Failure>(result).error)
    }
}
