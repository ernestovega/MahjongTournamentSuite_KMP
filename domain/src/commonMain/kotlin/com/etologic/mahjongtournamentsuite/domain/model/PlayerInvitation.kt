package com.etologic.mahjongtournamentsuite.domain.model

data class PlayerInvitation(
    val invitationId: String,
    val tournamentPlayerId: Int,
    val email: String,
    val status: InvitationStatus,
    val expiresAt: String? = null,
)

enum class PlayerAccountAction {
    /** Invite a new player, or tell a linked player that the registration exists. */
    SEND,

    /** Send again. An invitation gets a new link and the old link stops working. */
    RESEND,

    /** Revoke a pending invitation, or remove the link to an account. */
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
