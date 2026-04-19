package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.etologic.mahjongtournamentsuite.presentation.screen.RankingStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.screen.TimerStandaloneScreen
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import java.util.UUID

sealed class StandaloneWindow {
    abstract val id: String

    data class Timer(
        override val id: String = UUID.randomUUID().toString(),
    ) : StandaloneWindow()

    data class Rankings(
        val tournamentId: String,
        val tournamentName: String?,
        override val id: String = UUID.randomUUID().toString(),
    ) : StandaloneWindow()
}

object StandaloneWindows {
    private val windows: SnapshotStateList<StandaloneWindow> = mutableStateListOf()

    fun openTimer() {
        windows.add(StandaloneWindow.Timer())
    }

    fun openRankings(
        tournamentId: String,
        tournamentName: String? = null,
    ) {
        windows.add(
            StandaloneWindow.Rankings(
                tournamentId = tournamentId,
                tournamentName = tournamentName,
            ),
        )
    }

    @Composable
    fun Host() {
        windows.forEach { w ->
            when (w) {
                is StandaloneWindow.Timer -> {
                    Window(
                        title = "Timer",
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        MtsTheme {
                            TimerStandaloneScreen()
                        }
                    }
                }

                is StandaloneWindow.Rankings -> {
                    Window(
                        title = "Rankings",
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        MtsTheme {
                            RankingStandaloneScreen(
                                tournamentId = w.tournamentId,
                                tournamentName = w.tournamentName,
                                onClose = { windows.removeAll { it.id == w.id } },
                            )
                        }
                    }
                }
            }
        }
    }
}
