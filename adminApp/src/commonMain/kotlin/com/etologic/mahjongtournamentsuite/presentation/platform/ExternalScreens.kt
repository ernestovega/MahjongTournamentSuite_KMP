package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController

/**
 * Opens the Timer of a tournament, whose Play starts the round on the server. A null [tournamentId] opens a Timer
 * without a tournament: its clock stays on this device and it changes no data.
 */
expect fun openTimer(
    navController: NavHostController,
    tournamentId: String?,
    initialRound: Int = 1,
)

expect fun openRankings(
    navController: NavHostController,
    tournamentId: String,
)
