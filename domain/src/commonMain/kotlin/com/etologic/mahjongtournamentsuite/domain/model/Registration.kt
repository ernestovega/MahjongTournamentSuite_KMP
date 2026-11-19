package com.etologic.mahjongtournamentsuite.domain.model

/**
 * The registrations of a tournament, before its draw. See docs/registrations.md.
 * A tournament with registrations has no players and no draw ([Tournament.drawPending]) until the organizer closes
 * them and makes the draw with the confirmed people.
 */
enum class RegistrationPhase { OPEN, CLOSED }

data class RegistrationSettings(
    val status: RegistrationPhase = RegistrationPhase.OPEN,
    /** The number of places. Null: no limit. Who registers when they are full goes to the waiting list. */
    val maxPlayers: Int? = null,
    /** The last day to register (yyyy-MM-dd, inclusive), a local date of the venue. Null: the organizer closes them. */
    val closesOn: String? = null,
    /** The name of the public link, `/inscripcion/<slug>`. */
    val slug: String = "",
    /** Registrations with a place. */
    val confirmed: Int = 0,
    /** Registrations on the waiting list. */
    val waiting: Int = 0,
) {
    val isOpen: Boolean get() = status == RegistrationPhase.OPEN
    val isFull: Boolean get() = maxPlayers != null && confirmed >= maxPlayers
}

enum class RegistrationStatus {
    /** The person must open the link of the email. Nobody else sees it. */
    PENDING,

    /** The person has a place. */
    CONFIRMED,

    /** The person is on the waiting list. */
    WAITING,
}

enum class RegistrationSource { SELF, ADMIN, IMPORT }

/** One person of the public list: no email, no payment, no food restrictions. */
data class PublicRegistration(
    val emaId: String,
    val name: String,
    val country: String,
    val status: RegistrationStatus,
    /** 1, 2, 3... in its list: the places or the waiting list. */
    val position: Int,
    val teamId: String? = null,
)

data class RegistrationTeam(
    val id: String,
    val name: String,
    val captainEmaId: String,
    /** The EMA numbers of the members. */
    val members: List<String>,
)

data class TournamentRegistrations(
    val settings: RegistrationSettings,
    val isTeams: Boolean,
    val registrations: List<PublicRegistration>,
    val teams: List<RegistrationTeam>,
) {
    val confirmed: List<PublicRegistration> get() = registrations.filter { it.status == RegistrationStatus.CONFIRMED }
    val waiting: List<PublicRegistration> get() = registrations.filter { it.status == RegistrationStatus.WAITING }
    fun teamOf(registration: PublicRegistration): RegistrationTeam? = teams.firstOrNull { it.id == registration.teamId }
}

/** One person as the organizers see it. */
data class AdminRegistration(
    val emaId: String,
    val name: String,
    val country: String,
    val status: RegistrationStatus,
    val position: Int,
    val teamId: String? = null,
    val email: String = "",
    val paid: Boolean = false,
    /** Private: only the organizers and the person see it. */
    val foodRestrictions: String = "",
    val source: RegistrationSource = RegistrationSource.SELF,
    /** ISO time of the registration. Only the organizers see it. */
    val registeredAt: String = "",
    val hasAccount: Boolean = false,
    /** Another person of the list has the same email. One account cannot share an email. */
    val sharedEmail: Boolean = false,
)

data class AdminRegistrations(
    val settings: RegistrationSettings,
    val isTeams: Boolean,
    val drawPending: Boolean,
    val registrations: List<AdminRegistration>,
    val teams: List<RegistrationTeam>,
) {
    fun teamOf(registration: AdminRegistration): RegistrationTeam? = teams.firstOrNull { it.id == registration.teamId }
}

/** The registration of the person who uses the app: by the account, or by the link of the emails. */
data class OwnRegistration(
    val emaId: String,
    val name: String,
    val country: String,
    val status: RegistrationStatus,
    val position: Int,
    val teamId: String? = null,
    val email: String = "",
    val foodRestrictions: String = "",
    /** The token of the links of the emails. The app keeps it to manage the registration without an account. */
    val manageToken: String = "",
)

data class RegisterRequest(
    val emaId: String,
    /** Without an account, the email that gets the link to confirm. */
    val email: String,
    val foodRestrictions: String,
    /** The consent to keep the food restrictions, which can be health data. */
    val consent: Boolean,
)

data class RegisterResult(
    /** PENDING: the person must open the link of the email. */
    val status: RegistrationStatus,
    val position: Int?,
    /** The email that got the link, partly hidden, for example "er***@gmail.com". */
    val emailHint: String,
    /** Only for a signed-in person. */
    val manageToken: String?,
)

/** A person without an EMA number writes to the organization of the tournament. */
data class NoEmaNumberRequest(
    val name: String,
    val country: String,
    val email: String,
    val message: String,
)

/** What an organizer changes in one registration. Null keeps the value. */
data class RegistrationUpdate(
    val email: String? = null,
    val status: RegistrationStatus? = null,
    val paid: Boolean? = null,
    /** The name of the team. Empty takes the person out of its team. */
    val teamName: String? = null,
    val foodRestrictions: String? = null,
)

data class NewRegistration(
    val emaId: String,
    val email: String = "",
    val status: RegistrationStatus? = null,
    val paid: Boolean = false,
    val foodRestrictions: String = "",
    val teamName: String = "",
    /** False adds the person without an email, for example when the organizer adds people who registered elsewhere. */
    val notify: Boolean = true,
)

/** One answer of the old Google Form. */
data class RegistrationImportRow(
    val line: Int,
    val emaId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val country: String,
    val team: String?,
    val foodRestrictions: String,
    /** The name and the country of the EMA registry, which the app uses instead of the ones of the file. */
    val registryName: String?,
    val registryCountry: String?,
    val sharedEmail: Boolean,
    /** Codes, such as INVALID_EMA or ALREADY_REGISTERED. A row with problems is not imported. */
    val problems: List<String>,
)

data class RegistrationImport(
    val rows: List<RegistrationImportRow>,
    val imported: Int,
)

/** Who sits in each slot of the draw. */
data class DrawAssignment(val playerId: Int, val emaId: String)

/** Why the server refused an action of the registrations. The app writes the reason in the language of the user. */
enum class RegistrationErrorCode {
    ALREADY_IN_TEAM,
    ALREADY_REGISTERED,
    BAD_ASSIGNMENTS,
    BAD_DRAW,
    BAD_PLAYER_COUNT,
    CONSENT_REQUIRED,
    DRAW_MADE,
    EMA_NOT_FOUND,
    EMPTY_FILE,
    FOOD_TOO_LONG,
    INCOMPLETE_TEAMS,
    INVALID_CLOSE_DATE,
    INVALID_EMAIL,
    INVALID_MAX_PLAYERS,
    INVALID_NAME,
    INVALID_SLUG,
    INVALID_TEAM_NAME,
    LINK_EXPIRED,
    NOT_IN_TEAM,
    NO_CONTACT,
    NO_EMA_COLUMN,
    NO_EMA_ID,
    NO_REGISTRATIONS,
    NO_TEAM,
    NO_TEAMS,
    PENDING,
    REGISTRATION_CLOSED,
    REGISTRATION_NOT_FOUND,
    REGISTRATION_OPEN,
    SLUG_TAKEN,
    TEAM_FULL,
    TEAM_NAME_TAKEN,
    TOO_MANY_ROWS,
    USE_LEAVE,
}

/** The size of a team in a tournament with teams. */
const val REGISTRATION_TEAM_SIZE = 4
const val MAX_FOOD_RESTRICTIONS_LENGTH = 300
const val MAX_REGISTRATION_TEAM_NAME_LENGTH = 40
