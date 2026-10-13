package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.NoPlayersRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.syncTable
import com.etologic.mahjongtournamentsuite.presentation.screen.syncHands
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RankingPresenterTest {
    private fun server(): FakeTournamentRepository = FakeTournamentRepository().also {
        it.tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 4, numRounds = 2))
        it.players = (1..4).map { id -> TournamentPlayer(id = id, name = "Player $id", team = 0) }
        it.seed(syncTable(version = 2, roundId = 1, tableId = 1))
        it.seed(syncTable(version = 1, roundId = 2, tableId = 1))
    }

    private fun presenter(repository: FakeTournamentRepository) = RankingPresenter(
        tournamentRepository = repository,
        playerRepository = NoPlayersRepository(),
        calculateRankings = CalculateTournamentRankingsUseCase(),
        logger = Logger.withTag("RankingPresenterTest"),
    )

    @Test
    fun rankingAsksForAllTablesInOneCallAndNeverOneByOne() = runTest {
        val repository = server()

        val snapshot = assertIs<AppResult.Success<RankingSnapshot>>(presenter(repository).load("t1")).value

        assertEquals(2, snapshot.rankingTables.size)
        assertEquals(1, repository.calls.count { it.startsWith("all") })
        assertEquals(0, repository.calls.count { it.startsWith("get ") })
        assertEquals(0, repository.calls.count { it.startsWith("list ") })
    }

    @Test
    fun rankingKeepsTheTableOrderOfTheStore() = runTest {
        val repository = server()

        val snapshot = assertIs<AppResult.Success<RankingSnapshot>>(presenter(repository).load("t1")).value

        assertEquals(listOf(1 to 1, 2 to 1), snapshot.rankingTables.map { it.table.roundId to it.table.tableId })
        assertEquals(syncHands().size, snapshot.rankingTables.first().hands.size)
    }

    @Test
    fun theSnapshotKnowsTheDataRevisionOfItsTables() = runTest {
        val repository = server() // table versions 2 and 1

        val snapshot = assertIs<AppResult.Success<RankingSnapshot>>(presenter(repository).load("t1")).value

        assertEquals(3L, snapshot.dataRevision)
    }

    @Test
    fun forcedLoadAsksTheRepositoryToRefreshEverything() = runTest {
        val repository = server()

        presenter(repository).load("t1", force = true)

        assertEquals(listOf("all FORCE"), repository.calls.filter { it.startsWith("all") })
    }

    @Test
    fun normalLoadUsesWhatTheAppHoldsAndNeverChecksTheServer() = runTest {
        val repository = server()

        presenter(repository).load("t1")

        assertEquals(listOf("all CACHED"), repository.calls.filter { it.startsWith("all") })
        assertEquals(listOf("tournaments CACHED"), repository.calls.filter { it.startsWith("tournaments") })
        assertEquals(listOf("players CACHED"), repository.calls.filter { it.startsWith("players") })
    }
}
