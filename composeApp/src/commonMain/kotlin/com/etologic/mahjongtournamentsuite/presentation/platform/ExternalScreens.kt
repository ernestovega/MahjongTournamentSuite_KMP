package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController

expect fun openTimer(navController: NavHostController)

expect fun openRankings(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
)
