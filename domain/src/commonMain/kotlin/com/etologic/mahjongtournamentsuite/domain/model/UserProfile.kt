package com.etologic.mahjongtournamentsuite.domain.model

data class UserProfile(
    val uid: String,
    val email: String,
)

enum class GlobalUserRole {
    REGULAR,
    SUPERADMIN,
}

data class TournamentAssignment(
    val tournamentId: String,
    val tournamentName: String,
    val role: TournamentRole,
)

data class ManagedUser(
    val uid: String,
    val email: String,
    val alias: String,
    val role: GlobalUserRole,
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignment>,
)
