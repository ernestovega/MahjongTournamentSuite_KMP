package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository

class PlayersPresenter(
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val logger: Logger,
) {
    suspend fun loadPlayers(tournamentId: String): AppResult<List<TournamentPlayer>> {
        logger.i { "Loading tournament players." }
        return tournamentRepository.listTournamentPlayers(tournamentId)
    }

    suspend fun loadBasePlayers(): AppResult<List<Player>> = playerRepository.listPlayers()

    suspend fun loadCountries(): AppResult<List<Country>> = tournamentRepository.listCountries()

    suspend fun assignPlayer(
        tournamentId: String,
        tournamentPlayerId: Int,
        emaId: String?,
    ): AppResult<Unit> = tournamentRepository.assignTournamentPlayer(tournamentId, tournamentPlayerId, emaId)
}
