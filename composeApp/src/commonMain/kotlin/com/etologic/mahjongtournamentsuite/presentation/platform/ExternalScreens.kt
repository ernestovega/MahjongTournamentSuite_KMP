package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController

expect fun openTimer(
    navController: NavHostController,
    initialRound: Int = 1,
)

expect fun openRankings(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
)
