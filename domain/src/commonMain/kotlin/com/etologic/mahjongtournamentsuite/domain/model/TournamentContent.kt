package com.etologic.mahjongtournamentsuite.domain.model

data class TournamentPlayer(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    /** EMA number of the shared player assigned to this tournament slot, if any. */
    val assignedEmaId: String? = null,
    /** Tournament-only participant data when this slot is assigned to a non-member. */
    val nonMember: NonMemberPlayer? = null,
    /** Player account linked to this slot after the player accepted the invitation. */
    val accountUid: String? = null,
    val invitationStatus: InvitationStatus = InvitationStatus.NONE,
    val invitedEmail: String? = null,
    /** When the last invitation or registration email was sent to the player. Null when none was sent. */
    val contactedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

enum class InvitationStatus {
    NONE,
    INVITED,
    ACCEPTED,
}

data class NonMemberPlayer(
    val firstName: String,
    val lastName: String,
    val country: String,
)

/** Where a player stands in the contact process. Editors filter and run bulk actions with it. */
enum class PlayerContactState {
    /** The slot has no EMA id, so nobody can be contacted. */
    NO_EMA_ID,
    /** No email was sent yet and no account is linked. */
    NOT_INVITED,
    /** An account is linked, but no email was sent yet. */
    LINKED,
    /** An invitation was sent and the player did not accept yet. */
    INVITED,
    /** The player has an account and an email was sent, or the player accepted. */
    CONTACTED,
}

val TournamentPlayer.contactState: PlayerContactState
    get() = when {
        assignedEmaId.isNullOrBlank() -> PlayerContactState.NO_EMA_ID
        invitationStatus == InvitationStatus.INVITED -> PlayerContactState.INVITED
        accountUid != null && contactedAt != null -> PlayerContactState.CONTACTED
        accountUid != null -> PlayerContactState.LINKED
        else -> PlayerContactState.NOT_INVITED
    }

val TournamentPlayer.isAssigned: Boolean
    get() = !assignedEmaId.isNullOrBlank() || nonMember != null

/** A named group of the fixed schedule slots that share the same team id. */
data class TournamentTeam(
    val id: Int,
    val name: String,
    val playerIds: List<Int>,
)

/** A real player that is independent of every tournament. */
data class Player(
    /** Unique EMA number. This is the shared-player primary key. */
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val country: String = "",
    val photoUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

val Player.displayName: String
    get() = listOf(firstName, lastName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .uppercase()

val NonMemberPlayer.displayName: String
    get() = listOf(firstName, lastName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .uppercase()

data class Country(
    val code: String,
    val name: String,
)

data class TournamentRound(
    val roundId: Int,
)

data class TournamentTable(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val isCompleted: Boolean,
    val useTotalsOnly: Boolean,
    val hasProgress: Boolean,
    val hasValidManualTotals: Boolean = false,
    /** Server status: `empty`, `incomplete`, `partial` or `completed`. Blank for data from an older server. */
    val completionStatus: String = "",
    /** Scores of the best done hands of the table, from highest to lowest. See [topHandScores]. */
    val bestHandScores: List<Int> = emptyList(),
    val chickenHandCount: Int = 0,
    val version: Long = 0,
)
