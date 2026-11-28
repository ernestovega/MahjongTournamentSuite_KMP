package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.NoPlayersRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A ranking that cannot load one of its parts gives the error of that part, and an unknown tournament is an error. */
class RankingPresenterFailuresTest {
    private val server = FakeTournamentRepository().also {
        it.tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = true, numPlayers = 4, numRounds = 1))
        it.players = (1..4).map { id -> TournamentPlayer(id = id, name = "Player $id", team = 1) }
    }

    private suspend fun load(
        tournaments: TournamentRepository = server,
        players: PlayerRepository = NoPlayersRepository(),
        id: String = "t1",
    ): AppResult<RankingSnapshot> = RankingPresenter(tournaments, players, CalculateTournamentRankingsUseCase(), Logger.withTag("RankingPresenterFailuresTest")).load(id)

    @Test
    fun anUnknownTournamentIsAnError() = runTest {
        val result = load(id = "missing")
        assertIs<AppError.Unexpected>(assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun theTeamsThatDoNotLoadGiveTheirError() = runTest {
        val result = load(
            tournaments = object : TournamentRepository by server {
                override suspend fun listTournamentTeams(tournamentId: String, refreshMode: RefreshMode): AppResult<List<TournamentTeam>> = AppResult.Failure(AppError.Network)
            },
        )
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun theRegistryThatDoesNotLoadGivesItsError() = runTest {
        val registry = NoPlayersRepository()
        val result = load(
            players = object : PlayerRepository by registry {
                override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> = AppResult.Failure(AppError.Network)
            },
        )
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun theTablesThatDoNotLoadGiveTheirError() = runTest {
        val result = load(
            tournaments = object : TournamentRepository by server {
                override suspend fun getAllTablesWithHands(tournamentId: String, refreshMode: RefreshMode): AppResult<List<Pair<TableState, List<TableHand>>>> = AppResult.Failure(AppError.Network)
            },
        )
        assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
    }
}
