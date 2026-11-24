package com.etologic.mahjongtournamentsuite.presentation.platform

import android.content.Intent
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.data.platform.AndroidPlatformContext

actual fun openTimer(
    navController: NavHostController,
    tournamentId: String?,
    initialRound: Int,
) {
    startStandaloneActivity(TIMER_ACTIVITY) {
        tournamentId?.let { putExtra(EXTRA_TOURNAMENT_ID, it) }
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

actual fun openTableQrWindow(tournamentName: String, link: String, logoUrl: String?, organizerName: String, organizerColor: String) {
    startStandaloneActivity(TABLE_QR_ACTIVITY) {
        putExtra(EXTRA_TOURNAMENT_NAME, tournamentName)
        putExtra(EXTRA_LINK, link)
        logoUrl?.let { putExtra(EXTRA_LOGO_URL, it) }
        putExtra(EXTRA_ORGANIZER_NAME, organizerName)
        putExtra(EXTRA_ORGANIZER_COLOR, organizerColor)
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
private const val TABLE_QR_ACTIVITY = "com.etologic.mahjongtournamentsuite.TableQrActivity"
private const val EXTRA_TOURNAMENT_NAME = "tournamentName"
private const val EXTRA_LINK = "link"
private const val EXTRA_LOGO_URL = "logoUrl"
private const val EXTRA_ORGANIZER_NAME = "organizerName"
private const val EXTRA_ORGANIZER_COLOR = "organizerColor"
private const val EXTRA_INITIAL_ROUND = "initialRound"
private const val EXTRA_TOURNAMENT_ID = "tournamentId"
