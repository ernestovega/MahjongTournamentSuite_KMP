package com.etologic.mahjongtournamentsuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import java.util.UUID
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_icon
import mahjongtournamentsuite.adminapp.generated.resources.rankings
import mahjongtournamentsuite.adminapp.generated.resources.timer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

sealed class StandaloneWindow {
    abstract val id: String

    data class Timer(
        val tournamentId: String?,
        val initialRound: Int,
        override val id: String = UUID.randomUUID().toString(),
    ) : StandaloneWindow()

    data class TableQr(
        val tournamentName: String,
        val link: String,
        val logoUrl: String?,
        val organizerName: String,
        val organizerColor: String,
        override val id: String = UUID.randomUUID().toString(),
    ) : StandaloneWindow()

    data class Rankings(
        val tournamentId: String,
        override val id: String = UUID.randomUUID().toString(),
    ) : StandaloneWindow()
}

object StandaloneWindows {
    private val windows: SnapshotStateList<StandaloneWindow> = mutableStateListOf()
    val hasOpenWindows: Boolean get() = windows.isNotEmpty()

    fun openTimer(tournamentId: String?, initialRound: Int) {
        windows.add(StandaloneWindow.Timer(tournamentId = tournamentId, initialRound = initialRound))
    }

    fun openTableQr(tournamentName: String, link: String, logoUrl: String?, organizerName: String, organizerColor: String) {
        windows.add(StandaloneWindow.TableQr(tournamentName, link, logoUrl, organizerName, organizerColor))
    }

    fun openRankings(
        tournamentId: String,
    ) {
        windows.add(
            StandaloneWindow.Rankings(
                tournamentId = tournamentId,
            ),
        )
    }

    @Composable
    fun Host() {
        windows.forEach { w ->
            when (w) {
                is StandaloneWindow.Timer -> {
                    Window(
                        title = stringResource(Res.string.timer),
                        icon = painterResource(Res.drawable.app_icon),
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        TimerApp(tournamentId = w.tournamentId, initialRound = w.initialRound)
                    }
                }

                is StandaloneWindow.TableQr -> {
                    Window(
                        title = w.tournamentName,
                        icon = painterResource(Res.drawable.app_icon),
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        TableQrApp(w.tournamentName, w.link, w.logoUrl, w.organizerName, w.organizerColor)
                    }
                }

                is StandaloneWindow.Rankings -> {
                    Window(
                        title = stringResource(Res.string.rankings),
                        icon = painterResource(Res.drawable.app_icon),
                        onCloseRequest = { windows.removeAll { it.id == w.id } },
                        state = rememberWindowState(placement = WindowPlacement.Maximized),
                    ) {
                        RankingApp(w.tournamentId)
                    }
                }
            }
        }
    }
}
