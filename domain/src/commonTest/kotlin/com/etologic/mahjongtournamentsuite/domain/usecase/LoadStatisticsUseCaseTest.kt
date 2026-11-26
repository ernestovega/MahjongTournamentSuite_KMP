package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.UnusedTournamentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The statistics of one tournament: the names and profiles of the slots, and each read that can fail. */
class LoadStatisticsUseCaseTest {
    private val failure = AppResult.Failure(AppError.Unexpected("offline"))
    private val tournament = Tournament(id = "a", name = "Open a", isTeams = false, numPlayers = 4, numRounds = 1)
    private val registry = listOf(Player(emaId = "04000001", firstName = "Ana", lastName = "Ruiz", country = "ES"))

    private var tournaments: AppResult<List<Tournament>> = AppResult.Success(listOf(tournament))
    private var players: AppResult<List<Player>> = AppResult.Success(registry)
    private var slots: AppResult<List<TournamentPlayer>> = AppResult.Success(
        listOf(
            TournamentPlayer(id = 1, name = "Ana", team = 1, assignedEmaId = "04000001"),
            TournamentPlayer(id = 2, name = "Bea", team = 1, assignedEmaId = "04000099", country = "FR"),
            TournamentPlayer(id = 3, name = "", team = 1),
        ),
    )
    private var tables: AppResult<List<Pair<TableState, List<TableHand>>>> = AppResult.Success(emptyList())

    private val useCase = LoadStatisticsUseCase(
        tournamentRepository = object : UnusedTournamentRepository() {
            override suspend fun listTournaments(refreshMode: RefreshMode) = tournaments

            override suspend fun listTournamentPlayers(tournamentId: String, refreshMode: RefreshMode) = slots

            override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode) = tables
        },
        playerRepository = object : PlayerRepository {
            override suspend fun listPlayers(refreshMode: RefreshMode) = players

            override suspend fun createPlayer(player: Player): AppResult<Player> = error("Not used by this test")

            override suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = error("Not used by this test")

            override suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = error("Not used by this test")

            override suspend fun deleteEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerDeleteResult>> = error("Not used by this test")

            override suspend fun tournamentsOfEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerTournaments>> = error("Not used by this test")
        },
        calculateStatistics = CalculateStatisticsUseCase(),
    )

    @Test
    fun theSlotsAreNamedByTheRegistryOrByTheirNumber() = runTest {
        val data = assertIs<AppResult.Success<TournamentStatisticsData>>(useCase.tournament("a")).value

        assertEquals(listOf("Player 2", "Player 3"), listOf(data.names[2], data.names[3]), "a number outside the registry and no number")
        assertEquals("04000001", data.profiles.getValue(1).emaId)
        assertEquals(PlayerProfile("Bea", "04000099", "FR", null), data.profiles.getValue(2))
        assertEquals(PlayerProfile("Player 3", "", "", null), data.profiles.getValue(3))
        assertEquals(setOf("04000001", "04000099", "a#3"), data.rivalNames.keys)
    }

    @Test
    fun eachFailedReadIsTheResult() = runTest {
        assertIs<AppResult.Failure>(useCase.tournament("missing"))

        tables = failure
        assertEquals(failure, useCase.tournament("a"))
        slots = failure
        assertEquals(failure, useCase.tournament("a"))
        players = failure
        assertEquals(failure, useCase.tournament("a"))
        tournaments = failure
        assertEquals(failure, useCase.tournament("a"))
    }
}
