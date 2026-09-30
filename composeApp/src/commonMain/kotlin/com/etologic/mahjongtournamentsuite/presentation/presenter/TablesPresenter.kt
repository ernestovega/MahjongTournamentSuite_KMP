package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class TablesPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val logger: Logger,
) {
    suspend fun loadRounds(tournamentId: String, force: Boolean = false): AppResult<List<TournamentRound>> {
        logger.i { "Loading tournament rounds." }
        return tournamentRepository.listTournamentRounds(tournamentId, force.mode())
    }

    suspend fun loadPlayers(tournamentId: String, force: Boolean = false): AppResult<List<TournamentPlayer>> =
        tournamentRepository.listTournamentPlayers(tournamentId, force.mode())

    suspend fun loadBasePlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())

    suspend fun loadTables(
        tournamentId: String,
        roundId: Int?,
        force: Boolean = false,
    ): AppResult<List<TournamentTable>> {
        logger.i { "Loading tournament tables." }
        return tournamentRepository.listTournamentTables(
            tournamentId = tournamentId,
            roundId = roundId,
            refreshMode = force.mode(),
        )
    }
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
