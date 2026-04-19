package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class TablesPresenter(
    private val tournamentRepository: TournamentRepository,
    private val logger: Logger,
) {
    suspend fun loadRounds(tournamentId: String): AppResult<List<TournamentRound>> {
        logger.i { "Loading tournament rounds." }
        return tournamentRepository.listTournamentRounds(tournamentId)
    }

    suspend fun loadPlayers(tournamentId: String): AppResult<List<TournamentPlayer>> =
        tournamentRepository.listTournamentPlayers(tournamentId)

    suspend fun loadTables(
        tournamentId: String,
        roundId: Int?,
    ): AppResult<List<TournamentTable>> {
        logger.i { "Loading tournament tables." }
        return tournamentRepository.listTournamentTables(
            tournamentId = tournamentId,
            roundId = roundId,
        )
    }
}

