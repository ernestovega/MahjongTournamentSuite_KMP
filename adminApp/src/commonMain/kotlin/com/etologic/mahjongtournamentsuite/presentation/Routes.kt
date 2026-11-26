package com.etologic.mahjongtournamentsuite.presentation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object SignInRoute

/** The "Need help?" form of the sign-in. [email] is the email that the sign-in had, to fill the form. */
@Serializable
data class SupportRoute(val email: String = "")

@Serializable
data object TournamentsRoute

@Serializable
data object PlayerBaseRoute

/** The clubs and federations that organize the tournaments. Every staff account opens it from the tournaments. */
@Serializable
data object OrganizationsRoute

@Serializable
data class TournamentRoute(
    val tournamentId: String,
)

/** [editUid] opens the edit dialog of that account at once. The players screen uses it. */
/** The registrations of a tournament before its draw. See docs/registrations.md. */
@Serializable
data class RegistrationsRoute(
    val tournamentId: String,
)

/**
 * The change of the number of players of a tournament before its first hand (docs/player-count-change.md).
 * [started] is true when the screen that opens it already knows that a table has data: it then only explains why.
 */
@Serializable
data class PlayerCountRoute(
    val tournamentId: String,
    val started: Boolean = false,
)

@Serializable
data class UsersRoute(
    val editUid: String? = null,
)

/** The standard roles and the custom ones. Only super admins open it, from the accounts screen. */
@Serializable
data object RolesRoute

@Serializable
data class PlayersRoute(
    val tournamentId: String,
)

@Serializable
data class TeamsRoute(
    val tournamentId: String,
)

/**
 * The statistics screen. [scope] is the name of a `StatisticsScope`. The other values give its context.
 * [emaId] preselects the person of the "all tournaments" scope.
 */
@Serializable
data class StatisticsRoute(
    val scope: String,
    val tournamentId: String? = null,
    val roundId: Int? = null,
    val tableId: Int? = null,
    val slotId: Int? = null,
    val emaId: String? = null,
)

/** The error log of the apps. Only administrators can open it. */
@Serializable
data object ErrorsRoute

/**
 * Who wrote what and when in the database. Only administrators can open it.
 * [tournamentId] opens it with the tournament filter on that tournament; null shows all tournaments.
 */
/**
 * Round control of a tournament, the plan B of the Timer. [round] is the round it opens at: the round in play, or the
 * next one to play.
 */
@Serializable
data class RoundControlRoute(val tournamentId: String, val round: Int = 1)

@Serializable
data class ActivityRoute(val tournamentId: String? = null)

/** The catalog of the design system. It is in no menu: administrators open it from their profile. */
@Serializable
data object DesignSystemRoute
