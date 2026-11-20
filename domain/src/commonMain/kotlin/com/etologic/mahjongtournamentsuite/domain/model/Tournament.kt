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
    /** The organizations that own the tournament. The first one is the main organizer. */
    val organizerIds: List<String> = emptyList(),
    /** The organizers as the tournament shows them, in the order of [organizerIds]. */
    val organizers: List<TournamentOrganizer> = emptyList(),
    /** The color of the main organizer. A tournament has no color of its own. */
    val primaryColor: String = "#02B16B",
    /** The logo of the main organizer. A tournament has no logo of its own. */
    val associationLogoUrl: String? = null,
    /** Hash of the logo file. Tournaments that share a logo have the same hash. */
    val associationLogoHash: String? = null,
    val hostCountry: String = "",
    val hostCity: String = "",
    /** Where the tournament takes place. Every field can be empty. */
    val venue: TournamentVenue = TournamentVenue(),
    /** First calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventStartDate: String? = null,
    /** Last calendar date of the tournament in ISO-8601 format (yyyy-MM-dd). */
    val eventEndDate: String? = null,
    /** The planned start of each round. The real start is the Play of the Timer ([roundClocks]). */
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    /** The clocks of the rounds that started. */
    val roundClocks: List<RoundClock> = emptyList(),
    /** The manual controls of the rounds (the plan B of the Timer). A round without one follows its clock. */
    val roundControls: List<RoundControl> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
    /** Minutes that a match lasts, from the Play of the Timer. */
    val matchDurationMinutes: Int = DEFAULT_MATCH_DURATION_MINUTES,
    /** What happens when the time of a match is over. */
    val matchEndMode: MatchEndMode = MatchEndMode.STOP,
    /** With [MatchEndMode.FINISH_HAND], minutes after the end time in which the players can record the hand in play. */
    val matchExtraMinutes: Int = DEFAULT_MATCH_EXTRA_MINUTES,
    /** IANA time zone of the venue, for example "Europe/Madrid". The round times are local times of this zone. */
    val timeZone: String? = null,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    /** What the signed-in account may do in this tournament. Only the tournament list of the management app has it. */
    val permissions: Set<Permission> = emptySet(),
    /** False hides a test tournament from the public list. Only its staff see it. */
    val isPublic: Boolean = true,
    /** False hides the differences button on the table screen of the player app. */
    val showTableDiffs: Boolean = true,
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /**
     * True for a tournament with registrations whose draw is not made yet. It has no players and no tables: the
     * organizer makes the draw when the registrations close. See [RegistrationSettings].
     */
    val drawPending: Boolean = false,
    /** The registrations of the tournament, or null when it never had them. */
    val registration: RegistrationSettings? = null,
) {
    /** The registrations are part of the tournament now: before its draw. */
    val inRegistration: Boolean get() = drawPending && registration != null
}

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
    /** The organizations that own the tournament. The first one is the main organizer. At least one. */
    val organizerIds: List<String>,
    val hostCountry: String,
    val hostCity: String,
    val venue: TournamentVenue = TournamentVenue(),
    val roundSchedules: List<TournamentRoundSchedule> = emptyList(),
    val agendaItems: List<TournamentAgendaItem> = emptyList(),
    val matchDurationMinutes: Int = DEFAULT_MATCH_DURATION_MINUTES,
    val matchEndMode: MatchEndMode = MatchEndMode.STOP,
    val matchExtraMinutes: Int = DEFAULT_MATCH_EXTRA_MINUTES,
    val timeZone: String? = null,
    /**
     * Not null: the tournament opens registrations and has no players or draw yet ([players] and [tables] are empty,
     * [numPlayers] is ignored). Only status, maxPlayers, closesOn and slug count.
     */
    val registration: RegistrationSettings? = null,
)

/** One organizer of a tournament: a club or a federation, see [Organization]. */
data class TournamentOrganizer(
    val id: String,
    val name: String,
    val type: OrganizationType = OrganizationType.CLUB,
    val country: String = "",
    val primaryColor: String = "#02B16B",
    val logoUrl: String? = null,
)

const val DEFAULT_MATCH_DURATION_MINUTES = 120

/** The default duration of a match that finishes the hand in play. The extra time comes after it. */
const val DEFAULT_MATCH_DURATION_FINISH_HAND_MINUTES = 115

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
    /** True for an editor whose only tournament is this one: removing it makes the account a player. */
    val onlyTournament: Boolean = false,
)

/**
 * A registration of the signed-in player in a tournament that waits for its draw: with a place, or on the waiting
 * list ([waiting]).
 */
data class MyRegistration(
    val tournamentId: String,
    val waiting: Boolean,
)

/** The tournaments of the signed-in player: the slots after the draws and the registrations before them. */
data class MyTournaments(
    val slots: List<MyTournamentSlot> = emptyList(),
    val registrations: List<MyRegistration> = emptyList(),
)

/** The slot and the team of the signed-in player in one tournament. */
data class MyTournamentSlot(
    val tournamentId: String,
    val playerId: Int,
    val teamId: Int,
)
