package com.etologic.mahjongtournamentsuite.domain.model

data class Tournament(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val shortName: String = "",
    val primaryColor: String = "#02B16B",
    val associationLogoUrl: String? = null,
    val hostCountry: String = "",
    val hostCity: String = "",
    /** First calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventStartDate: String? = null,
    /** Last calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventEndDate: String? = null,
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
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
    val shortName: String,
    val primaryColor: String,
    val associationLogoContentType: String? = null,
    val associationLogoBytes: ByteArray? = null,
    val associationLogoSourceTournamentId: String? = null,
    val hostCountry: String,
    val hostCity: String,
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
)

/** Local tournament date and start time for one round. */
data class TournamentRoundSchedule(
    val roundId: Int,
    /** ISO-8601 calendar date (yyyy-MM-dd), or null while it is not configured. */
    val date: String? = null,
    /** Local tournament time (HH:mm), or null while it is not configured. */
    val startTime: String? = null,
)

/** One item in the player-facing tournament agenda. */
data class TournamentAgendaItem(
    val title: String,
    /** ISO-8601 calendar date (yyyy-MM-dd), or null while it is not configured. */
    val date: String? = null,
    /** Local tournament time (HH:mm), or null while it is not configured. */
    val startTime: String? = null,
    /** Optional local end time (HH:mm). */
    val endTime: String? = null,
)

data class TournamentMember(
    val uid: String,
    val email: String,
    val role: GlobalUserRole,
)
