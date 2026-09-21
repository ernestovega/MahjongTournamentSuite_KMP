package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RankingSnapshot(
    val rankings: TournamentRankings,
    val tournamentPlayers: List<TournamentPlayer>,
    val basePlayers: List<Player>,
    val isTeams: Boolean,
    val tableCount: Int,
    val completedTableCount: Int,
)

class RankingPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val calculateRankings: CalculateTournamentRankingsUseCase,
    private val logger: Logger,
) {
    suspend fun load(tournamentId: String): AppResult<RankingSnapshot> {
        logger.i { "Loading tournament rankings." }

        val tournaments = when (val result = tournamentRepository.listTournaments()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val players = when (val result = tournamentRepository.listTournamentPlayers(tournamentId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val basePlayers = when (val result = playerRepository.listPlayers()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val tableSummaries = when (val result = tournamentRepository.listTournamentTables(tournamentId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }

        val tableResults = coroutineScope {
            val requestLimit = Semaphore(TABLE_REQUEST_LIMIT)
            tableSummaries.map { summary ->
                async {
                    requestLimit.withPermit {
                        tournamentRepository.getTableWithHands(
                            tournamentId = tournamentId,
                            roundId = summary.roundId,
                            tableId = summary.tableId,
                        )
                    }
                }
            }.awaitAll()
        }
        val firstFailure = tableResults.filterIsInstance<AppResult.Failure>().firstOrNull()
        if (firstFailure != null) return firstFailure
        val rankingTables = tableResults.map { result ->
            val value = (result as AppResult.Success).value
            RankingTable(table = value.first, hands = value.second)
        }

        val isTeams = tournaments.firstOrNull { it.id == tournamentId }?.isTeams == true
        return AppResult.Success(
            RankingSnapshot(
                rankings = calculateRankings(players, rankingTables, isTeams),
                tournamentPlayers = players,
                basePlayers = basePlayers,
                isTeams = isTeams,
                tableCount = tableSummaries.size,
                completedTableCount = tableSummaries.count { it.isCompleted },
            ),
        )
    }

    private companion object {
        const val TABLE_REQUEST_LIMIT = 8
    }
}
