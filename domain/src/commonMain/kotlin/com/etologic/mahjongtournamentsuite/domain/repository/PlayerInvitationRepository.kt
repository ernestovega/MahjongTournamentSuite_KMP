package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.InvitedPerson
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerInvitation

/** Editor-side actions that link a tournament slot to a player account and tell the player. */
interface PlayerInvitationRepository {
    suspend fun listInvitations(tournamentId: String): AppResult<List<PlayerInvitation>>

    /** Lists the people who did not accept their invitation yet, in all tournaments of the editor. */
    suspend fun listInvitedPeople(): AppResult<List<InvitedPerson>>

    /**
     * Runs one action for one player or for many. Every player has its own result.
     * Only an editor starts it. Assigning a player never sends an email.
     */
    suspend fun runAction(
        tournamentId: String,
        action: PlayerAccountAction,
        items: List<PlayerAccountActionItem>,
    ): AppResult<List<PlayerAccountActionResult>>

    /** Saves the email of players that have no account yet. A later send uses it. */
    suspend fun saveEmails(tournamentId: String, items: List<PlayerAccountActionItem>): AppResult<Unit>
}
