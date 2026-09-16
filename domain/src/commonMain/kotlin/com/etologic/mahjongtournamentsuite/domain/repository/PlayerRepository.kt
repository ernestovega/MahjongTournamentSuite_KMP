package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player

/** Access to the shared player base. EMA number is the player primary key. */
interface PlayerRepository {
    suspend fun listPlayers(): AppResult<List<Player>>

    suspend fun createPlayer(player: Player): AppResult<Player>

    suspend fun updatePlayer(player: Player): AppResult<Unit>
}
