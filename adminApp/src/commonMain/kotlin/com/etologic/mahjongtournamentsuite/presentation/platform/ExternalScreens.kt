package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController

/** Opens the Timer of a tournament. Its Play starts the round on the server. */
expect fun openTimer(
    navController: NavHostController,
    tournamentId: String,
    initialRound: Int = 1,
)

expect fun openRankings(
    navController: NavHostController,
    tournamentId: String,
)
