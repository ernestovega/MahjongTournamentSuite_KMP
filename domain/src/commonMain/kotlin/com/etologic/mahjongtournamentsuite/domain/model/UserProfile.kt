package com.etologic.mahjongtournamentsuite.domain.model

data class UserProfile(
    val uid: String,
    val email: String,
    val alias: String = "",
)

enum class GlobalUserRole {
    EDITOR,
    ADMIN,
}

data class TournamentAssignment(
    val tournamentId: String,
    val tournamentName: String,
)

data class ManagedUser(
    val uid: String,
    val email: String,
    val alias: String,
    val role: GlobalUserRole,
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignment>,
)
