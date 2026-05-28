package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class PlayersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val logger: Logger,
) {
    suspend fun loadPlayers(tournamentId: String): AppResult<List<TournamentPlayer>> {
        logger.i { "Loading tournament players." }
        return tournamentRepository.listTournamentPlayers(tournamentId)
    }
}

