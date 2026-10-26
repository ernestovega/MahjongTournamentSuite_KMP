package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_users
import mahjongtournamentsuite.adminapp.generated.resources.ema_report
import mahjongtournamentsuite.adminapp.generated.resources.export_players
import mahjongtournamentsuite.adminapp.generated.resources.import_players
import mahjongtournamentsuite.adminapp.generated.resources.new_player
import mahjongtournamentsuite.adminapp.generated.resources.new_tournament
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.statistics
import mahjongtournamentsuite.adminapp.generated.resources.teams
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_ema_players
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_list
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_invitations
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_new_user
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppTopBarActions(
    onTeams: (() -> Unit)? = null,
    onPlayers: (() -> Unit)? = null,
    onIdCards: (() -> Unit)? = null,
    onIdList: (() -> Unit)? = null,
    onPlayerAccounts: (() -> Unit)? = null,
    onImportPlayers: (() -> Unit)? = null,
    onExportPlayers: (() -> Unit)? = null,
    onPlayerBase: (() -> Unit)? = null,
    onAppUsers: (() -> Unit)? = null,
    onStatistics: (() -> Unit)? = null,
    onEmaReport: (() -> Unit)? = null,
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
    newTournamentFocusRequester: FocusRequester? = null,
    newPlayerFocusRequester: FocusRequester? = null,
    newUserFocusRequester: FocusRequester? = null,
) {
    TopBarButtonsLayout {
        onTeams?.let { AppTopBarButton(stringResource(Res.string.teams), Icons.Default.Groups, it, teamsFocusRequester) }
        onPlayers?.let { AppTopBarButton(stringResource(Res.string.players), Icons.Default.People, it, playersFocusRequester) }
        onIdCards?.let { AppTopBarButton(stringResource(Res.string.top_bar_id_cards), Icons.Default.Badge, it, idCardsFocusRequester) }
        onIdList?.let {
            AppTopBarButton(stringResource(Res.string.top_bar_id_list), Icons.AutoMirrored.Filled.FormatListBulleted, it, idListFocusRequester)
        }
        onPlayerAccounts?.let { AppTopBarButton(stringResource(Res.string.top_bar_invitations), Icons.Default.Email, it) }
        onImportPlayers?.let { AppTopBarButton(stringResource(Res.string.import_players), Icons.Default.UploadFile, it) }
        onExportPlayers?.let { AppTopBarButton(stringResource(Res.string.export_players), Icons.Default.FileDownload, it) }
        onAppUsers?.let { AppTopBarButton(stringResource(Res.string.app_users), Icons.Default.ManageAccounts, it, usersFocusRequester) }
        onStatistics?.let { AppTopBarButton(stringResource(Res.string.statistics), StatisticsIcon, it) }
        onEmaReport?.let { AppTopBarButton(stringResource(Res.string.ema_report), Icons.Outlined.Assessment, it, exportFocusRequester) }
        onPlayerBase?.let { AppTopBarButton(stringResource(Res.string.top_bar_ema_players), Icons.Default.People, it, playerBaseFocusRequester) }
        onNewTournament?.let { AppTopBarButton(stringResource(Res.string.new_tournament), Icons.Outlined.AddBox, it, newTournamentFocusRequester) }
        onNewPlayer?.let { AppTopBarButton(stringResource(Res.string.new_player), Icons.Default.PersonAdd, it, newPlayerFocusRequester) }
        onNewUser?.let { AppTopBarButton(stringResource(Res.string.top_bar_new_user), Icons.Default.PersonAdd, it, newUserFocusRequester) }
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
            onNewTournament = {},
            onNewPlayer = {},
        )
    }
}
