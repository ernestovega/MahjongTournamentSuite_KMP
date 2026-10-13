package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult

/** Player-side account actions. */
interface PlayerAccountRepository {
    /**
     * Accepts an invitation from the link of the email.
     * A new account sends a [password]. An existing account signs in first and sends no password.
     */
    suspend fun acceptInvitation(token: String, password: String?): AppResult<String>
}
