package com.etologic.mahjongtournamentsuite.domain.model

/** What the server did for one player. The server picks the action by the state of the slot. */
enum class PlayerAccountOutcome {
    /** The slot had no account. The server created it and sent the password reset email. */
    INVITED,

    /** The slot had an account. The server sent the password reset email. */
    RESET_SENT,

    FAILED,
}

/** Why an action failed for a player. */
enum class PlayerAccountFailure {
    PLAYER_NOT_FOUND,
    NO_EMA_ID,
    NO_EMAIL,
    EMAIL_IN_USE,
    EMAIL_FAILED,
    UNEXPECTED,
}

data class PlayerAccountActionResult(
    val playerId: Int,
    val outcome: PlayerAccountOutcome,
    val reason: PlayerAccountFailure? = null,
)
