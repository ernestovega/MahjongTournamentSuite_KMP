package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult

/** Editor-side actions that link a tournament slot to a player account and tell the player. */
interface PlayerAccountRepository {
    /**
     * Runs one action for one player or for many. Every player has its own result.
     * Only an editor starts it. Assigning a player never sends an email.
     */
    suspend fun runAction(
        tournamentId: String,
        action: PlayerAccountAction,
        items: List<PlayerAccountActionItem>,
    ): AppResult<List<PlayerAccountActionResult>>

    /**
     * Saves the email of players in their accounts. A player with no account gets an account without a password.
     * An account that has an email keeps it. The server sends no email.
     */
    suspend fun saveEmails(tournamentId: String, items: List<PlayerAccountActionItem>): AppResult<List<PlayerEmailResult>>
}
