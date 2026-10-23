package com.etologic.mahjongtournamentsuite.domain.model

data class PlayerInvitation(
    val invitationId: String,
    val tournamentPlayerId: Int,
    val email: String,
    val status: InvitationStatus,
    val expiresAt: String? = null,
)

/** A person who got an invitation and did not accept yet. The invitation was sent from one tournament. */
data class InvitedPerson(
    val invitationId: String,
    val tournamentId: String,
    val tournamentName: String,
    val playerId: Int,
    val emaId: String,
    val playerName: String,
    val email: String,
    val expiresAt: String? = null,
    val expired: Boolean = false,
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
