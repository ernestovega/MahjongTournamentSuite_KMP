package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult

/** Editor-side action that gives players an account or resets their password. */
interface PlayerAccountRepository {
    /**
     * Sends the action that fits each player. A slot with no account gets one, made from the email of the import,
     * and the password reset email. A slot with an account gets the password reset email.
     * Every player has its own result. Importing or assigning a player never sends an email.
     */
    suspend fun send(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>>
}
