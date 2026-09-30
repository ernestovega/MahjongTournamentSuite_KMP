package com.etologic.mahjongtournamentsuite.presentation.platform

import android.content.Intent
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun openTimer(
    navController: NavHostController,
    initialRound: Int,
) {
    startStandaloneActivity(TIMER_ACTIVITY) {
        putExtra(EXTRA_INITIAL_ROUND, initialRound)
    }
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
) {
    startStandaloneActivity(RANKINGS_ACTIVITY) {
        putExtra(EXTRA_TOURNAMENT_ID, tournamentId)
    }
}

private fun startStandaloneActivity(
    className: String,
    configure: Intent.() -> Unit = {},
) {
    val context = AndroidPlatformContext.requireContext()
    context.startActivity(
        Intent().apply {
            setClassName(context.packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            configure()
        },
    )
}

private const val TIMER_ACTIVITY = "com.etologic.mahjongtournamentsuite.TimerActivity"
private const val RANKINGS_ACTIVITY = "com.etologic.mahjongtournamentsuite.RankingsActivity"
private const val EXTRA_INITIAL_ROUND = "initialRound"
private const val EXTRA_TOURNAMENT_ID = "tournamentId"
