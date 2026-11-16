package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountDeleteResult

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

    /** Deletes the account of each EMA player, never the EMA player. Every player has its own result. Up to 50 per request. */
    suspend fun deletePlayerAccounts(emaIds: List<String>): AppResult<List<PlayerAccountDeleteResult>>
}
