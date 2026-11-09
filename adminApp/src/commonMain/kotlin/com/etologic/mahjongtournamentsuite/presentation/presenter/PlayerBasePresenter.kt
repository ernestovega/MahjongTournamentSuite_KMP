package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.trackAction

class PlayerBasePresenter(
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
    private val telemetry: Telemetry = NoTelemetry,
) {
    suspend fun loadPlayers(force: Boolean = false): AppResult<List<Player>> = playerRepository.listPlayers(force.mode())
    suspend fun createPlayer(player: Player): AppResult<Player> = playerRepository.createPlayer(player).trackAction(telemetry, TelemetryAction.REGISTRY_PLAYER_CREATED)
    suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit> = playerRepository.updatePlayer(previousEmaId, player)
        .trackAction(telemetry, TelemetryAction.REGISTRY_PLAYER_UPDATED)
    suspend fun updatePlayerPhoto(emaId: String, contentType: String, bytes: ByteArray): AppResult<Player> = playerRepository.updatePlayerPhoto(emaId, contentType, bytes)
        .trackAction(telemetry, TelemetryAction.REGISTRY_PHOTO_UPDATED)
    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.mode())
}

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
