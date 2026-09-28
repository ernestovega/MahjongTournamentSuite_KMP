package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme

@Composable
fun AppTopBarActions(
    onTeams: (() -> Unit)? = null,
    onPlayers: (() -> Unit)? = null,
    onIdCards: (() -> Unit)? = null,
    onPlayerBase: (() -> Unit)? = null,
    onUsers: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onNewTournament: (() -> Unit)? = null,
    onNewPlayer: (() -> Unit)? = null,
    onNewUser: (() -> Unit)? = null,
    teamsFocusRequester: FocusRequester? = null,
    playersFocusRequester: FocusRequester? = null,
    idCardsFocusRequester: FocusRequester? = null,
    playerBaseFocusRequester: FocusRequester? = null,
    usersFocusRequester: FocusRequester? = null,
    refreshFocusRequester: FocusRequester? = null,
    newTournamentFocusRequester: FocusRequester? = null,
    newPlayerFocusRequester: FocusRequester? = null,
    newUserFocusRequester: FocusRequester? = null,
) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            onTeams?.let { AppTopBarButton("Teams", it, teamsFocusRequester) }
            onPlayers?.let { AppTopBarButton("Players", it, playersFocusRequester) }
            onIdCards?.let { AppTopBarButton("ID cards", it, idCardsFocusRequester) }
            onUsers?.let { AppTopBarButton("App Users", it, usersFocusRequester) }
            onPlayerBase?.let { AppTopBarButton("EMA Players", it, playerBaseFocusRequester) }
            onRefresh?.let { AppTopBarButton("Refresh", it, refreshFocusRequester) }
            onNewTournament?.let { AppTopBarButton("New Tournament", it, newTournamentFocusRequester) }
            onNewPlayer?.let { AppTopBarButton("New Player", it, newPlayerFocusRequester) }
            onNewUser?.let { AppTopBarButton("NEW USER", it, newUserFocusRequester) }
        }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarActionsPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTopBarActions(
            onPlayers = {},
            onPlayerBase = {},
            onUsers = {},
            onRefresh = {},
            onNewTournament = {},
            onNewPlayer = {},
        )
    }
}
