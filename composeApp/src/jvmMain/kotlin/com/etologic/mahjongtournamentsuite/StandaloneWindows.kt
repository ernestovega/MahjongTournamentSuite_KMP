package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import java.util.UUID

sealed class StandaloneWindow {
    abstract val id: String

    data class Timer(
        val initialRound: Int,
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
    val hasOpenWindows: Boolean get() = windows.isNotEmpty()

    fun openTimer(initialRound: Int) {
        windows.add(StandaloneWindow.Timer(initialRound = initialRound))
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
                        TimerApp(initialRound = w.initialRound)
                    }
                }

                is StandaloneWindow.Rankings -> {
                    Window(
                        title = "Rankings",
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        RankingApp(
                            tournamentId = w.tournamentId,
                            tournamentName = w.tournamentName,
                        )
                    }
                }
            }
        }
    }
}
