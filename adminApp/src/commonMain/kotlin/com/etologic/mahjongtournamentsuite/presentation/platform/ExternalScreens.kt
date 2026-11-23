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

/** Opens the QR code of a tournament in a window of its own, as big as it fits, to project it (docs/table-qr.md). */
expect fun openTableQrWindow(tournamentName: String, link: String)
