package com.etologic.mahjongtournamentsuite.domain.model

data class TournamentPlayer(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    /** EMA number of the shared player assigned to this tournament slot, if any. */
    val assignedEmaId: String? = null,
    /** Player account linked to this slot. */
    val accountUid: String? = null,
    val invitationStatus: InvitationStatus = InvitationStatus.NONE,
    /** The email of the linked account. The email lives only in the account. Null when the slot has no account. */
    val email: String? = null,
    /** When the last invitation or registration email was sent to the player. Null when none was sent. */
    val contactedAt: String? = null,
    /** Set when a substitute plays for this person from a round on. The substitute is never recorded. */
    val substitution: PlayerSubstitution? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

enum class SubstitutionReason {
    /** The player came late. The round starts when the substitute is at the table. */
    LATE,
    /** The player is ill. */
    ILLNESS,
    /** The player is disqualified. The player gets 0 table points for the whole tournament. */
    DISQUALIFIED,
}

/**
 * A substitute plays for the player from [fromRound]. By the EMA rules the player gets no result for those rounds.
 * A disqualified player gets no result in any round, and the result still counts for the EMA ranking.
 */
data class PlayerSubstitution(
    val reason: SubstitutionReason,
    val fromRound: Int,
    /** The player leaves after the session has started. The substitute then gets 0 table points in [fromRound]. */
    val duringSession: Boolean = false,
    /**
     * The head referee excluded the player. The EMA report and the statistics leave out the player.
     * The player stays in the ranking, struck through. This protects the player. Only for [SubstitutionReason.ILLNESS].
     */
    val excludedFromEma: Boolean = false,
) {
    /** True when the result of the player in [roundId] does not count for the player. */
    fun ignoresRound(roundId: Int): Boolean = reason == SubstitutionReason.DISQUALIFIED || roundId >= fromRound

    /** True when the substitute plays in [roundId]. The person has no personal statistics from that round. */
    fun substitutePlays(roundId: Int): Boolean = roundId >= fromRound

    /** True when rankings show the player struck through: a disqualified or an excluded player. */
    val struckThrough: Boolean get() = reason == SubstitutionReason.DISQUALIFIED || excludedFromEma

    /** True when the player has no personal statistics at all. Only an excluded player. */
    val excludedFromStatistics: Boolean get() = excludedFromEma
}

/**
 * [NONE]: no account, or an account with no password that got no email yet.
 * [INVITED]: the account has no password yet and the invitation email went out.
 * [ACCEPTED]: the person chose a password.
 */
enum class InvitationStatus {
    NONE,
    INVITED,
    ACCEPTED,
}

/** Where a player stands in the contact process. Editors filter and run bulk actions with it. */
enum class PlayerContactState {
    /** The slot has no EMA id, so nobody can be contacted. */
    NO_EMA_ID,
    /** No email was sent yet and no account is linked. */
    NOT_INVITED,
    /** An account is linked, but no email was sent yet. */
    LINKED,
    /** The invitation was sent and the player did not choose a password yet. */
    INVITED,
    /** The player has an account and an email was sent, or the player accepted. */
    CONTACTED,
}

val TournamentPlayer.contactState: PlayerContactState
    get() = when {
        assignedEmaId.isNullOrBlank() -> PlayerContactState.NO_EMA_ID
        invitationStatus == InvitationStatus.INVITED -> PlayerContactState.INVITED
        accountUid != null && contactedAt != null -> PlayerContactState.CONTACTED
        accountUid != null -> PlayerContactState.LINKED
        else -> PlayerContactState.NOT_INVITED
    }

val TournamentPlayer.isAssigned: Boolean
    get() = !assignedEmaId.isNullOrBlank()

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

data class Country(
    val code: String,
    val name: String,
)

/** The place that Google Places found for a map link. A value is null when Places does not hold it. */
data class VenueLinkPlace(
    val name: String?,
    val address: String?,
    val city: String?,
    /** Two-letter country code. */
    val countryCode: String?,
    /** IANA time zone name of the place. */
    val timeZone: String? = null,
)

/** A map link as the full Google Maps link, with the place when Google Places found it. */
data class ResolvedVenueLink(
    val url: String,
    val place: VenueLinkPlace?,
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
    val hasProgress: Boolean,
    val hasValidManualTotals: Boolean = false,
    /** Server status: `empty`, `incomplete`, `partial` or `completed`. */
    val completionStatus: String = "",
    /** Scores of the best done hands of the table, from highest to lowest. See [topHandScores]. */
    val bestHandScores: List<Int> = emptyList(),
    val chickenHandCount: Int = 0,
    val version: Long = 0,
)
