package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
import com.etologic.mahjongtournamentsuite.presentation.RolesRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppSuccessDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollArea
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account_created_reset_sent
import mahjongtournamentsuite.adminapp.generated.resources.account_permissions_saved
import mahjongtournamentsuite.adminapp.generated.resources.account_request_rejected
import mahjongtournamentsuite.adminapp.generated.resources.accounts
import mahjongtournamentsuite.adminapp.generated.resources.accounts_count_summary
import mahjongtournamentsuite.adminapp.generated.resources.alias
import mahjongtournamentsuite.adminapp.generated.resources.app_users
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.email
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.password_reset_sent_to
import mahjongtournamentsuite.adminapp.generated.resources.removed_and_requested
import mahjongtournamentsuite.adminapp.generated.resources.removed_from_organizations
import mahjongtournamentsuite.adminapp.generated.resources.role
import mahjongtournamentsuite.adminapp.generated.resources.send_password_reset_count
import mahjongtournamentsuite.adminapp.generated.resources.status
import mahjongtournamentsuite.adminapp.generated.resources.user_deleted
import mahjongtournamentsuite.adminapp.generated.resources.user_disabled
import mahjongtournamentsuite.adminapp.generated.resources.user_enabled
import mahjongtournamentsuite.adminapp.generated.resources.user_status_active
import mahjongtournamentsuite.adminapp.generated.resources.user_status_disabled
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedExtendedFloatingActionButton as ExtendedFloatingActionButton

@Composable
fun GlobalUsersScreen(navController: NavHostController, editUid: String? = null) {
    val presenter = koinInject<UsersPresenter>()
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<AdminStatus?>(null) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var organizations by remember { mutableStateOf<List<Organization>>(emptyList()) }
    var emaPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var notice by remember { mutableStateOf<UiText?>(null) }
    var editingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDisabledUser by remember { mutableStateOf<ManagedUser?>(null) }
    var pendingDeletedUser by remember { mutableStateOf<ManagedUser?>(null) }
    // A club admin takes an account out of its organizations, and may ask the super admins for the rest.
    var pendingRemovalUser by remember { mutableStateOf<ManagedUser?>(null) }
    // The custom role and the single permissions of an editor or a club admin.
    var permissionsUser by remember { mutableStateOf<ManagedUser?>(null) }
    // The super admins approve or reject the requests of the club admins. An approval asks first.
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
                    tournaments.firstOrNull { it.id == selectedTournamentId }?.organizerIds.orEmpty().any { it in user.adminOrganizations } ||
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
            when (val result = presenter.loadOrganizations()) {
                is AppResult.Success -> organizations = result.value
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
                presenter.createUser(edit.email, edit.alias, edit.role, edit.assignments, edit.emaId, edit.adminOrganizations)
            } else {
                presenter.updateUser(
                    original.copy(
                        email = edit.email,
                        alias = edit.alias,
                        role = edit.role,
                        tournamentAssignments = edit.assignments,
                        emaId = edit.emaId,
                        adminOrganizations = edit.adminOrganizations,
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

    fun removeFromMyOrganizations(user: ManagedUser, kind: AccountRequestKind?, reason: String) {
        scope.launch {
            saving = true
            error = null
            notice = null
            when (val result = presenter.removeFromMyOrganizations(user.uid, kind, reason)) {
                is AppResult.Success -> {
                    users = users.map { if (it.uid == user.uid) result.value else it }
                    val name = user.alias.ifBlank { user.email }
                    notice = UiText.of(if (kind == null) Res.string.removed_from_organizations else Res.string.removed_and_requested, name)
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
                    ButtonText(stringResource(Res.string.send_password_reset_count, chosenUids.size), Modifier.padding(start = 12.dp))
                }
            }
        },
        // Every editor and admin creates player accounts. A club admin also creates editors and club admins of
        // its organizations, and a super admin any account.
        actions = if (access?.has(Permission.ACCOUNTS_CREATE_PLAYER) == true) {
            {
                AppTopBarActions(
                    onRoles = if (access?.has(Permission.PERMISSIONS_MANAGE) == true) ({ navController.navigate(RolesRoute) }) else null,
                    onNewUser = { showCreateDialog = true },
                    newUserFocusRequester = addFocusRequester,
                )
            }
        } else {
            null
        },
    ) {
        // The filters, the requests and the selection bar hide when the list scrolls down and show when it scrolls up.
        HideOnScrollArea {
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
                HideOnScrollTabs {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    }
                }
                val canReset = access?.canSendPasswordResets == true
                SectionCard(
                    container = false,
                    title = stringResource(Res.string.accounts),
                    subtitle = if (loading) stringResource(Res.string.loading) else pluralStringResource(Res.plurals.accounts_count_summary, users.size, filteredUsers.size, users.size),
                ) {
                    if (canReset) {
                        HideOnScrollTabs {
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
    }

    if (showCreateDialog || editingUser != null) {
        val original = editingUser
        ManageUserDialog(
            organizations = organizations,
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
            onRemoveFromMyOrganizations = { user ->
                editingUser = null
                pendingRemovalUser = user
            },
            onEditPermissions = { user ->
                editingUser = null
                permissionsUser = user
            },
        )
    }

    permissionsUser?.let { user ->
        AccountPermissionsFlow(
            user = user,
            access = access ?: AdminStatus(uid = "", role = GlobalUserRole.PLAYER),
            presenter = presenter,
            onDismiss = { permissionsUser = null },
            onSaved = { saved ->
                users = users.map { if (it.uid == saved.uid) saved else it }
                permissionsUser = null
                notice = UiText.of(Res.string.account_permissions_saved, saved.alias.ifBlank { saved.email })
            },
        )
    }

    pendingRemovalUser?.let { user ->
        RemoveFromOrganizationsDialog(
            user = user,
            saving = saving,
            onDismiss = { pendingRemovalUser = null },
            onConfirm = { kind, reason -> removeFromMyOrganizations(user, kind, reason) },
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
