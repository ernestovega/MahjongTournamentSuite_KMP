package com.etologic.mahjongtournamentsuite.domain.model

data class UserProfile(
    val uid: String,
    val email: String,
    val alias: String = "",
    /** EMA number of the player this account represents. Only player accounts have one. */
    val emaId: String? = null,
    val roles: Set<GlobalUserRole> = emptySet(),
)

enum class GlobalUserRole {
    EDITOR,
    ADMIN,
    PLAYER,
}

data class TournamentAssignment(
    val tournamentId: String,
    val tournamentName: String,
)

data class ManagedUser(
    val uid: String,
    val email: String,
    val alias: String,
    /** The highest capability of the account. */
    val role: GlobalUserRole,
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignment>,
    val roles: Set<GlobalUserRole> = setOf(role),
    val emaId: String? = null,
)
