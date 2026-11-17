package com.etologic.mahjongtournamentsuite.presentation.presenter

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerTournaments
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

    /** Deletes many EMA players. Only admins call it. Their accounts stay. */
    suspend fun deleteEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerDeleteResult>> {
        val results = mutableListOf<EmaPlayerDeleteResult>()
        for (chunk in emaIds.chunked(MAX_EMA_PLAYER_DELETES_PER_REQUEST)) {
            when (val result = playerRepository.deleteEmaPlayers(chunk)) {
                is AppResult.Success -> results += result.value
                // A request that fails after others went through must not hide them. Its players show as failed.
                is AppResult.Failure -> {
                    if (results.isEmpty()) return result.trackAction(telemetry, TelemetryAction.REGISTRY_PLAYERS_DELETED)
                    results += chunk.map { EmaPlayerDeleteResult(it, EmaPlayerDeleteOutcome.FAILED, EmaPlayerDeleteFailure.UNKNOWN) }
                }
            }
        }
        return AppResult.Success(results).trackAction(telemetry, TelemetryAction.REGISTRY_PLAYERS_DELETED)
    }

    /** The tournaments where each chosen player has data, for the warning before a delete. Players without data are left out. */
    suspend fun tournamentsOfEmaPlayers(emaIds: List<String>): AppResult<List<EmaPlayerTournaments>> {
        val all = mutableListOf<EmaPlayerTournaments>()
        for (chunk in emaIds.chunked(MAX_EMA_PLAYER_DELETES_PER_REQUEST)) {
            when (val result = playerRepository.tournamentsOfEmaPlayers(chunk)) {
                is AppResult.Success -> all += result.value
                is AppResult.Failure -> return result
            }
        }
        return AppResult.Success(all)
    }

    suspend fun loadCountries(force: Boolean = false): AppResult<List<Country>> = tournamentRepository.listCountries(force.mode())
}

/** The server deletes up to 50 EMA players in one request. */
internal const val MAX_EMA_PLAYER_DELETES_PER_REQUEST = 50

private fun Boolean.mode() = if (this) RefreshMode.FORCE else RefreshMode.IF_CHANGED
