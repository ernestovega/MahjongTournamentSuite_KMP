package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.StandaloneWindows

actual fun openTimer(
    navController: NavHostController,
    tournamentId: String?,
    initialRound: Int,
) {
    StandaloneWindows.openTimer(tournamentId, initialRound)
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
) {
    StandaloneWindows.openRankings(
        tournamentId = tournamentId,
    )
}

actual fun openTableQrWindow(tournamentName: String, link: String, logoUrl: String?) {
    StandaloneWindows.openTableQr(tournamentName, link, logoUrl)
}
