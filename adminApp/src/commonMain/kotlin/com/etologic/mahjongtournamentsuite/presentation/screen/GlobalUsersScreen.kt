package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.rememberMenuScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppSuccessDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PhotoEditor
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.a_player_sees_the_tournaments_where
import mahjongtournamentsuite.adminapp.generated.resources.account_created_reset_sent
import mahjongtournamentsuite.adminapp.generated.resources.accounts
import mahjongtournamentsuite.adminapp.generated.resources.accounts_count_summary
import mahjongtournamentsuite.adminapp.generated.resources.action_did_not_run
import mahjongtournamentsuite.adminapp.generated.resources.add_account
import mahjongtournamentsuite.adminapp.generated.resources.admins_have_access_to_every_tournament
import mahjongtournamentsuite.adminapp.generated.resources.alias
import mahjongtournamentsuite.adminapp.generated.resources.all_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.another_account_already_uses_this_email
import mahjongtournamentsuite.adminapp.generated.resources.app_users
import mahjongtournamentsuite.adminapp.generated.resources.assign_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.change
import mahjongtournamentsuite.adminapp.generated.resources.clear_all
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.confirm_email
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.disable
import mahjongtournamentsuite.adminapp.generated.resources.disable_account
import mahjongtournamentsuite.adminapp.generated.resources.disable_account_action
import mahjongtournamentsuite.adminapp.generated.resources.disable_user_message
import mahjongtournamentsuite.adminapp.generated.resources.edit_account
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.ema_links_account
import mahjongtournamentsuite.adminapp.generated.resources.ema_needed_for_player
import mahjongtournamentsuite.adminapp.generated.resources.ema_owner_other
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_n
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.enable_account
import mahjongtournamentsuite.adminapp.generated.resources.firebase_will_send_a_password_reset
import mahjongtournamentsuite.adminapp.generated.resources.global_role
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_are_available
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_assigned
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_match
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_selected
import mahjongtournamentsuite.adminapp.generated.resources.optional_display_name
import mahjongtournamentsuite.adminapp.generated.resources.password_reset_info
import mahjongtournamentsuite.adminapp.generated.resources.password_reset_sent_to
import mahjongtournamentsuite.adminapp.generated.resources.player
import mahjongtournamentsuite.adminapp.generated.resources.plus_more
import mahjongtournamentsuite.adminapp.generated.resources.role
import mahjongtournamentsuite.adminapp.generated.resources.role_admin
import mahjongtournamentsuite.adminapp.generated.resources.role_editor
import mahjongtournamentsuite.adminapp.generated.resources.role_player
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.search_by_email
import mahjongtournamentsuite.adminapp.generated.resources.search_by_name
import mahjongtournamentsuite.adminapp.generated.resources.search_ema_players
import mahjongtournamentsuite.adminapp.generated.resources.select_results
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.selected_only
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_email
import mahjongtournamentsuite.adminapp.generated.resources.status
import mahjongtournamentsuite.adminapp.generated.resources.the_email_addresses_do_not_match
import mahjongtournamentsuite.adminapp.generated.resources.tournament
import mahjongtournamentsuite.adminapp.generated.resources.tournament_assignments
import mahjongtournamentsuite.adminapp.generated.resources.tournaments_selected
import mahjongtournamentsuite.adminapp.generated.resources.user_disabled
import mahjongtournamentsuite.adminapp.generated.resources.user_enabled
import mahjongtournamentsuite.adminapp.generated.resources.user_status_active
import mahjongtournamentsuite.adminapp.generated.resources.user_status_disabled
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun GlobalUsersScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<AdminStatus?>(null) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var emaPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var notice by remember { mutableStateOf<UiText?>(null) }
    var editingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDisabledUser by remember { mutableStateOf<ManagedUser?>(null) }
    var emailFilter by rememberSaveable { mutableStateOf("") }
    var selectedRoles by remember { mutableStateOf<Set<GlobalUserRole>>(emptySet()) }
    var tournamentFilterText by rememberSaveable { mutableStateOf("") }
    var selectedTournamentId by rememberSaveable { mutableStateOf<String?>(null) }
    var tournamentFilterExpanded by remember { mutableStateOf(false) }
    var restoreUid by rememberSaveable { mutableStateOf<String?>(null) }
    val addFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(users.map(ManagedUser::uid)) {
        users.associate { it.uid to FocusRequester() }
    }
    val filteredUsers = users.filter { user ->
        user.email.matchesSearch(emailFilter) &&
            (selectedRoles.isEmpty() || user.role in selectedRoles) &&
            (selectedTournamentId == null || user.role == GlobalUserRole.ADMIN || user.tournamentAssignments.any {
                it.tournamentId == selectedTournamentId
            })
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            when (val status = presenter.loadAdminStatus(force)) {
                is AppResult.Success -> access = status.value
                is AppResult.Failure -> error = status.error.toUiText()
            }
            when (val result = presenter.loadUsers(force)) {
                is AppResult.Success -> users = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiText()
            }
            when (val result = presenter.loadTournaments(force)) {
                is AppResult.Success -> tournaments = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiText()
            }
            when (val result = presenter.loadEmaPlayers(force)) {
                is AppResult.Success -> emaPlayers = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiText()
            }
            loading = false
        }
    }

    fun saveUser(
        original: ManagedUser?,
        email: String,
        alias: String,
        role: GlobalUserRole,
        assignments: List<TournamentAssignment>,
        emaId: String?,
    ) {
        scope.launch {
            saving = true
            error = null
            notice = null
            val result = if (original == null) {
                presenter.createUser(email, alias, role, assignments, emaId)
            } else {
                presenter.updateUser(
                    original.copy(
                        email = email,
                        alias = alias,
                        role = role,
                        tournamentAssignments = assignments,
                        emaId = emaId,
                    ),
                )
            }
            when (result) {
                is AppResult.Success -> {
                    users = (users.filterNot { it.uid == result.value.uid } + result.value)
                        .sortedBy { it.email.lowercase() }
                    if (original == null) {
                        notice = UiText.of(Res.string.account_created_reset_sent, result.value.email)
                    }
                    editingUser = null
                    showCreateDialog = false
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    fun setDisabled(user: ManagedUser, disabled: Boolean) {
        scope.launch {
            saving = true
            error = null
            notice = null
            when (val result = presenter.setUserDisabled(user.uid, disabled)) {
                is AppResult.Success -> {
                    users = users.map { if (it.uid == user.uid) result.value else it }
                    notice = UiText.of(if (disabled) Res.string.user_disabled else Res.string.user_enabled, user.email)
                    pendingDisabledUser = null
                    editingUser = null
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    LaunchedEffect(presenter) { refresh() }
    LaunchedEffect(loading, access) {
        if (!loading && access != null && restoreUid == null) addFocusRequester.requestFocus()
    }
    LaunchedEffect(editingUser, showCreateDialog, pendingDisabledUser, restoreUid, users) {
        if (editingUser == null && !showCreateDialog && pendingDisabledUser == null) {
            val uid = restoreUid
            if (uid == null) addFocusRequester.requestFocus() else {
                rowFocusRequesters[uid]?.requestFocus()
                restoreUid = null
            }
        }
    }

    AppScaffold(
        title = stringResource(Res.string.app_users),
        isLoading = loading || saving,
        showBackgroundLogo = loading && users.isEmpty(),
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                // Only admins create accounts. A new account is an editor or an admin.
                onNewUser = if (access?.isAdmin == true) ({ showCreateDialog = true }) else null,
                newUserFocusRequester = addFocusRequester,
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let {
                AppErrorDialog(
                    message = it,
                    onDismiss = { error = null },
                )
            }
            notice?.let { AppSuccessDialog(it, onDismiss = { notice = null }) }
            UserFilters(
                emailFilter = emailFilter,
                onEmailFilterChange = { emailFilter = it },
                selectedRoles = selectedRoles,
                onRoleToggle = { role ->
                    selectedRoles = if (role in selectedRoles) selectedRoles - role else selectedRoles + role
                },
                tournaments = tournaments,
                tournamentFilterText = tournamentFilterText,
                onTournamentFilterTextChange = {
                    tournamentFilterText = it
                    selectedTournamentId = null
                    tournamentFilterExpanded = true
                },
                tournamentFilterExpanded = tournamentFilterExpanded,
                onTournamentFilterExpandedChange = { tournamentFilterExpanded = it },
                onTournamentSelected = { tournament ->
                    selectedTournamentId = tournament?.id
                    tournamentFilterText = tournament?.name.orEmpty()
                    tournamentFilterExpanded = false
                },
            )
            SectionCard(
                title = stringResource(Res.string.accounts),
                subtitle = if (loading) stringResource(Res.string.loading) else stringResource(Res.string.accounts_count_summary, filteredUsers.size, users.size),
            ) {
                UsersTable(
                    users = filteredUsers,
                    countryByEmaId = emaPlayers.associate { it.emaId to it.country },
                    enabled = !loading && !saving,
                    canEditAdmins = access?.isAdmin == true,
                    selfUid = access?.uid,
                    rowFocusRequesters = rowFocusRequesters,
                    onEdit = { user ->
                        restoreUid = user.uid
                        editingUser = user
                    },
                )
            }
        }
    }

    if (showCreateDialog || editingUser != null) {
        val original = editingUser
        ManageUserDialog(
            original = original,
            tournaments = tournaments,
            accounts = users,
            emaPlayers = emaPlayers,
            isSelf = original?.uid == access?.uid,
            canManageRoles = access?.isAdmin == true,
            canDisable = access?.canDisableUsers == true,
            canEditPhoto = access?.isAdmin == true && original != null,
            saving = saving,
            onDismiss = {
                showCreateDialog = false
                editingUser = null
            },
            onUploadPhoto = { target, contentType, bytes ->
                presenter.updateUserPhoto(target.uid, contentType, bytes).also { result ->
                    if (result is AppResult.Success) users = users.map { if (it.uid == target.uid) result.value else it }
                }
            },
            onRemovePhoto = { target ->
                presenter.removeUserPhoto(target.uid).also { result ->
                    if (result is AppResult.Success) users = users.map { if (it.uid == target.uid) result.value else it }
                }
            },
            onSave = { email, alias, role, assignments, emaId -> saveUser(original, email, alias, role, assignments, emaId) },
            onResetPassword = { email ->
                scope.launch {
                    saving = true
                    error = null
                    notice = null
                    when (val result = presenter.requestPasswordReset(email)) {
                        is AppResult.Success -> notice = UiText.of(Res.string.password_reset_sent_to, email)
                        is AppResult.Failure -> error = result.error.toUiText()
                    }
                    saving = false
                }
            },
            onChangeDisabled = { user ->
                if (user.disabled) setDisabled(user, false) else {
                    editingUser = null
                    pendingDisabledUser = user
                }
            },
        )
    }

    pendingDisabledUser?.let { user ->
        DisableUserDialog(
            user = user,
            saving = saving,
            onDismiss = { pendingDisabledUser = null },
            onConfirm = { setDisabled(user, true) },
        )
    }
}

private val UserCountryColumnWidth = 64.dp

@Composable
private fun UsersTable(
    users: List<ManagedUser>,
    countryByEmaId: Map<String, String>,
    enabled: Boolean,
    canEditAdmins: Boolean,
    selfUid: String?,
    rowFocusRequesters: Map<String, FocusRequester>,
    onEdit: (ManagedUser) -> Unit,
) {
    LazyColumnWithScrollbar(
        state = rememberPersistentLazyListState("users"),
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        item {
            DataTableHeaderRow {
                Text(
                    stringResource(Res.string.country),
                    modifier = Modifier.width(UserCountryColumnWidth),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(Res.string.alias), modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.email), modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.role), modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.ema_id), modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.status), modifier = Modifier.weight(0.13f), style = MaterialTheme.typography.labelLarge)
            }
            DataTableDivider()
        }
        itemsIndexed(users, key = { _, user -> user.uid }) { _, user ->
            // An editor edits player accounts and the own account. Admins edit every account.
            val canEdit = enabled && (canEditAdmins || user.role == GlobalUserRole.PLAYER || user.uid == selfUid)
            DataTableRow(
                modifier = Modifier.focusRequester(rowFocusRequesters.getValue(user.uid)),
                onClick = { if (canEdit) onEdit(user) },
                clickFocusable = canEdit,
            ) {
                val country = user.emaId?.let(countryByEmaId::get).orEmpty()
                Box(modifier = Modifier.width(UserCountryColumnWidth), contentAlignment = Alignment.Center) {
                    if (country.isBlank()) {
                        Text("—")
                    } else {
                        CountryFlag(code = country, contentDescription = stringResource(Res.string.country_n, country))
                    }
                }
                Text(user.alias.ifBlank { "—" }, modifier = Modifier.weight(0.25f), maxLines = 1)
                Text(user.email, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    user.roles.sorted().map { it.label() }.joinToString(" + ").ifBlank { user.role.label() },
                    modifier = Modifier.weight(0.2f),
                    maxLines = 1,
                )
                Text(user.emaId ?: "—", modifier = Modifier.weight(0.12f), maxLines = 1)
                Text(stringResource(if (user.disabled) Res.string.user_status_disabled else Res.string.user_status_active), modifier = Modifier.weight(0.13f))
            }
            DataTableDivider()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UserFilters(
    emailFilter: String,
    onEmailFilterChange: (String) -> Unit,
    selectedRoles: Set<GlobalUserRole>,
    onRoleToggle: (GlobalUserRole) -> Unit,
    tournaments: List<Tournament>,
    tournamentFilterText: String,
    onTournamentFilterTextChange: (String) -> Unit,
    tournamentFilterExpanded: Boolean,
    onTournamentFilterExpandedChange: (Boolean) -> Unit,
    onTournamentSelected: (Tournament?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = emailFilter,
                onValueChange = onEmailFilterChange,
                label = { Text(stringResource(Res.string.search_by_email)) },
                singleLine = true,
                trailingIcon = {
                    if (emailFilter.isNotEmpty()) {
                        IconButton(onClick = { onEmailFilterChange("") }) { Text("×") }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuBox(
                expanded = tournamentFilterExpanded,
                onExpandedChange = onTournamentFilterExpandedChange,
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = tournamentFilterText,
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.tournament)) },
                    placeholder = { Text(stringResource(Res.string.all_tournaments)) },
                    singleLine = true,
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tournamentFilterExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val character = event.key.typeaheadCharacter()
                            when {
                                character != null -> {
                                    onTournamentFilterTextChange(tournamentFilterText + character)
                                    true
                                }
                                event.key == Key.Backspace && tournamentFilterText.isNotEmpty() -> {
                                    onTournamentFilterTextChange(tournamentFilterText.dropLast(1))
                                    true
                                }
                                else -> false
                            }
                        },
                )
                ExposedDropdownMenu(
                    expanded = tournamentFilterExpanded,
                    onDismissRequest = { onTournamentFilterExpandedChange(false) },
                    scrollState = rememberMenuScrollState(tournamentFilterExpanded, null),
                ) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.all_tournaments)) }, onClick = { onTournamentSelected(null) })
                    tournaments.filter {
                        tournamentFilterText.isBlank() || it.name.matchesSearch(tournamentFilterText)
                    }.forEach { tournament ->
                        DropdownMenuItem(text = { Text(tournament.name) }, onClick = { onTournamentSelected(tournament) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlobalUserRole.entries.forEach { role ->
                FilterChip(
                    selected = role in selectedRoles,
                    onClick = { onRoleToggle(role) },
                    label = { Text(role.label()) },
                )
            }
        }
    }
}

@Composable
private fun ManageUserDialog(
    original: ManagedUser?,
    tournaments: List<Tournament>,
    accounts: List<ManagedUser>,
    emaPlayers: List<Player>,
    isSelf: Boolean,
    canManageRoles: Boolean,
    canDisable: Boolean,
    canEditPhoto: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onUploadPhoto: suspend (ManagedUser, String, ByteArray) -> AppResult<ManagedUser>,
    onRemovePhoto: suspend (ManagedUser) -> AppResult<ManagedUser>,
    onSave: (String, String, GlobalUserRole, List<TournamentAssignment>, String?) -> Unit,
    onResetPassword: (String) -> Unit,
    onChangeDisabled: (ManagedUser) -> Unit,
) {
    var email by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
    var confirmEmail by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var emaId by remember(original) { mutableStateOf(original?.emaId.orEmpty()) }
    var role by remember(original) { mutableStateOf(original?.role ?: GlobalUserRole.EDITOR) }
    val assignments = remember(original) {
        mutableStateMapOf<String, Boolean>().apply {
            tournaments.forEach { tournament ->
                put(tournament.id, original?.tournamentAssignments?.any { it.tournamentId == tournament.id } == true)
            }
        }
    }
    // The photo saves at once, apart from the other fields. This state shows the saved photo.
    var photoUser by remember(original) { mutableStateOf(original) }
    var showTournamentSelector by remember { mutableStateOf(false) }
    var restoreTournamentSelectorFocus by remember { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val tournamentSelectorFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val normalizedEmail = email.trim()
    val emailsMatch = original != null || normalizedEmail.equals(confirmEmail.trim(), ignoreCase = true)
    // One account per email and one account per EMA ID. The server checks again when it saves.
    val emailOwner = accounts.firstOrNull { it.uid != original?.uid && it.email.equals(normalizedEmail, ignoreCase = true) }
    val emaOwner = emaId.trim().takeIf { it.isNotEmpty() }
        ?.let { id -> accounts.firstOrNull { it.uid != original?.uid && it.emaId == id } }
    val isPlayerRole = role == GlobalUserRole.PLAYER
    val valid = normalizedEmail.contains("@") && emailsMatch && emailOwner == null && emaOwner == null &&
        (!isPlayerRole || emaId.isNotBlank())
    var showEmaPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }
    LaunchedEffect(showTournamentSelector, restoreTournamentSelectorFocus) {
        if (!showTournamentSelector && restoreTournamentSelectorFocus) {
            tournamentSelectorFocus.requestFocus()
            restoreTournamentSelectorFocus = false
        }
    }

    if (showTournamentSelector) {
        TournamentAssignmentsDialog(
            tournaments = tournaments,
            assignments = assignments,
            enabled = !saving,
            onAssignmentChange = { tournamentId, assigned -> assignments[tournamentId] = assigned },
            onDismiss = { showTournamentSelector = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.appFocusGroup(),
        title = { Text(stringResource(if (original == null) Res.string.add_account else Res.string.edit_account)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Only admins set the photo of another account. Editors do not see this part.
                photoUser?.takeIf { canEditPhoto }?.let { target ->
                    PhotoEditor(
                        photoUrl = target.photoUrl,
                        hasOwnPhoto = target.hasOwnPhoto,
                        name = target.alias.ifBlank { target.email },
                        onUpload = { contentType, bytes ->
                            when (val result = onUploadPhoto(target, contentType, bytes)) {
                                is AppResult.Success -> { photoUser = result.value; null }
                                is AppResult.Failure -> result.error.toUiText()
                            }
                        },
                        onRemove = {
                            when (val result = onRemovePhoto(target)) {
                                is AppResult.Success -> { photoUser = result.value; null }
                                is AppResult.Failure -> result.error.toUiText()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(Res.string.email)) },
                    supportingText = { if (emailOwner != null) Text(stringResource(Res.string.another_account_already_uses_this_email)) },
                    isError = emailOwner != null,
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(Res.string.alias)) },
                    supportingText = { Text(stringResource(Res.string.optional_display_name)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                // An admin sets the EMA ID of any account. An editor sets it only for player accounts.
                if (canManageRoles || isPlayerRole) {
                    val emaPlayer = emaPlayers.firstOrNull { it.emaId == emaId.trim() }
                    OutlinedTextField(
                        value = emaId,
                        onValueChange = { emaId = it.filter(Char::isLetterOrDigit) },
                        label = { Text(stringResource(Res.string.ema_id)) },
                        supportingText = {
                            Text(
                                when {
                                    emaOwner != null -> stringResource(Res.string.ema_owner_other, emaOwner.email)
                                    emaPlayer != null -> stringResource(Res.string.ema_player_n, emaPlayer.displayName)
                                    isPlayerRole && emaId.isBlank() -> stringResource(Res.string.ema_needed_for_player)
                                    else -> stringResource(Res.string.ema_links_account)
                                },
                            )
                        },
                        isError = emaOwner != null,
                        trailingIcon = {
                            IconButton(onClick = { showEmaPicker = true }, enabled = !saving) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_ema_players))
                            }
                        },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (original == null) {
                    OutlinedTextField(
                        value = confirmEmail,
                        onValueChange = { confirmEmail = it },
                        label = { Text(stringResource(Res.string.confirm_email)) },
                        supportingText = {
                            if (confirmEmail.isNotEmpty() && !emailsMatch) Text(stringResource(Res.string.the_email_addresses_do_not_match))
                        },
                        isError = confirmEmail.isNotEmpty() && !emailsMatch,
                        singleLine = true,
                        enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GlobalRoleDropdown(
                    role = role,
                    // A player account may be promoted. An account with no role claim also reads as a player.
                    // An editor can only choose the role of a new account, and cannot create admins.
                    options = when {
                        original?.role == GlobalUserRole.PLAYER -> listOf(GlobalUserRole.PLAYER, GlobalUserRole.EDITOR, GlobalUserRole.ADMIN)
                        original == null && !canManageRoles -> listOf(GlobalUserRole.EDITOR, GlobalUserRole.PLAYER)
                        original == null -> listOf(GlobalUserRole.EDITOR, GlobalUserRole.ADMIN, GlobalUserRole.PLAYER)
                        else -> listOf(GlobalUserRole.EDITOR, GlobalUserRole.ADMIN)
                    },
                    enabled = (canManageRoles || original == null) && !isSelf && !saving,
                    onSelect = { role = it },
                )
                if (role == GlobalUserRole.EDITOR) {
                    Text(stringResource(Res.string.tournament_assignments), style = MaterialTheme.typography.titleSmall)
                    if (tournaments.isEmpty()) {
                        Text(stringResource(Res.string.no_tournaments_are_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val selectedTournaments = tournaments.filter { assignments[it.id] == true }
                        val assignmentSummary = when (selectedTournaments.size) {
                            0 -> stringResource(Res.string.no_tournaments_assigned)
                            1 -> selectedTournaments.single().name
                            else -> stringResource(Res.string.tournaments_selected, selectedTournaments.size)
                        }
                        FocusedOutlinedButton(
                            onClick = {
                                restoreTournamentSelectorFocus = true
                                showTournamentSelector = true
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            buttonModifier = Modifier.fillMaxWidth(),
                            focusRequester = tournamentSelectorFocus,
                        ) {
                            Text(
                                text = assignmentSummary,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(stringResource(Res.string.change))
                        }
                        if (selectedTournaments.size > 1) {
                            Text(
                                text = selectedTournaments.take(2).joinToString { it.name } +
                                    if (selectedTournaments.size > 2) " · " + stringResource(Res.string.plus_more, selectedTournaments.size - 2) else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else if (role == GlobalUserRole.ADMIN) {
                    Text(stringResource(Res.string.admins_have_access_to_every_tournament))
                } else {
                    Text(stringResource(Res.string.a_player_sees_the_tournaments_where))
                }
                if (original == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.firebase_will_send_a_password_reset), modifier = Modifier.weight(1f))
                        InfoTooltipIcon(stringResource(Res.string.password_reset_info))
                    }
                } else if (!isSelf) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FocusedTextButton(
                            onClick = { onResetPassword(original.email) },
                            enabled = !saving,
                        ) { Text(stringResource(Res.string.send_password_reset_email)) }
                        InfoTooltipIcon(stringResource(Res.string.password_reset_info))
                    }
                    if (canDisable) {
                        FocusedTextButton(
                            onClick = { onChangeDisabled(original) },
                            enabled = !saving,
                        ) { Text(stringResource(if (original.disabled) Res.string.enable_account else Res.string.disable_account_action)) }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = {
                    val selectedAssignments = if (role != GlobalUserRole.EDITOR) emptyList() else {
                        tournaments.filter { assignments[it.id] == true }
                            .map { TournamentAssignment(it.id, it.name) }
                    }
                    onSave(normalizedEmail, alias.trim(), role, selectedAssignments, emaId.trim().ifBlank { null })
                },
                enabled = valid && !saving,
                focusRequester = saveFocus,
                buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            FocusedTextButton(
                onClick = onDismiss,
                enabled = !saving,
                focusRequester = cancelFocus,
                buttonModifier = Modifier.focusLoop(saveFocus, saveFocus),
            ) { Text(stringResource(Res.string.cancel)) }
        },
    )
    if (showEmaPicker) {
        EmaPlayerPickerDialog(
            players = emaPlayers,
            owners = accounts.filter { it.uid != original?.uid && it.emaId != null }.associate { it.emaId!! to it.email },
            selectedEmaId = emaId.trim().ifBlank { null },
            onSelect = { emaId = it.emaId; showEmaPicker = false },
            onDismiss = { showEmaPicker = false },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GlobalRoleDropdown(
    role: GlobalUserRole,
    options: List<GlobalUserRole>,
    enabled: Boolean,
    onSelect: (GlobalUserRole) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = role.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.global_role)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = rememberMenuScrollState(expanded, options.indexOf(role)),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun TournamentAssignmentsDialog(
    tournaments: List<Tournament>,
    assignments: Map<String, Boolean>,
    enabled: Boolean,
    onAssignmentChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showSelectedOnly by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val filteredTournaments = tournaments.filter { tournament ->
        (!showSelectedOnly || assignments[tournament.id] == true) &&
            tournament.name.matchesSearch(searchText)
    }
    val selectedCount = tournaments.count { assignments[it.id] == true }

    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    LaunchedEffect(searchText, showSelectedOnly) {
        if (filteredTournaments.isNotEmpty()) listState.scrollToItem(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        title = { Text(stringResource(Res.string.assign_tournaments)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(Res.string.search_by_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocus)
                        .focusProperties { previous = closeFocus },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = showSelectedOnly,
                        onClick = { showSelectedOnly = !showSelectedOnly },
                        label = { Text(stringResource(Res.string.selected_only)) },
                    )
                    Text(
                        text = stringResource(Res.string.selected_count, selectedCount),
                        modifier = Modifier.weight(1f).padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
                if (filteredTournaments.isEmpty()) {
                    Text(
                        text = if (showSelectedOnly && selectedCount == 0) {
                            stringResource(Res.string.no_tournaments_selected)
                        } else {
                            stringResource(Res.string.no_tournaments_match)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(240.dp).padding(top = 16.dp),
                    )
                } else {
                    LazyColumnWithScrollbar(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    ) {
                        items(
                            count = filteredTournaments.size,
                            key = { filteredTournaments[it].id },
                        ) { index ->
                            val tournament = filteredTournaments[index]
                            TournamentSelectionRow(
                                tournament = tournament,
                                selected = assignments[tournament.id] == true,
                                enabled = enabled,
                                onToggle = {
                                    onAssignmentChange(tournament.id, assignments[tournament.id] != true)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = onDismiss,
                enabled = enabled,
                focusRequester = closeFocus,
                buttonModifier = Modifier.focusProperties { next = searchFocus },
            ) { Text(stringResource(Res.string.close)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FocusedTextButton(
                    onClick = {
                        filteredTournaments.forEach { onAssignmentChange(it.id, true) }
                    },
                    enabled = enabled && filteredTournaments.isNotEmpty(),
                ) { Text(stringResource(Res.string.select_results)) }
                FocusedTextButton(
                    onClick = {
                        tournaments.forEach { onAssignmentChange(it.id, false) }
                    },
                    enabled = enabled && selectedCount > 0,
                ) { Text(stringResource(Res.string.clear_all)) }
            }
        },
    )
}

@Composable
private fun TournamentSelectionRow(
    tournament: Tournament,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .activateOnEnter(enabled, onToggle)
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = { onToggle() },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                enabled = enabled,
            )
            Text(
                text = tournament.name,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DisableUserDialog(
    user: ManagedUser,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val disableFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.appFocusGroup(),
        title = { Text(stringResource(Res.string.disable_account)) },
        text = { Text(stringResource(Res.string.disable_user_message, user.email)) },
        confirmButton = {
            FocusedButton(
                onClick = onConfirm,
                enabled = !saving,
                focusRequester = disableFocus,
                buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
            ) { Text(stringResource(Res.string.disable)) }
        },
        dismissButton = {
            FocusedTextButton(
                onClick = onDismiss,
                enabled = !saving,
                focusRequester = cancelFocus,
                buttonModifier = Modifier.focusLoop(disableFocus, disableFocus),
            ) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun GlobalUserRole.label(): String = stringResource(
    when (this) {
        GlobalUserRole.EDITOR -> Res.string.role_editor
        GlobalUserRole.ADMIN -> Res.string.role_admin
        GlobalUserRole.PLAYER -> Res.string.role_player
    },
)

private fun Key.typeaheadCharacter(): Char? = when (this) {
    Key.A -> 'a'
    Key.B -> 'b'
    Key.C -> 'c'
    Key.D -> 'd'
    Key.E -> 'e'
    Key.F -> 'f'
    Key.G -> 'g'
    Key.H -> 'h'
    Key.I -> 'i'
    Key.J -> 'j'
    Key.K -> 'k'
    Key.L -> 'l'
    Key.M -> 'm'
    Key.N -> 'n'
    Key.O -> 'o'
    Key.P -> 'p'
    Key.Q -> 'q'
    Key.R -> 'r'
    Key.S -> 's'
    Key.T -> 't'
    Key.U -> 'u'
    Key.V -> 'v'
    Key.W -> 'w'
    Key.X -> 'x'
    Key.Y -> 'y'
    Key.Z -> 'z'
    else -> null
}
