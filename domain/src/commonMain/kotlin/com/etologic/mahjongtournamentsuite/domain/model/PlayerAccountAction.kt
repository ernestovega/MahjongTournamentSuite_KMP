package com.etologic.mahjongtournamentsuite.domain.model

/** What the server did for one player. The server picks the action by the state of the slot. */
enum class PlayerAccountOutcome {
    /** The slot had no account. The server created it and sent the password reset email. */
    INVITED,

    /** The slot had an account. The server sent the reminder: an email with a link to the tournament. */
    REMINDED,

    FAILED,
}

/** Why an action failed for a player. */
enum class PlayerAccountFailure {
    PLAYER_NOT_FOUND,
    NO_EMA_ID,
    NO_EMAIL,
    EMAIL_IN_USE,
    INVALID_EMAIL,
    EMAIL_FAILED,
    UNEXPECTED,
}

data class PlayerAccountActionResult(
    val playerId: Int,
    val outcome: PlayerAccountOutcome,
    val reason: PlayerAccountFailure? = null,
)

data class PlayerEmailItem(val playerId: Int, val email: String)

enum class PlayerEmailOutcome { SAVED, CLEARED, FAILED }

/** Why a pending email was not saved. */
enum class PlayerEmailFailure { PLAYER_NOT_FOUND, NO_EMA_ID, HAS_ACCOUNT, INVALID_EMAIL, EMAIL_IN_USE }

data class PlayerEmailResult(
    val playerId: Int,
    val outcome: PlayerEmailOutcome,
    val reason: PlayerEmailFailure? = null,
)
