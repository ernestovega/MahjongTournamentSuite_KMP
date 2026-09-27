package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class TeamsPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val logger: Logger,
) {
    suspend fun loadTeams(tournamentId: String): AppResult<List<TournamentTeam>> =
        tournamentRepository.listTournamentTeams(tournamentId)

    suspend fun loadTournamentPlayers(tournamentId: String): AppResult<List<TournamentPlayer>> =
        tournamentRepository.listTournamentPlayers(tournamentId)

    suspend fun loadBasePlayers(): AppResult<List<Player>> = playerRepository.listPlayers()

    suspend fun loadTables(tournamentId: String): AppResult<List<TournamentTable>> =
        tournamentRepository.listTournamentTables(tournamentId)

    suspend fun updateTeam(
        tournamentId: String,
        teamId: Int,
        name: String,
        emaIds: List<String?>,
    ): AppResult<Unit> {
        logger.i { "Updating tournament team $teamId." }
        return tournamentRepository.updateTournamentTeam(tournamentId, teamId, name, emaIds)
    }
}
