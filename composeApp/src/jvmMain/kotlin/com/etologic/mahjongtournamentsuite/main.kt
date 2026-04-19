package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val state = rememberWindowState(placement = WindowPlacement.Maximized)
    Window(
        onCloseRequest = ::exitApplication,
        title = "MahjongTournamentSuite",
        state = state,
    ) {
        DisposableEffect(Unit) {
            window.minimumSize = Dimension(900, 650)
            onDispose { }
        }
        App()
    }

    StandaloneWindows.Host()
}
