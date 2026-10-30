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
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction
import com.etologic.mahjongtournamentsuite.domain.usecase.CalculateTournamentRankingsUseCase
import com.etologic.mahjongtournamentsuite.presentation.util.tournamentDataRevision

data class RankingSnapshot(
    val tournament: Tournament,
    val rankings: TournamentRankings,
    val tournamentPlayers: List<TournamentPlayer>,
    val basePlayers: List<Player>,
    val tournamentTeams: List<TournamentTeam>,
    val isTeams: Boolean,
    val rankingTables: List<RankingTable>,
) {
    /** The data revision of the tables behind these rankings. See [tournamentDataRevision]. */
    val dataRevision: Long get() = tournamentDataRevision(rankingTables.map { it.table.version })
}

class RankingPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val calculateRankings: CalculateTournamentRankingsUseCase,
    private val logger: Logger,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun generateEmaReport(
        tournamentId: String,
        rankings: List<PlayerRanking>,
    ): AppResult<ByteArray> = tournamentRepository.generateEmaReport(tournamentId, rankings)
        .trackAction(telemetry, TelemetryAction.EMA_REPORT_EXPORTED)

    suspend fun load(tournamentId: String, force: Boolean = false): AppResult<RankingSnapshot> {
        logger.i { "Loading tournament rankings." }

        // A ranking screen stays still while it is open. It uses what the app holds and asks the server only
        // when nothing is held yet. The table editor keeps the held tables current.
        val refreshMode = if (force) RefreshMode.FORCE else RefreshMode.CACHED
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
        // One call gives every table. After the first load it makes no request.
        val tables = when (val result = tournamentRepository.getAllTablesWithHands(tournamentId, refreshMode)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val rankingTables = tables.map { (table, hands) -> RankingTable(table = table, hands = hands) }

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
