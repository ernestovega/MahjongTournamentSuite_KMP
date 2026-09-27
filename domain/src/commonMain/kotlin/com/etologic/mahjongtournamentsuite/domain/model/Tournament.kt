package com.etologic.mahjongtournamentsuite.domain.model

data class Tournament(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    /** First calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventStartDate: String? = null,
    /** Last calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventEndDate: String? = null,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class CreateTournamentRequest(
    val name: String,
    val eventStartDate: String,
    val eventEndDate: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
)

data class TournamentMember(
    val uid: String,
    val email: String,
    val role: GlobalUserRole,
)
