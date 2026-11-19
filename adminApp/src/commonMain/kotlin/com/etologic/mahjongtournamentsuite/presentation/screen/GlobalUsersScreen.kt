package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
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
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.PhotoEditor
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.verticalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_created_reset_sent
import mahjongtournamentsuite.adminapp.generated.resources.account_disabled_without_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.account_grants_outside
import mahjongtournamentsuite.adminapp.generated.resources.account_request_rejected
import mahjongtournamentsuite.adminapp.generated.resources.accounts
import mahjongtournamentsuite.adminapp.generated.resources.accounts_count_summary
import mahjongtournamentsuite.adminapp.generated.resources.add_account
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
import mahjongtournamentsuite.adminapp.generated.resources.delete
import mahjongtournamentsuite.adminapp.generated.resources.delete_account
import mahjongtournamentsuite.adminapp.generated.resources.delete_account_action
import mahjongtournamentsuite.adminapp.generated.resources.delete_user_message
import mahjongtournamentsuite.adminapp.generated.resources.disable
import mahjongtournamentsuite.adminapp.generated.resources.disable_account
import mahjongtournamentsuite.adminapp.generated.resources.disable_account_action
import mahjongtournamentsuite.adminapp.generated.resources.disable_user_message
import mahjongtournamentsuite.adminapp.generated.resources.edit_account
import mahjongtournamentsuite.adminapp.generated.resources.editor_needs_tournament
import mahjongtournamentsuite.adminapp.generated.resources.ema_id
import mahjongtournamentsuite.adminapp.generated.resources.ema_needed_for_player
import mahjongtournamentsuite.adminapp.generated.resources.ema_owner_other
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_n
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden
import mahjongtournamentsuite.adminapp.generated.resources.email_hidden_info
import mahjongtournamentsuite.adminapp.generated.resources.enable_account
import mahjongtournamentsuite.adminapp.generated.resources.global_role
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_are_available
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_assigned
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_match
import mahjongtournamentsuite.adminapp.generated.resources.no_tournaments_selected
import mahjongtournamentsuite.adminapp.generated.resources.optional_display_name
import mahjongtournamentsuite.adminapp.generated.resources.password_reset_sent_to
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.photo_of
import mahjongtournamentsuite.adminapp.generated.resources.plus_more
import mahjongtournamentsuite.adminapp.generated.resources.remove_from_my_countries
import mahjongtournamentsuite.adminapp.generated.resources.removed_and_requested
import mahjongtournamentsuite.adminapp.generated.resources.removed_from_countries
import mahjongtournamentsuite.adminapp.generated.resources.role
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_and_send_password_reset
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.search_ema_players
import mahjongtournamentsuite.adminapp.generated.resources.select_results
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import mahjongtournamentsuite.adminapp.generated.resources.selected_only
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_count
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_email
import mahjongtournamentsuite.adminapp.generated.resources.status
import mahjongtournamentsuite.adminapp.generated.resources.the_email_addresses_do_not_match
import mahjongtournamentsuite.adminapp.generated.resources.tournament
import mahjongtournamentsuite.adminapp.generated.resources.tournament_assignments
import mahjongtournamentsuite.adminapp.generated.resources.tournament_assignments_other_countries
import mahjongtournamentsuite.adminapp.generated.resources.tournaments_selected
import mahjongtournamentsuite.adminapp.generated.resources.user_deleted
import mahjongtournamentsuite.adminapp.generated.resources.user_disabled
import mahjongtournamentsuite.adminapp.generated.resources.user_enabled
import mahjongtournamentsuite.adminapp.generated.resources.user_status_active
import mahjongtournamentsuite.adminapp.generated.resources.user_status_disabled
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton as ExtendedFloatingActionButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

@Composable
fun GlobalUsersScreen(navController: NavHostController, editUid: String? = null) {
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
    var pendingDeletedUser by remember { mutableStateOf<ManagedUser?>(null) }
    // A country admin takes an account out of its countries, and may ask the super admins for the rest.
    var pendingRemovalUser by remember { mutableStateOf<ManagedUser?>(null) }
    // The super admins approve or reject the requests of the country admins. An approval asks first.
    var accountRequests by remember { mutableStateOf<List<AccountRequest>>(emptyList()) }
    var approvingRequest by remember { mutableStateOf<AccountRequest?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedRoles by remember { mutableStateOf<Set<GlobalUserRole>>(emptySet()) }
    var selectedTournamentId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUid by rememberSaveable { mutableStateOf<String?>(null) }
    // Admins select accounts and send them the password reset email in one go.
    var selectedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmResetOpen by remember { mutableStateOf(false) }
    var resetResults by remember { mutableStateOf<List<PasswordResetResult>?>(null) }
    var confirmDeleteOpen by remember { mutableStateOf(false) }
    var deleteResults by remember { mutableStateOf<List<UserDeleteResult>?>(null) }
    var deleteResultUsers by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var editUidHandled by rememberSaveable { mutableStateOf(false) }
    val addFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember(users.map(ManagedUser::uid)) {
        users.associate { it.uid to FocusRequester() }
    }
    // The search looks in every field of the list: country, alias, email, roles, EMA number and status.
    val roleLabels = GlobalUserRole.entries.associateWith { it.label() }
    val activeLabel = stringResource(Res.string.user_status_active)
    val disabledLabel = stringResource(Res.string.user_status_disabled)
    val countryByEmaId = emaPlayers.associate { it.emaId to it.country }
    val filteredUsers = users.filter { user ->
        listOf(
            user.emaId?.let(countryByEmaId::get).orEmpty(),
            user.alias,
            user.email,
            (user.roles.sorted().map { roleLabels.getValue(it) }.ifEmpty { listOf(roleLabels.getValue(user.role)) }).joinToString(" + "),
            user.emaId.orEmpty(),
            if (user.disabled) disabledLabel else activeLabel,
        ).any { it.matchesSearch(searchQuery) } &&
            (selectedRoles.isEmpty() || user.roles.ifEmpty { setOf(user.role) }.any { it in selectedRoles }) &&
            (
                selectedTournamentId == null ||
                    GlobalUserRole.SUPER_ADMIN in user.roles ||
                    tournaments.firstOrNull { it.id == selectedTournamentId }?.hostCountry?.uppercase() in user.adminCountries ||
                    user.tournamentAssignments.any {
                        it.tournamentId == selectedTournamentId
                    }
                )
    }

    // The players screen opens this screen with the account to edit. The dialog opens once the accounts are loaded.
    LaunchedEffect(users, editUid) {
        if (editUid != null && !editUidHandled && users.isNotEmpty()) {
            users.firstOrNull { it.uid == editUid }?.let { editingUser = it }
            editUidHandled = true
        }
    }

    // Only the accounts that the list shows are sent. A selected account that a filter hides stays out.
    val chosenUids = filteredUsers.map { it.uid }.filter { it in selectedUids }
    // An admin cannot delete the own account, so a bulk delete leaves it out.
    val deletableUids = chosenUids.filter { it != access?.uid }

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
            if (access?.canReviewRequests == true) {
                when (val result = presenter.loadAccountRequests()) {
                    is AppResult.Success -> accountRequests = result.value
                    is AppResult.Failure -> if (error == null) error = result.error.toUiText()
                }
            }
            loading = false
        }
    }

    fun saveUser(original: ManagedUser?, edit: AccountEdit) {
        scope.launch {
            saving = true
            error = null
            notice = null
            val result = if (original == null) {
                presenter.createUser(edit.email, edit.alias, edit.role, edit.assignments, edit.emaId, edit.adminCountries)
            } else {
                presenter.updateUser(
                    original.copy(
                        email = edit.email,
                        alias = edit.alias,
                        role = edit.role,
                        tournamentAssignments = edit.assignments,
                        emaId = edit.emaId,
                        adminCountries = edit.adminCountries,
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

    fun removeFromMyCountries(user: ManagedUser, kind: AccountRequestKind?, reason: String) {
        scope.launch {
            saving = true
            error = null
            notice = null
            when (val result = presenter.removeFromMyCountries(user.uid, kind, reason)) {
                is AppResult.Success -> {
                    users = users.map { if (it.uid == user.uid) result.value else it }
                    val name = user.alias.ifBlank { user.email }
                    notice = UiText.of(if (kind == null) Res.string.removed_from_countries else Res.string.removed_and_requested, name)
                    pendingRemovalUser = null
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    fun resolveRequest(request: AccountRequest, approve: Boolean) {
        scope.launch {
            saving = true
            error = null
            notice = null
            when (val result = presenter.resolveAccountRequest(request.id, approve)) {
                is AppResult.Success -> {
                    accountRequests = accountRequests.filterNot { it.id == request.id }
                    notice = when {
                        !approve -> UiText.of(Res.string.account_request_rejected)
                        request.kind == AccountRequestKind.DELETE -> UiText.of(Res.string.user_deleted, request.targetEmail)
                        else -> UiText.of(Res.string.user_disabled, request.targetEmail)
                    }
                    approvingRequest = null
                    // The account changed or is gone, so the list loads again.
                    if (approve) {
                        when (val users2 = presenter.loadUsers(force = true)) {
                            is AppResult.Success -> users = users2.value
                            is AppResult.Failure -> Unit
                        }
                    }
                }
                is AppResult.Failure -> error = result.error.toUiText()
            }
            saving = false
        }
    }

    fun deleteUser(user: ManagedUser) {
        scope.launch {
            saving = true
            error = null
            notice = null
            when (val result = presenter.deleteUser(user.uid)) {
                is AppResult.Success -> {
                    users = users.filterNot { it.uid == user.uid }
                    selectedUids = selectedUids - user.uid
                    notice = UiText.of(Res.string.user_deleted, user.email)
                    pendingDeletedUser = null
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
    LaunchedEffect(editingUser, showCreateDialog, pendingDisabledUser, pendingDeletedUser, restoreUid, users) {
        if (editingUser == null && !showCreateDialog && pendingDisabledUser == null && pendingDeletedUser == null) {
            val uid = restoreUid
            if (uid == null) {
                addFocusRequester.requestFocus()
            } else {
                rowFocusRequesters[uid]?.requestFocus()
                restoreUid = null
            }
        }
    }

    // On a phone the send button is a floating button. It shows only while accounts are selected.
    val compact = LocalCompactLayout.current
    val showResetFab = compact && access?.canSendPasswordResets == true && chosenUids.isNotEmpty() && !saving && !loading
    AppScaffold(
        title = stringResource(Res.string.app_users),
        isLoading = loading || saving,
        showBackgroundLogo = loading && users.isEmpty(),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(visible = showResetFab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(onClick = { confirmResetOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Text(stringResource(Res.string.send_password_reset_count, chosenUids.size), Modifier.padding(start = 12.dp))
                }
            }
        },
        // Every editor and admin creates player accounts. A country admin also creates editors and country admins of
        // its countries, and a super admin any account.
        actions = if (access?.has(Permission.ACCOUNTS_CREATE_PLAYER) == true) {
            {
                AppTopBarActions(
                    onNewUser = { showCreateDialog = true },
                    newUserFocusRequester = addFocusRequester,
                )
            }
        } else {
            null
        },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            // The floating button must not cover the last account.
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = if (compact) 88.dp else 24.dp),
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
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedRoles = selectedRoles,
                onRoleToggle = { role ->
                    selectedRoles = if (role in selectedRoles) selectedRoles - role else selectedRoles + role
                },
                tournaments = tournaments,
                selectedTournamentId = selectedTournamentId,
                onTournamentSelected = { selectedTournamentId = it },
            )
            if (accountRequests.isNotEmpty()) {
                AccountRequestsCard(
                    requests = accountRequests,
                    enabled = !loading && !saving,
                    onApprove = { approvingRequest = it },
                    onReject = { resolveRequest(it, approve = false) },
                )
            }
            val canReset = access?.canSendPasswordResets == true
            SectionCard(
                title = stringResource(Res.string.accounts),
                subtitle = if (loading) stringResource(Res.string.loading) else stringResource(Res.string.accounts_count_summary, filteredUsers.size, users.size),
            ) {
                if (canReset) {
                    PasswordResetBar(
                        shownCount = filteredUsers.size,
                        selectedCount = chosenUids.size,
                        busy = saving || loading,
                        onSelectAllShown = { selectedUids = filteredUsers.map { it.uid }.toSet() },
                        onClear = { selectedUids = emptySet() },
                        onSend = { confirmResetOpen = true },
                        deleteCount = if (access?.canDeleteUsers == true) deletableUids.size else 0,
                        showDelete = access?.canDeleteUsers == true,
                        onDelete = { confirmDeleteOpen = true },
                        showSend = !compact,
                    )
                }
                UsersTable(
                    users = filteredUsers,
                    selectable = canReset,
                    selectedUids = selectedUids,
                    onToggleSelected = { uid -> selectedUids = if (uid in selectedUids) selectedUids - uid else selectedUids + uid },
                    countryByEmaId = emaPlayers.associate { it.emaId to it.country },
                    enabled = !loading && !saving,
                    isSuperAdmin = access?.isSuperAdmin == true,
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
            access = access ?: AdminStatus(uid = "", role = GlobalUserRole.PLAYER),
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
            onSave = { edit -> saveUser(original, edit) },
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
                if (user.disabled) {
                    setDisabled(user, false)
                } else {
                    editingUser = null
                    pendingDisabledUser = user
                }
            },
            onDelete = { user ->
                editingUser = null
                pendingDeletedUser = user
            },
            onRemoveFromMyCountries = { user ->
                editingUser = null
                pendingRemovalUser = user
            },
        )
    }

    pendingRemovalUser?.let { user ->
        RemoveFromCountriesDialog(
            user = user,
            saving = saving,
            onDismiss = { pendingRemovalUser = null },
            onConfirm = { kind, reason -> removeFromMyCountries(user, kind, reason) },
        )
    }

    // Approving a request disables or deletes the account at once, so it asks first with the usual dialog.
    approvingRequest?.let { request ->
        val target = users.firstOrNull { it.uid == request.targetUid } ?: ManagedUser(
            uid = request.targetUid,
            email = request.targetEmail,
            alias = "",
            role = GlobalUserRole.PLAYER,
            disabled = false,
            tournamentAssignments = emptyList(),
        )
        if (request.kind == AccountRequestKind.DELETE) {
            DeleteUserDialog(user = target, saving = saving, onDismiss = { approvingRequest = null }, onConfirm = { resolveRequest(request, approve = true) })
        } else {
            DisableUserDialog(user = target, saving = saving, onDismiss = { approvingRequest = null }, onConfirm = { resolveRequest(request, approve = true) })
        }
    }

    if (confirmResetOpen) {
        val uids = chosenUids
        PasswordResetsConfirmDialog(
            count = uids.size,
            busy = saving,
            onDismiss = { confirmResetOpen = false },
            onConfirm = {
                scope.launch {
                    saving = true
                    error = null
                    when (val result = presenter.sendPasswordResets(uids)) {
                        is AppResult.Success -> {
                            resetResults = result.value
                            selectedUids = emptySet()
                        }
                        is AppResult.Failure -> error = result.error.toUiText()
                    }
                    confirmResetOpen = false
                    saving = false
                }
            },
        )
    }
    resetResults?.let { PasswordResetsResultDialog(it, users, onDismiss = { resetResults = null }) }

    if (confirmDeleteOpen) {
        val uids = deletableUids
        UserDeletesConfirmDialog(
            count = uids.size,
            busy = saving,
            onDismiss = { confirmDeleteOpen = false },
            onConfirm = {
                scope.launch {
                    saving = true
                    error = null
                    notice = null
                    when (val result = presenter.deleteUsers(uids)) {
                        is AppResult.Success -> {
                            val deleted = result.value.filter { it.outcome == UserDeleteOutcome.DELETED }.map { it.uid }.toSet()
                            // The dialog names the accounts that stay, so it needs the list from before the delete.
                            deleteResults = result.value
                            deleteResultUsers = users
                            users = users.filterNot { it.uid in deleted }
                            selectedUids = selectedUids - deleted
                        }
                        is AppResult.Failure -> error = result.error.toUiText()
                    }
                    confirmDeleteOpen = false
                    saving = false
                }
            },
        )
    }
    deleteResults?.let { UserDeletesResultDialog(it, deleteResultUsers, onDismiss = { deleteResults = null }) }

    pendingDisabledUser?.let { user ->
        DisableUserDialog(
            user = user,
            saving = saving,
            onDismiss = { pendingDisabledUser = null },
            onConfirm = { setDisabled(user, true) },
        )
    }

    pendingDeletedUser?.let { user ->
        DeleteUserDialog(
            user = user,
            saving = saving,
            onDismiss = { pendingDeletedUser = null },
            onConfirm = { deleteUser(user) },
        )
    }
}

private val UserCountryColumnWidth = 64.dp
private val UserCheckboxColumnWidth = 48.dp

@Composable
private fun UsersTable(
    users: List<ManagedUser>,
    countryByEmaId: Map<String, String>,
    enabled: Boolean,
    isSuperAdmin: Boolean,
    selfUid: String?,
    rowFocusRequesters: Map<String, FocusRequester>,
    selectable: Boolean,
    selectedUids: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEdit: (ManagedUser) -> Unit,
) {
    val compact = LocalCompactLayout.current
    LazyColumnWithScrollbar(
        state = rememberPersistentLazyListState("users"),
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        if (!compact) {
            item {
                DataTableHeaderRow {
                    if (selectable) Spacer(Modifier.width(UserCheckboxColumnWidth))
                    Text(
                        stringResource(Res.string.photo),
                        modifier = Modifier.width(PlayerPhotoColumnWidth),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
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
        }
        itemsIndexed(users, key = { _, user -> user.uid }) { _, user ->
            // Everyone opens player accounts and the own account. A country admin opens the staff of its countries,
            // and a super admin every account.
            val canEdit = enabled && (isSuperAdmin || user.canManage || user.role == GlobalUserRole.PLAYER || user.uid == selfUid)
            val emailText = if (user.emailHidden) stringResource(Res.string.email_hidden) else user.email
            DataTableRow(
                modifier = Modifier.focusRequester(rowFocusRequesters.getValue(user.uid)),
                onClick = { if (canEdit) onEdit(user) },
                clickFocusable = canEdit,
            ) {
                if (selectable) {
                    Checkbox(
                        checked = user.uid in selectedUids,
                        onCheckedChange = { onToggleSelected(user.uid) },
                        enabled = enabled,
                        modifier = Modifier.width(UserCheckboxColumnWidth).semantics { contentDescription = user.email },
                    )
                }
                val country = user.emaId?.let(countryByEmaId::get).orEmpty()
                val roleLabel = user.roles.sorted().map { it.label() }.joinToString(" + ").ifBlank { user.role.label() }
                val statusLabel = stringResource(if (user.disabled) Res.string.user_status_disabled else Res.string.user_status_active)
                // The own photo of the account, or the photo of its EMA player.
                Box(
                    modifier = Modifier.width(PlayerPhotoColumnWidth).height(PlayerPhotoSize),
                    contentAlignment = Alignment.Center,
                ) {
                    val photoUrl = user.photoUrl
                    if (photoUrl.isNullOrBlank()) {
                        PlayerPhotoPlaceholder(PlayerPhotoSize)
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = stringResource(Res.string.photo_of, user.alias.ifBlank { user.email }),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(PlayerPhotoSize),
                        )
                    }
                }
                if (compact) {
                    // A phone shows the alias over the email, and the other columns as one small line.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            user.alias.ifBlank { emailText },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (user.alias.isNotBlank()) {
                            Text(
                                emailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (country.isNotBlank()) CountryFlag(code = country, contentDescription = stringResource(Res.string.country_n, country))
                            Text(
                                listOfNotNull(roleLabel, user.emaId?.let { "EMA $it" }, statusLabel).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.width(UserCountryColumnWidth), contentAlignment = Alignment.Center) {
                        if (country.isBlank()) {
                            Text("—")
                        } else {
                            CountryFlag(code = country, contentDescription = stringResource(Res.string.country_n, country))
                        }
                    }
                    Text(user.alias.ifBlank { "—" }, modifier = Modifier.weight(0.25f), maxLines = 1)
                    Text(
                        emailText,
                        modifier = Modifier.weight(0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (user.emailHidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                    Text(
                        user.roles.sorted().map { it.label() }.joinToString(" + ").ifBlank { user.role.label() },
                        modifier = Modifier.weight(0.2f),
                        maxLines = 1,
                    )
                    Text(user.emaId ?: "—", modifier = Modifier.weight(0.12f), maxLines = 1)
                    Text(stringResource(if (user.disabled) Res.string.user_status_disabled else Res.string.user_status_active), modifier = Modifier.weight(0.13f))
                }
            }
            DataTableDivider()
        }
    }
}

/** The order of the role filter buttons. */
internal val RoleFilterOrder = listOf(GlobalUserRole.SUPER_ADMIN, GlobalUserRole.COUNTRY_ADMIN, GlobalUserRole.EDITOR, GlobalUserRole.PLAYER)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun UserFilters(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedRoles: Set<GlobalUserRole>,
    onRoleToggle: (GlobalUserRole) -> Unit,
    tournaments: List<Tournament>,
    selectedTournamentId: String?,
    onTournamentSelected: (String?) -> Unit,
) {
    // A phone puts the search and the tournament on two lines, so both fields have room.
    val compact = LocalCompactLayout.current
    val fieldModifier = if (compact) Modifier.fillMaxWidth() else Modifier
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (compact) 1 else 2,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                label = { Text(stringResource(Res.string.search)) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) { Text("×") }
                    }
                },
                modifier = if (compact) fieldModifier else Modifier.weight(1f),
            )
            val allTournaments = stringResource(Res.string.all_tournaments)
            val options = remember(tournaments, allTournaments) {
                listOf(SelectOption<String?>(null, allTournaments)) + tournaments.map { SelectOption(it.id, it.name) }
            }
            SelectField(
                label = stringResource(Res.string.tournament),
                options = options,
                selected = selectedTournamentId,
                onSelect = onTournamentSelected,
                modifier = if (compact) fieldModifier else Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleFilterOrder.forEach { role ->
                FilterChip(
                    selected = role in selectedRoles,
                    onClick = { onRoleToggle(role) },
                    label = { Text(role.label()) },
                )
            }
        }
    }
}

/** What the account dialog saves. */
internal data class AccountEdit(
    val email: String,
    val alias: String,
    val role: GlobalUserRole,
    val assignments: List<TournamentAssignment>,
    val emaId: String?,
    val adminCountries: Set<String>,
)

/**
 * The roles that the viewer may give in the account dialog. A super admin gives every role. A country admin gives
 * player, editor and country admin, but never demotes an account below what it has in countries that are not its own.
 * An editor creates only player accounts.
 */
internal fun roleOptions(access: AdminStatus, original: ManagedUser?): List<GlobalUserRole> {
    if (access.isSuperAdmin) return listOf(GlobalUserRole.EDITOR, GlobalUserRole.COUNTRY_ADMIN, GlobalUserRole.SUPER_ADMIN, GlobalUserRole.PLAYER)
    if (!access.canManageStaff) return listOf(GlobalUserRole.PLAYER)
    val countriesOutside = original?.adminCountries.orEmpty() - access.adminCountries
    return buildList {
        if (countriesOutside.isEmpty()) add(GlobalUserRole.EDITOR)
        add(GlobalUserRole.COUNTRY_ADMIN)
        if (original?.hasGrantsOutside != true) add(GlobalUserRole.PLAYER)
    }
}

@Composable
private fun ManageUserDialog(
    original: ManagedUser?,
    tournaments: List<Tournament>,
    accounts: List<ManagedUser>,
    emaPlayers: List<Player>,
    isSelf: Boolean,
    access: AdminStatus,
    saving: Boolean,
    onDismiss: () -> Unit,
    onUploadPhoto: suspend (ManagedUser, String, ByteArray) -> AppResult<ManagedUser>,
    onRemovePhoto: suspend (ManagedUser) -> AppResult<ManagedUser>,
    onSave: (AccountEdit) -> Unit,
    onResetPassword: (String) -> Unit,
    onChangeDisabled: (ManagedUser) -> Unit,
    /** Deletes the account for good, after a confirmation. Only super admins have it. */
    onDelete: (ManagedUser) -> Unit,
    /** A country admin takes the account out of its countries, after a dialog. */
    onRemoveFromMyCountries: (ManagedUser) -> Unit,
) {
    val canEditPhoto = access.canEditUserPhotos && original != null
    val canDisable = access.canDisableUsers
    val canDelete = access.canDeleteUsers
    val emailHidden = original?.emailHidden == true
    // A new email plus a password reset takes over the account, so only a super admin changes it.
    val canChangeEmail = original == null || access.canChangeEmails
    // The EMA ID links the account to a player. It is set when the account is created; later only a super admin changes it.
    val canChangeEmaId = original == null || access.canChangeEmaIds
    val options = remember(access, original) { roleOptions(access, original) }
    // The tournaments where the viewer assigns editors: the ones of its countries, or every one for a super admin.
    val assignableTournaments = tournaments.filter { access.can(Permission.TOURNAMENT_MEMBERS, it.permissions) }
    var email by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
    var confirmEmail by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var emaId by remember(original) { mutableStateOf(original?.emaId.orEmpty()) }
    var role by remember(original) { mutableStateOf(original?.role ?: options.first()) }
    var adminCountries by remember(original) { mutableStateOf(original?.adminCountries.orEmpty()) }
    val assignments = remember(original) {
        mutableStateMapOf<String, Boolean>().apply {
            tournaments.forEach { tournament ->
                put(tournament.id, original?.tournamentAssignments?.any { it.tournamentId == tournament.id } == true)
            }
        }
    }
    // A country admin manages the tournaments of its countries without an assignment. It is an editor elsewhere only.
    val editorTournaments = when (role) {
        GlobalUserRole.EDITOR -> assignableTournaments
        GlobalUserRole.COUNTRY_ADMIN -> assignableTournaments.filter { it.hostCountry.uppercase() !in adminCountries }
        else -> emptyList()
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
    val emailOwner = accounts.firstOrNull { it.uid != original?.uid && it.email.equals(normalizedEmail, ignoreCase = true) && normalizedEmail.isNotEmpty() }
    val emaOwner = emaId.trim().takeIf { it.isNotEmpty() }
        ?.let { id -> accounts.firstOrNull { it.uid != original?.uid && it.emaId == id } }
    val isPlayerRole = role == GlobalUserRole.PLAYER
    val selectedEditorTournaments = editorTournaments.filter { assignments[it.id] == true }
    // An editor needs a tournament. Tournaments that the viewer does not see count too, and they stay as they are.
    val editorWithoutTournament = role == GlobalUserRole.EDITOR && selectedEditorTournaments.isEmpty() && original?.hasGrantsOutside != true
    val countryAdminWithoutCountry = role == GlobalUserRole.COUNTRY_ADMIN && adminCountries.isEmpty()
    val valid = (emailHidden || normalizedEmail.contains("@")) &&
        emailsMatch &&
        emailOwner == null &&
        emaOwner == null &&
        (!isPlayerRole || emaId.isNotBlank()) &&
        !editorWithoutTournament &&
        !countryAdminWithoutCountry
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
            tournaments = editorTournaments,
            assignments = assignments,
            enabled = !saving,
            onAssignmentChange = { tournamentId, assigned -> assignments[tournamentId] = assigned },
            onDismiss = { showTournamentSelector = false },
        )
        return
    }

    TrackedDialog("AddAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(if (original == null) Res.string.add_account else Res.string.edit_account)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScrollWithIndicators(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Only super admins set the photo of another account. The others do not see this part.
                    photoUser?.takeIf { canEditPhoto }?.let { target ->
                        PhotoEditor(
                            photoUrl = target.photoUrl,
                            hasOwnPhoto = target.hasOwnPhoto,
                            name = target.alias.ifBlank { target.email },
                            onUpload = { contentType, bytes ->
                                when (val result = onUploadPhoto(target, contentType, bytes)) {
                                    is AppResult.Success -> {
                                        photoUser = result.value
                                        null
                                    }
                                    is AppResult.Failure -> result.error.toUiText()
                                }
                            },
                            onRemove = {
                                when (val result = onRemovePhoto(target)) {
                                    is AppResult.Success -> {
                                        photoUser = result.value
                                        null
                                    }
                                    is AppResult.Failure -> result.error.toUiText()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = if (emailHidden) stringResource(Res.string.email_hidden) else email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(Res.string.email)) },
                        supportingText = {
                            when {
                                emailOwner != null -> Text(stringResource(Res.string.another_account_already_uses_this_email))
                                emailHidden -> Text(stringResource(Res.string.email_hidden_info))
                            }
                        },
                        isError = emailOwner != null,
                        singleLine = true,
                        enabled = !saving && canChangeEmail && !emailHidden,
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
                    // A new account gets its EMA ID here. Later only a super admin changes it, so the others see it only.
                    if (canChangeEmaId || emaId.isNotBlank()) {
                        val emaPlayer = emaPlayers.firstOrNull { it.emaId == emaId.trim() }
                        OutlinedTextField(
                            value = emaId,
                            onValueChange = { emaId = it.filter(Char::isLetterOrDigit) },
                            label = { Text(stringResource(Res.string.ema_id)) },
                            supportingText = {
                                val message = when {
                                    emaOwner != null -> stringResource(Res.string.ema_owner_other, emaOwner.email)
                                    emaPlayer != null -> stringResource(Res.string.ema_player_n, emaPlayer.displayName)
                                    isPlayerRole && emaId.isBlank() -> stringResource(Res.string.ema_needed_for_player)
                                    else -> null
                                }
                                if (message != null) Text(message)
                            },
                            isError = emaOwner != null,
                            trailingIcon = if (canChangeEmaId) {
                                {
                                    IconButton(onClick = { showEmaPicker = true }, enabled = !saving) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_ema_players))
                                    }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            enabled = !saving && canChangeEmaId,
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
                        // The own role never changes here. A country admin changes the staff of its countries only.
                        options = if (role in options) options else listOf(role) + options,
                        enabled = options.size > 1 && !isSelf && !saving && (original == null || original.canManage || access.isSuperAdmin),
                        onSelect = { role = it },
                    )
                    if (original?.disabledWithoutRole == true) {
                        Text(
                            stringResource(Res.string.account_disabled_without_tournaments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (original?.hasGrantsOutside == true && !isSelf) {
                        Text(
                            stringResource(Res.string.account_grants_outside),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (role == GlobalUserRole.COUNTRY_ADMIN) {
                        AdminCountriesField(
                            selected = adminCountries,
                            editable = if (access.isSuperAdmin) null else access.adminCountries,
                            enabled = !saving && !isSelf,
                            onChange = { adminCountries = it },
                        )
                    }
                    if (role == GlobalUserRole.EDITOR || (role == GlobalUserRole.COUNTRY_ADMIN && editorTournaments.isNotEmpty())) {
                        Text(
                            stringResource(
                                if (role == GlobalUserRole.COUNTRY_ADMIN) Res.string.tournament_assignments_other_countries else Res.string.tournament_assignments,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (editorTournaments.isEmpty()) {
                            Text(stringResource(Res.string.no_tournaments_are_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val assignmentSummary = when (selectedEditorTournaments.size) {
                                0 -> stringResource(Res.string.no_tournaments_assigned)
                                1 -> selectedEditorTournaments.single().name
                                else -> stringResource(Res.string.tournaments_selected, selectedEditorTournaments.size)
                            }
                            FocusedOutlinedButton(
                                onClick = {
                                    restoreTournamentSelectorFocus = true
                                    showTournamentSelector = true
                                },
                                enabled = !saving && !isSelf,
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
                            if (selectedEditorTournaments.size > 1) {
                                Text(
                                    text = selectedEditorTournaments.take(2).joinToString { it.name } +
                                        if (selectedEditorTournaments.size > 2) " · " + stringResource(Res.string.plus_more, selectedEditorTournaments.size - 2) else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (editorWithoutTournament) {
                            Text(
                                stringResource(Res.string.editor_needs_tournament),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                // Save on top. The account actions under it, outlined, the destructive ones in red. Cancel last.
                MtsDialogButtons {
                    FocusedButton(
                        onClick = {
                            val selectedAssignments = selectedEditorTournaments
                                .map { TournamentAssignment(it.id, it.name, it.hostCountry.uppercase()) }
                            onSave(
                                AccountEdit(
                                    // A hidden email goes as empty: the account keeps it.
                                    email = if (emailHidden) "" else normalizedEmail,
                                    alias = alias.trim(),
                                    role = role,
                                    assignments = selectedAssignments,
                                    emaId = emaId.trim().ifBlank { null },
                                    adminCountries = if (role == GlobalUserRole.COUNTRY_ADMIN) adminCountries else emptySet(),
                                ),
                            )
                        },
                        enabled = valid && !saving,
                        focusRequester = saveFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    ) { Text(stringResource(if (original == null) Res.string.save_and_send_password_reset else Res.string.save)) }
                    if (original != null && !isSelf && access.canSendPasswordResets && !emailHidden) {
                        FocusedOutlinedButton(
                            onClick = { onResetPassword(original.email) },
                            enabled = !saving,
                        ) { Text(stringResource(Res.string.send_password_reset_email)) }
                    }
                    // A country admin takes the account out of its countries. A super admin disables or deletes it.
                    if (original != null && !isSelf && access.canRequestRemoval && GlobalUserRole.SUPER_ADMIN !in original.roles) {
                        FocusedOutlinedButton(
                            onClick = { onRemoveFromMyCountries(original) },
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(Res.string.remove_from_my_countries)) }
                    }
                    if (original != null && !isSelf && canDisable) {
                        // Enabling an account again is not destructive: only Disable is red.
                        FocusedOutlinedButton(
                            onClick = { onChangeDisabled(original) },
                            enabled = !saving,
                            colors = if (original.disabled) ButtonDefaults.outlinedButtonColors() else MtsButtons.destructiveOutlinedColors(),
                            border = if (original.disabled) ButtonDefaults.outlinedButtonBorder(!saving) else MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(if (original.disabled) Res.string.enable_account else Res.string.disable_account_action)) }
                    }
                    if (original != null && !isSelf && canDelete) {
                        FocusedOutlinedButton(
                            onClick = { onDelete(original) },
                            enabled = !saving,
                            colors = MtsButtons.destructiveOutlinedColors(),
                            border = MtsButtons.destructiveBorder(!saving),
                        ) { Text(stringResource(Res.string.delete_account_action)) }
                    }
                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(saveFocus, saveFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
    if (showEmaPicker) {
        EmaPlayerPickerDialog(
            players = emaPlayers,
            owners = accounts.filter { it.uid != original?.uid && it.emaId != null }.associate { it.emaId!! to it.email },
            selectedEmaId = emaId.trim().ifBlank { null },
            onSelect = {
                emaId = it.emaId
                showEmaPicker = false
            },
            onDismiss = { showEmaPicker = false },
        )
    }
}

@Composable
private fun GlobalRoleDropdown(
    role: GlobalUserRole,
    options: List<GlobalUserRole>,
    enabled: Boolean,
    onSelect: (GlobalUserRole) -> Unit,
) {
    SelectField(
        label = stringResource(Res.string.global_role),
        options = options.map { SelectOption(it, it.label()) },
        selected = role,
        onSelect = onSelect,
        enabled = enabled,
    )
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

    TrackedDialog("AssignTournaments") {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.assign_tournaments)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text(stringResource(Res.string.search)) },
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
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onDismiss,
                        enabled = enabled,
                        focusRequester = closeFocus,
                        buttonModifier = Modifier.focusProperties { next = searchFocus },
                    ) { Text(stringResource(Res.string.close)) }

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
                }
            },
        )
    }
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
private fun DeleteUserDialog(
    user: ManagedUser,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeleteAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.delete_account)) },
            text = { MtsDialogText(stringResource(Res.string.delete_user_message, user.email)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !saving,
                        focusRequester = deleteFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                        colors = MtsButtons.destructiveColors(),
                    ) { Text(stringResource(Res.string.delete)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
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
    TrackedDialog("DisableAccount") {
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            modifier = Modifier.appFocusGroup(),
            title = { MtsDialogTitle(stringResource(Res.string.disable_account)) },
            text = { MtsDialogText(stringResource(Res.string.disable_user_message, user.email)) },
            confirmButton = {
                MtsDialogButtons {
                    FocusedButton(
                        onClick = onConfirm,
                        enabled = !saving,
                        focusRequester = disableFocus,
                        buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    ) { Text(stringResource(Res.string.disable)) }

                    FocusedTextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        focusRequester = cancelFocus,
                        buttonModifier = Modifier.focusLoop(disableFocus, disableFocus),
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }
}
