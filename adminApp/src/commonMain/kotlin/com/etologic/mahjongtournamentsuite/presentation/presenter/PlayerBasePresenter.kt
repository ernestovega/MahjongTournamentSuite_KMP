package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class PlayerBasePresenter(
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
) {
    suspend fun loadPlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())
    suspend fun createPlayer(player: Player): AppResult<Player> = playerRepository.createPlayer(player)
    suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = playerRepository.updatePlayer(previousEmaId, player)
    suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> =
        playerRepository.updatePlayerPhoto(emaId, contentType, bytes)
    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.mode())
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
