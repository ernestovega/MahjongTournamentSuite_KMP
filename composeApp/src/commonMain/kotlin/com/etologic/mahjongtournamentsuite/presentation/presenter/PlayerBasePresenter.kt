package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository

class PlayerBasePresenter(
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
) {
    suspend fun loadPlayers(): AppResult<List<Player>> = playerRepository.listPlayers()
    suspend fun createPlayer(player: Player): AppResult<Player> = playerRepository.createPlayer(player)
    suspend fun updatePlayer(player: Player): AppResult<Unit> = playerRepository.updatePlayer(player)
    suspend fun loadCountries(): AppResult<List<Country>> = tournamentRepository.listCountries()
}
