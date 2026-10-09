package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray> = tournamentRepository.generateEmaReport(tournamentId, rankings)

    suspend fun load(tournamentId: String, force: Boolean = false): AppResult<RankingSnapshot> {
        logger.i { "Loading tournament rankings." }

        val refreshMode = if (force) RefreshMode.FORCE else RefreshMode.IF_CHANGED
        val tournaments = when (val result = tournamentRepository.listTournaments(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val players = when (val result = tournamentRepository.listTournamentPlayers(tournamentId, refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val tournament = tournaments.firstOrNull { it.id == tournamentId }
            ?: return AppResult.Failure(com.etologic.mahjongtournamentsuite.domain.model.AppError.Unexpected("Tournament not found"))
        val tournamentTeams = if (tournament.isTeams) {
            when (val result = tournamentRepository.listTournamentTeams(tournamentId, refreshMode)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
        } else {
            emptyList()
        }
        val basePlayers = when (val result = playerRepository.listPlayers(refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val tableSummaries = when (val result = tournamentRepository.listTournamentTables(tournamentId, refreshMode = refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }

        // One request for each round, not for each table. The editor uses the same cache entries.
        val roundResults = coroutineScope {
            tableSummaries.map { it.roundId }.distinct().map { roundId ->
                async {
                    tournamentRepository.getRoundTablesWithHands(
                        tournamentId = tournamentId,
                        roundId = roundId,
                        refreshMode = refreshMode,
                    )
                }
            }.awaitAll()
        }
        val firstFailure = roundResults.filterIsInstance<AppResult.Failure>().firstOrNull()
        if (firstFailure != null) return firstFailure
        val rankingTables = roundResults
            .flatMap { (it as AppResult.Success).value }
            .sortedWith(compareBy({ it.first.roundId }, { it.first.tableId }))
            .map { (table, hands) -> RankingTable(table = table, hands = hands) }

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
}
