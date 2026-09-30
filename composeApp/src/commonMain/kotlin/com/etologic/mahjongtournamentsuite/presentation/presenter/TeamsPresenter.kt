package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class TeamsPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val logger: Logger,
) {
    suspend fun loadTeams(tournamentId: String, force: Boolean = false): AppResult<List<TournamentTeam>> =
        tournamentRepository.listTournamentTeams(tournamentId, force.mode())

    suspend fun loadTournamentPlayers(tournamentId: String, force: Boolean = false): AppResult<List<TournamentPlayer>> =
        tournamentRepository.listTournamentPlayers(tournamentId, force.mode())

    suspend fun loadBasePlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun loadTables(tournamentId: String, force: Boolean = false): AppResult<List<TournamentTable>> =
        tournamentRepository.listTournamentTables(tournamentId, refreshMode = force.mode())

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

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
