package com.etologic.mahjongtournamentsuite.presentation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object SignInRoute

@Serializable
data object TournamentsRoute

@Serializable
data object CreateTournamentRoute

@Serializable
data class TournamentRoute(
    val tournamentId: String,
    val tournamentName: String,
)

@Serializable
data class MembersRoute(
    val tournamentId: String? = null,
)

@Serializable
data class PlayersRoute(
    val tournamentId: String,
)

@Serializable
data class TablesRoute(
    val tournamentId: String,
)

@Serializable
data class TableRoute(
    val tournamentId: String,
    val roundId: Int,
    val tableId: Int,
)

@Serializable
data class PlayerTablesRoute(
    val tournamentId: String,
    val initialPlayerId: Int? = null,
)

@Serializable
data object EmaPlayersRoute

@Serializable
data object CountriesRoute

@Serializable
data object TimerRoute

@Serializable
data class RankingsRoute(
    val tournamentId: String,
    val tournamentName: String,
)
