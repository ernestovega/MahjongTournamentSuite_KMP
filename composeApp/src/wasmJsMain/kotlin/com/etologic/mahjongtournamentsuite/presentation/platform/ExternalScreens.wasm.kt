package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.navigation.NavHostController
import kotlinx.browser.window

actual fun openTimer(
    navController: NavHostController,
    initialRound: Int,
) {
    window.open("${window.location.pathname}?standalone=timer&initialRound=$initialRound", "_blank")
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
) {
    window.open(
        "${window.location.pathname}?standalone=rankings&tournamentId=$tournamentId",
        "_blank",
    )
}
