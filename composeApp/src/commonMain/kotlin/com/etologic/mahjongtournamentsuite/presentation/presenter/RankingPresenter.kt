package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RankingSnapshot(
    val tournament: Tournament,
    val rankings: TournamentRankings,
    val tournamentPlayers: List<TournamentPlayer>,
    val basePlayers: List<Player>,
    val tournamentTeams: List<TournamentTeam>,
    val isTeams: Boolean,
    val rankingTables: List<RankingTable>,
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
        val tournament = tournaments.firstOrNull { it.id == tournamentId }
            ?: return AppResult.Failure(com.etologic.mahjongtournamentsuite.domain.model.AppError.Unexpected("Tournament not found"))
        val tournamentTeams = if (tournament.isTeams) {
            when (val result = tournamentRepository.listTournamentTeams(tournamentId)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
        } else {
            emptyList()
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

        val isTeams = tournament.isTeams
        return AppResult.Success(
            RankingSnapshot(
                tournament = tournament,
                rankings = calculateRankings(players, rankingTables, isTeams),
                tournamentPlayers = players,
                basePlayers = basePlayers,
                tournamentTeams = tournamentTeams,
                isTeams = isTeams,
                rankingTables = rankingTables,
            ),
        )
    }

    private companion object {
        const val TABLE_REQUEST_LIMIT = 8
    }
}
