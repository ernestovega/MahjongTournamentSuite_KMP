package com.etologic.mahjongtournamentsuite.domain.model

data class Tournament(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    /** When false, the app does not count or show the best hands. */
    val countBestHands: Boolean = true,
    /** When false, the app does not count or show the chicken hands. */
    val countChickenHands: Boolean = true,
    val shortName: String = "",
    val primaryColor: String = "#02B16B",
    val associationLogoUrl: String? = null,
    val hostCountry: String = "",
    val hostCity: String = "",
    /** Where the tournament takes place. Every field can be empty. */
    val venue: TournamentVenue = TournamentVenue(),
    /** First calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventStartDate: String? = null,
    /** Last calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventEndDate: String? = null,
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
    /** Minutes that a match lasts. A table ends this long after the start of its round. */
    val matchDurationMinutes: Int = DEFAULT_MATCH_DURATION_MINUTES,
    /** What happens when the time of a match is over. */
    val matchEndMode: MatchEndMode = MatchEndMode.STOP,
    /** With [MatchEndMode.FINISH_HAND], minutes after the end time in which the players can record the hand in play. */
    val matchExtraMinutes: Int = DEFAULT_MATCH_EXTRA_MINUTES,
    /** IANA time zone of the venue, for example "Europe/Madrid". The round times are local times of this zone. */
    val timeZone: String? = null,
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
    val countBestHands: Boolean = true,
    val countChickenHands: Boolean = true,
    val players: List<TournamentPlayer>,
    val tables: List<TournamentTable>,
    val shortName: String,
    val primaryColor: String,
    val associationLogoContentType: String? = null,
    val associationLogoBytes: ByteArray? = null,
    val associationLogoSourceTournamentId: String? = null,
    val hostCountry: String,
    val hostCity: String,
    val venue: TournamentVenue = TournamentVenue(),
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
    val matchDurationMinutes: Int = DEFAULT_MATCH_DURATION_MINUTES,
    val matchEndMode: MatchEndMode = MatchEndMode.STOP,
    val matchExtraMinutes: Int = DEFAULT_MATCH_EXTRA_MINUTES,
    val timeZone: String? = null,
)

const val DEFAULT_MATCH_DURATION_MINUTES = 120

const val DEFAULT_MATCH_EXTRA_MINUTES = 15

/** What happens to a table when the time of the match is over. */
enum class MatchEndMode {
    /** The match ends at the end time. The hand in play does not count. */
    STOP,

    /** The players finish the hand in play. They can record one more hand during the extra minutes. */
    FINISH_HAND,
}

/** The place of a tournament. The map link points to Google Maps. */
data class TournamentVenue(
    val name: String = "",
    val address: String = "",
    val mapUrl: String = "",
    /** Email address that players can use to contact the organizers. */
    val contactEmail: String = "",
) {
    val isEmpty: Boolean get() = name.isBlank() && address.isBlank() && mapUrl.isBlank() && contactEmail.isBlank()
}

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
)

data class TournamentMember(
    val uid: String,
    val email: String,
    val role: GlobalUserRole,
)
