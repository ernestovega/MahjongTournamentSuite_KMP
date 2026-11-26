package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_icon
import mahjongtournamentsuite.adminapp.generated.resources.mahjongtournamentsuite
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The admin app alone, for development: `./gradlew :adminApp:run`. The installed desktop app is `:suite`. */
fun main() = desktopApplication { App() }

/**
 * The desktop application: the main window with [content] and the windows that the admin part opens apart (the timer,
 * the rankings, the QR of the tables). It ends when the main window and every other window are closed.
 */
fun desktopApplication(content: @Composable FrameWindowScope.() -> Unit) = application {
    var isMainWindowOpen by remember { mutableStateOf(true) }
    val hasStandaloneWindows = StandaloneWindows.hasOpenWindows

    LaunchedEffect(isMainWindowOpen, hasStandaloneWindows) {
        if (!isMainWindowOpen && !hasStandaloneWindows) exitApplication()
    }

    val state = rememberWindowState(placement = WindowPlacement.Maximized)
    if (isMainWindowOpen) {
        Window(
            onCloseRequest = { isMainWindowOpen = false },
            title = stringResource(Res.string.mahjongtournamentsuite),
            icon = painterResource(Res.drawable.app_icon),
            state = state,
        ) {
            DisposableEffect(Unit) {
                window.minimumSize = Dimension(900, 650)
                onDispose { }
            }
            content()
        }
    }

    StandaloneWindows.Host()
}
