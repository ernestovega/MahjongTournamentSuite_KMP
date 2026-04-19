package com.etologic.mahjongtournamentsuite.domain.model

enum class TournamentRole {
    READER,
    EDITOR,
    ADMIN,
}

data class Tournament(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    val createdByUid: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class CreateTournamentRequest(
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
)

data class TournamentMember(
    val uid: String,
    val role: TournamentRole,
)
