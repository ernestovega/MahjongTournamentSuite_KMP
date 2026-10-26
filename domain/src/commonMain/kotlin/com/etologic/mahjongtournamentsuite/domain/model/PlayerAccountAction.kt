package com.etologic.mahjongtournamentsuite.domain.model

enum class PlayerAccountAction {
    /**
     * Invite a player whose account has no password yet (the password reset email), or tell a player with a password
     * that the registration exists. A player with no account needs an email, and the server creates the account.
     */
    SEND,

    /** Send again. */
    RESEND,

    /** Remove the link between the slot and the account of this tournament. The account stays. */
    REVOKE,
}

data class PlayerAccountActionItem(
    val playerId: Int,
    val email: String? = null,
)

enum class PlayerAccountOutcome {
    INVITED,
    NOTIFIED,
    REVOKED,
    SKIPPED,
    FAILED,
}

data class PlayerAccountActionResult(
    val playerId: Int,
    val outcome: PlayerAccountOutcome,
    val message: String? = null,
)

enum class PlayerEmailOutcome {
    /** The server created the account of the player with this email. */
    CREATED,

    /** The account has this email already. */
    UNCHANGED,

    /** The account has another email, and the account email stays. [PlayerEmailResult.message] holds it. */
    KEPT_ACCOUNT_EMAIL,

    /** The server refused the email. [PlayerEmailResult.message] holds the reason. */
    FAILED,
}

data class PlayerEmailResult(
    val playerId: Int,
    val outcome: PlayerEmailOutcome,
    val message: String? = null,
)
