package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_users
import mahjongtournamentsuite.adminapp.generated.resources.ema_report
import mahjongtournamentsuite.adminapp.generated.resources.export_players
import mahjongtournamentsuite.adminapp.generated.resources.import_locked_text
import mahjongtournamentsuite.adminapp.generated.resources.import_locked_title
import mahjongtournamentsuite.adminapp.generated.resources.import_players
import mahjongtournamentsuite.adminapp.generated.resources.new_player
import mahjongtournamentsuite.adminapp.generated.resources.new_tournament
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.pc_top_bar
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.organizations_title
import mahjongtournamentsuite.adminapp.generated.resources.roles_open
import mahjongtournamentsuite.adminapp.generated.resources.statistics
import mahjongtournamentsuite.adminapp.generated.resources.teams
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_ema_players
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_list
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_invitations
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_new_user
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button

@Composable
fun AppTopBarActions(
    onTeams: (() -> Unit)? = null,
    onPlayers: (() -> Unit)? = null,
    onIdCards: (() -> Unit)? = null,
    onIdList: (() -> Unit)? = null,
    onPlayerAccounts: (() -> Unit)? = null,
    onImportPlayers: (() -> Unit)? = null,
    /** True when the tournament has data. The import button then looks disabled and explains why it cannot run. */
    importLocked: Boolean = false,
    onExportPlayers: (() -> Unit)? = null,
    /** Change of the number of players before the first hand. [playerCountLocked] makes it look disabled; it then explains why. */
    onPlayerCount: (() -> Unit)? = null,
    playerCountLocked: Boolean = false,
    onPlayerBase: (() -> Unit)? = null,
    /** The clubs and federations that organize the tournaments. */
    onOrganizations: (() -> Unit)? = null,
    onAppUsers: (() -> Unit)? = null,
    onStatistics: (() -> Unit)? = null,
    onEmaReport: (() -> Unit)? = null,
    onNewTournament: (() -> Unit)? = null,
    onNewPlayer: (() -> Unit)? = null,
    onNewUser: (() -> Unit)? = null,
    /** The custom roles. Only super admins have it. */
    onRoles: (() -> Unit)? = null,
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
    var lockedInfoOpen by remember { mutableStateOf(false) }
    if (lockedInfoOpen) {
        TrackedDialog("ImportLocked") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { lockedInfoOpen = false },
                title = { MtsDialogTitle(stringResource(Res.string.import_locked_title)) },
                text = { MtsDialogText(stringResource(Res.string.import_locked_text)) },
                confirmButton = {
                    MtsDialogButtons {
                        Button(onClick = { lockedInfoOpen = false }) { ButtonText(stringResource(Res.string.ok)) }
                    }
                },
            )
        }
    }
    TopBarButtonsLayout {
        onPlayers?.let { AppTopBarButton(stringResource(Res.string.players), Icons.Default.People, it, playersFocusRequester) }
        onIdCards?.let { AppTopBarButton(stringResource(Res.string.top_bar_id_cards), Icons.Default.Badge, it, idCardsFocusRequester) }
        onIdList?.let {
            AppTopBarButton(stringResource(Res.string.top_bar_id_list), Icons.AutoMirrored.Filled.FormatListBulleted, it, idListFocusRequester)
        }
        onTeams?.let { AppTopBarButton(stringResource(Res.string.teams), Icons.Default.Groups, it, teamsFocusRequester) }
        onPlayerAccounts?.let { AppTopBarButton(stringResource(Res.string.top_bar_invitations), Icons.Default.Email, it) }
        onImportPlayers?.let {
            AppTopBarButton(
                stringResource(Res.string.import_players),
                Icons.Default.FileDownload,
                onClick = if (importLocked) ({ lockedInfoOpen = true }) else it,
                looksDisabled = importLocked,
            )
        }
        onExportPlayers?.let { AppTopBarButton(stringResource(Res.string.export_players), Icons.Default.FileUpload, it) }
        onPlayerCount?.let { AppTopBarButton(stringResource(Res.string.pc_top_bar), Icons.Default.Exposure, onClick = it, looksDisabled = playerCountLocked) }
        onAppUsers?.let { AppTopBarButton(stringResource(Res.string.app_users), Icons.Default.ManageAccounts, it, usersFocusRequester) }
        onStatistics?.let { AppTopBarButton(stringResource(Res.string.statistics), StatisticsIcon, it) }
        onEmaReport?.let { AppTopBarButton(stringResource(Res.string.ema_report), Icons.Outlined.Assessment, it, exportFocusRequester) }
        onOrganizations?.let { AppTopBarButton(stringResource(Res.string.organizations_title), Icons.Default.Business, it) }
        onPlayerBase?.let { AppTopBarButton(stringResource(Res.string.top_bar_ema_players), Icons.Default.People, it, playerBaseFocusRequester) }
        onNewTournament?.let { AppTopBarButton(stringResource(Res.string.new_tournament), Icons.Outlined.AddBox, it, newTournamentFocusRequester) }
        onNewPlayer?.let { AppTopBarButton(stringResource(Res.string.new_player), Icons.Default.PersonAdd, it, newPlayerFocusRequester) }
        onRoles?.let { AppTopBarButton(stringResource(Res.string.roles_open), Icons.Default.AdminPanelSettings, it) }
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
