package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTopBarActions(
    onPlayers: (() -> Unit)? = null,
    onPlayerBase: (() -> Unit)? = null,
    onMembers: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onNewTournament: (() -> Unit)? = null,
    onNewPlayer: (() -> Unit)? = null,
) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            onPlayers?.let { AppTopBarButton("Players", it) }
            onPlayerBase?.let { AppTopBarButton("EMA Players", it) }
            onMembers?.let { AppTopBarButton("Members", it) }
            onRefresh?.let { AppTopBarButton("Refresh", it) }
            onNewTournament?.let { AppTopBarButton("New Tournament", it) }
            onNewPlayer?.let { AppTopBarButton("New Player", it) }
        }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarActionsPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTopBarActions(
            onPlayers = {},
            onPlayerBase = {},
            onMembers = {},
            onRefresh = {},
            onNewTournament = {},
            onNewPlayer = {},
        )
    }
}
