package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerDeleteResult

/** Access to the shared player base. EMA number is the player primary key. */
interface PlayerRepository {
    suspend fun listPlayers(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<List<Player>>

    suspend fun createPlayer(player: Player): AppResult<Player>

    suspend fun updatePlayer(previousEmaId: String, player: Player): AppResult<Unit>

    suspend fun updatePlayerPhoto(
        emaId: String,
        contentType: String,
        bytes: ByteArray,
    ): AppResult<Player>

    /** Deletes many players. Every player has its own result. The server takes up to 200 in one request. */
    suspend fun deletePlayers(emaIds: List<String>): AppResult<List<PlayerDeleteResult>>
}
