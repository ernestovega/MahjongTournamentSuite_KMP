package com.etologic.mahjongtournamentsuite.presentation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object SignInRoute

@Serializable
data object TournamentsRoute

@Serializable
data object PlayerBaseRoute

@Serializable
data class TournamentRoute(
    val tournamentId: String,
)

@Serializable
data object UsersRoute

@Serializable
data class PlayersRoute(
    val tournamentId: String,
)

@Serializable
data class TeamsRoute(
    val tournamentId: String,
)

@Serializable
data class TablesRoute(
    val tournamentId: String,
)

@Serializable
data object TimerRoute

@Serializable
data class RankingsRoute(
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

/** Usage analytics. Only administrators can open it. */
@Serializable
data object UsageRoute
