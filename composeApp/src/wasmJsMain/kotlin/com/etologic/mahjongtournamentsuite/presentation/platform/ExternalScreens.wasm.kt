package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.RankingsRoute
import com.etologic.mahjongtournamentsuite.presentation.TimerRoute

actual fun openTimer(navController: NavHostController) {
    navController.navigate(TimerRoute)
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
) {
    navController.navigate(
        RankingsRoute(
            tournamentId = tournamentId,
            tournamentName = tournamentName,
        ),
    )
}
