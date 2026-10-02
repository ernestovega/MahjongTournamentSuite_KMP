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
data object EmaPlayersRoute

@Serializable
data object CountriesRoute

@Serializable
data object TimerRoute

@Serializable
data class RankingsRoute(
    val tournamentId: String,
)
