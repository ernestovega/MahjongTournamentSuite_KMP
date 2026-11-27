package com.etologic.mahjongtournamentsuite.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.RankingApp
import com.etologic.mahjongtournamentsuite.TableQrApp
import com.etologic.mahjongtournamentsuite.TimerApp
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIViewController

/*
 * On Android and the desktop the timer, the rankings and the QR open in windows of their own, to project them.
 * On iOS they open as a sheet over the app, which the user closes with a swipe down. Windows of their own on an
 * iPad (UIScene) can come later. See docs/ios-release.md.
 */

actual fun openTimer(
    navController: NavHostController,
    tournamentId: String?,
    initialRound: Int,
) {
    // The screen stays on while the timer is open, like the window of the timer on the other platforms.
    presentSheet(keepScreenOn = true) { TimerApp(tournamentId = tournamentId, initialRound = initialRound) }
}

actual fun openRankings(
    navController: NavHostController,
    tournamentId: String,
) {
    presentSheet(keepScreenOn = true) { RankingApp(tournamentId) }
}

actual fun openTableQrWindow(tournamentName: String, link: String, logoUrl: String?, organizerName: String, organizerColor: String) {
    presentSheet(keepScreenOn = true) { TableQrApp(tournamentName, link, logoUrl, organizerName, organizerColor) }
}

private fun presentSheet(keepScreenOn: Boolean, content: @Composable () -> Unit) {
    val controller: UIViewController = ComposeUIViewController {
        if (keepScreenOn) {
            DisposableEffect(Unit) {
                UIApplication.sharedApplication.idleTimerDisabled = true
                onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
            }
        }
        content()
    }
    controller.modalPresentationStyle = UIModalPresentationPageSheet
    IosViewControllers.present(controller)
}
