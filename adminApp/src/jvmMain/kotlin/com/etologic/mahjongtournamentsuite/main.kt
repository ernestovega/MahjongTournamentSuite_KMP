package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    var isMainWindowOpen by remember { mutableStateOf(true) }
    val hasStandaloneWindows = StandaloneWindows.hasOpenWindows

    LaunchedEffect(isMainWindowOpen, hasStandaloneWindows) {
        if (!isMainWindowOpen && !hasStandaloneWindows) exitApplication()
    }

    val state = rememberWindowState(placement = WindowPlacement.Maximized)
    if (isMainWindowOpen) {
        Window(
            onCloseRequest = { isMainWindowOpen = false },
            title = "MahjongTournamentSuite",
            state = state,
        ) {
            DisposableEffect(Unit) {
                window.minimumSize = Dimension(900, 650)
                onDispose { }
            }
            App()
        }
    }

    StandaloneWindows.Host()
}
