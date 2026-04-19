package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.StandaloneWindows

actual fun openTimer(navController: NavHostController) {
    StandaloneWindows.openTimer()
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
) {
    StandaloneWindows.openRankings(
        tournamentId = tournamentId,
        tournamentName = tournamentName,
    )
}
