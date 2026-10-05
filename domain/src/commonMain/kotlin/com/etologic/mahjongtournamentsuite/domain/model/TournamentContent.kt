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
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class NonMemberPlayer(
    val firstName: String,
    val lastName: String,
    val country: String,
)

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
    val usePointsCalculation: Boolean,
    val hasProgress: Boolean,
    val hasValidManualTotals: Boolean = false,
    /** Server status: `empty`, `incomplete`, `partial` or `completed`. Blank for data from an older server. */
    val completionStatus: String = "",
    val bestHandScore: Int? = null,
    val chickenHandCount: Int = 0,
    val version: Long = 0,
)
