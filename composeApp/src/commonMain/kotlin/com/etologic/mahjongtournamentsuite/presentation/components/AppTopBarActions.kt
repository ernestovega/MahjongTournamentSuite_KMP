package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Assessment
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
    onIdList: (() -> Unit)? = null,
    onPlayerBase: (() -> Unit)? = null,
    onAppUsers: (() -> Unit)? = null,
    onEmaReport: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onNewTournament: (() -> Unit)? = null,
    onNewPlayer: (() -> Unit)? = null,
    onNewUser: (() -> Unit)? = null,
    teamsFocusRequester: FocusRequester? = null,
    playersFocusRequester: FocusRequester? = null,
    idCardsFocusRequester: FocusRequester? = null,
    idListFocusRequester: FocusRequester? = null,
    playerBaseFocusRequester: FocusRequester? = null,
    usersFocusRequester: FocusRequester? = null,
    exportFocusRequester: FocusRequester? = null,
    refreshFocusRequester: FocusRequester? = null,
    newTournamentFocusRequester: FocusRequester? = null,
    newPlayerFocusRequester: FocusRequester? = null,
    newUserFocusRequester: FocusRequester? = null,
) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            onTeams?.let { AppTopBarButton("Teams", Icons.Default.Groups, it, teamsFocusRequester) }
            onPlayers?.let { AppTopBarButton("Players", Icons.Default.People, it, playersFocusRequester) }
            onIdCards?.let { AppTopBarButton("ID cards", Icons.Default.Badge, it, idCardsFocusRequester) }
            onIdList?.let {
                AppTopBarButton("ID list", Icons.AutoMirrored.Filled.FormatListBulleted, it, idListFocusRequester)
            }
            onAppUsers?.let { AppTopBarButton("App users", Icons.Default.ManageAccounts, it, usersFocusRequester) }
            onEmaReport?.let { AppTopBarButton("EMA report", Icons.Outlined.Assessment, it, exportFocusRequester) }
            onPlayerBase?.let { AppTopBarButton("EMA players", Icons.Default.People, it, playerBaseFocusRequester) }
            onRefresh?.let { AppTopBarButton("Refresh", Icons.Default.Refresh, it, refreshFocusRequester) }
            onNewTournament?.let { AppTopBarButton("New tournament", Icons.Outlined.AddBox, it, newTournamentFocusRequester) }
            onNewPlayer?.let { AppTopBarButton("New player", Icons.Default.PersonAdd, it, newPlayerFocusRequester) }
            onNewUser?.let { AppTopBarButton("New user", Icons.Default.PersonAdd, it, newUserFocusRequester) }
        }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppTopBarActionsPreview() {
    MtsTheme(useDarkTheme = false) {
        AppTopBarActions(
            onPlayers = {},
            onPlayerBase = {},
            onAppUsers = {},
            onRefresh = {},
            onNewTournament = {},
            onNewPlayer = {},
        )
    }
}
