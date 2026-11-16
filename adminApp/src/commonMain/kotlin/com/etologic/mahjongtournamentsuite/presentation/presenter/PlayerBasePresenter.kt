package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerDeleteResult
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

    /** Deletes many players for good. Only admins call it. A player that a tournament or an account uses stays. */
    suspend fun deletePlayers(emaIds: List<String>): AppResult<List<PlayerDeleteResult>> {
        val results = mutableListOf<PlayerDeleteResult>()
        for (chunk in emaIds.chunked(MAX_PLAYER_DELETES_PER_REQUEST)) {
            when (val result = playerRepository.deletePlayers(chunk)) {
                is AppResult.Success -> results += result.value
                // A request that fails after others went through must not hide them. Its players show as failed.
                is AppResult.Failure -> {
                    if (results.isEmpty()) return result.trackAction(telemetry, TelemetryAction.REGISTRY_PLAYERS_DELETED)
                    results += chunk.map { PlayerDeleteResult(it, PlayerDeleteOutcome.FAILED, PlayerDeleteFailure.UNKNOWN) }
                }
            }
        }
        return AppResult.Success(results).trackAction(telemetry, TelemetryAction.REGISTRY_PLAYERS_DELETED)
    }

    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.mode())
}

/** The server deletes up to 200 EMA players in one request. */
internal const val MAX_PLAYER_DELETES_PER_REQUEST = 200

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
