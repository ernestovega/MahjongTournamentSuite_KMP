package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult

/** Editor-side action that gives players an account or resets their password. */
interface PlayerAccountRepository {
    /**
     * Sends the action that fits each player. A slot with no account gets one, made from the email of the import,
     * and the password reset email. A slot with an account gets the password reset email.
     * Every player has its own result. Importing or assigning a player never sends an email.
     */
    suspend fun send(tournamentId: String, playerIds: List<Int>): AppResult<List<PlayerAccountActionResult>>

    /**
     * Sets the pending email of slots, so "Invite" can create their accounts. An empty email clears it.
     * A slot with an account keeps the email of the account. Every item has its own result. It works also after
     * the tournament started, when the import is closed.
     */
    suspend fun saveEmails(tournamentId: String, items: List<PlayerEmailItem>): AppResult<List<PlayerEmailResult>>
}
