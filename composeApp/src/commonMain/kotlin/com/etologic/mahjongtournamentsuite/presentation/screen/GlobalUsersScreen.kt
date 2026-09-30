package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun GlobalUsersScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<AdminStatus?>(null) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
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
        user.email.contains(emailFilter.trim(), ignoreCase = true) &&
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
                is AppResult.Failure -> error = status.error.toUiMessage()
            }
            when (val result = presenter.loadUsers(force)) {
                is AppResult.Success -> users = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
            }
            when (val result = presenter.loadTournaments(force)) {
                is AppResult.Success -> tournaments = result.value
                is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
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
    ) {
        scope.launch {
            saving = true
            error = null
            notice = null
            val result = if (original == null) {
                presenter.createUser(email, alias, role, assignments)
            } else {
                presenter.updateUser(
                    original.copy(email = email, alias = alias, role = role, tournamentAssignments = assignments),
                )
            }
            when (result) {
                is AppResult.Success -> {
                    users = (users.filterNot { it.uid == result.value.uid } + result.value)
                        .sortedBy { it.email.lowercase() }
                    if (original == null) {
                        notice = "Account created. Firebase sent a password-reset email to ${result.value.email}."
                    }
                    editingUser = null
                    showCreateDialog = false
                }
                is AppResult.Failure -> error = result.error.toUiMessage()
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
                    notice = if (disabled) "${user.email} is disabled." else "${user.email} is enabled."
                    pendingDisabledUser = null
                    editingUser = null
                }
                is AppResult.Failure -> error = result.error.toUiMessage()
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
        title = "App users",
        isLoading = loading || saving,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onRefresh = { refresh(force = true) },
                onNewUser = { showCreateDialog = true },
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
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
                title = "Accounts",
                subtitle = if (loading) "Loading…" else "${filteredUsers.size} of ${users.size} accounts",
            ) {
                UsersTable(
                    users = filteredUsers,
                    enabled = !loading && !saving,
                    canEditAdmins = access?.isAdmin == true,
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
            isSelf = original?.uid == access?.uid,
            canManageRoles = access?.isAdmin == true,
            canDisable = access?.canDisableUsers == true,
            saving = saving,
            onDismiss = {
                showCreateDialog = false
                editingUser = null
            },
            onSave = { email, alias, role, assignments -> saveUser(original, email, alias, role, assignments) },
            onResetPassword = { email ->
                scope.launch {
                    saving = true
                    error = null
                    notice = null
                    when (val result = presenter.requestPasswordReset(email)) {
                        is AppResult.Success -> notice = "Password-reset email sent to $email."
                        is AppResult.Failure -> error = result.error.toUiMessage()
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

@Composable
private fun UsersTable(
    users: List<ManagedUser>,
    enabled: Boolean,
    canEditAdmins: Boolean,
    rowFocusRequesters: Map<String, FocusRequester>,
    onEdit: (ManagedUser) -> Unit,
) {
    LazyColumnWithScrollbar(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth().appFocusGroup(),
    ) {
        item {
            DataTableHeaderRow {
                Text("Alias", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge)
                Text("Email", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge)
                Text("Role", modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.labelLarge)
                Text("Status", modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
            }
            DataTableDivider()
        }
        itemsIndexed(users, key = { _, user -> user.uid }) { _, user ->
            val canEdit = enabled && (canEditAdmins || user.role == GlobalUserRole.EDITOR)
            DataTableRow(
                modifier = Modifier.focusRequester(rowFocusRequesters.getValue(user.uid)),
                onClick = { if (canEdit) onEdit(user) },
                clickFocusable = canEdit,
            ) {
                Text(user.alias.ifBlank { "—" }, modifier = Modifier.weight(0.3f), maxLines = 1)
                Text(user.email, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.role.label(), modifier = Modifier.weight(0.15f))
                Text(if (user.disabled) "Disabled" else "Active", modifier = Modifier.weight(0.25f))
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
                label = { Text("Search by email") },
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
                    label = { Text("Tournament") },
                    placeholder = { Text("All tournaments") },
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
                ) {
                    DropdownMenuItem(text = { Text("All tournaments") }, onClick = { onTournamentSelected(null) })
                    tournaments.filter {
                        tournamentFilterText.isBlank() || it.name.startsWith(tournamentFilterText, ignoreCase = true)
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
    isSelf: Boolean,
    canManageRoles: Boolean,
    canDisable: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, GlobalUserRole, List<TournamentAssignment>) -> Unit,
    onResetPassword: (String) -> Unit,
    onChangeDisabled: (ManagedUser) -> Unit,
) {
    var email by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
    var confirmEmail by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var role by remember(original) { mutableStateOf(original?.role ?: GlobalUserRole.EDITOR) }
    val assignments = remember(original) {
        mutableStateMapOf<String, Boolean>().apply {
            tournaments.forEach { tournament ->
                put(tournament.id, original?.tournamentAssignments?.any { it.tournamentId == tournament.id } == true)
            }
        }
    }
    var showTournamentSelector by remember { mutableStateOf(false) }
    var restoreTournamentSelectorFocus by remember { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val tournamentSelectorFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val normalizedEmail = email.trim()
    val emailsMatch = original != null || normalizedEmail.equals(confirmEmail.trim(), ignoreCase = true)
    val valid = normalizedEmail.contains("@") && emailsMatch
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
        title = { Text(if (original == null) "Add account" else "Edit account") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    supportingText = { Text("Optional display name") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (original == null) {
                    OutlinedTextField(
                        value = confirmEmail,
                        onValueChange = { confirmEmail = it },
                        label = { Text("Confirm email") },
                        supportingText = {
                            if (confirmEmail.isNotEmpty() && !emailsMatch) Text("The email addresses do not match.")
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
                    enabled = canManageRoles && !isSelf && !saving,
                    onSelect = { role = it },
                )
                if (role == GlobalUserRole.EDITOR) {
                    Text("Tournament assignments", style = MaterialTheme.typography.titleSmall)
                    if (tournaments.isEmpty()) {
                        Text("No tournaments are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val selectedTournaments = tournaments.filter { assignments[it.id] == true }
                        val assignmentSummary = when (selectedTournaments.size) {
                            0 -> "No tournaments assigned"
                            1 -> selectedTournaments.single().name
                            else -> "${selectedTournaments.size} tournaments selected"
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
                            Text("Change")
                        }
                        if (selectedTournaments.size > 1) {
                            Text(
                                text = selectedTournaments.take(2).joinToString { it.name } +
                                    if (selectedTournaments.size > 2) " · +${selectedTournaments.size - 2} more" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text("Admins have access to every tournament.")
                }
                if (original == null) {
                    Text("Firebase will send a password-reset email after creation.")
                } else if (!isSelf) {
                    FocusedTextButton(
                        onClick = { onResetPassword(original.email) },
                        enabled = !saving,
                    ) { Text("Send password-reset email") }
                    if (canDisable) {
                        FocusedTextButton(
                            onClick = { onChangeDisabled(original) },
                            enabled = !saving,
                        ) { Text(if (original.disabled) "Enable account" else "Disable account") }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = {
                    val selectedAssignments = if (role == GlobalUserRole.ADMIN) emptyList() else {
                        tournaments.filter { assignments[it.id] == true }
                            .map { TournamentAssignment(it.id, it.name) }
                    }
                    onSave(normalizedEmail, alias.trim(), role, selectedAssignments)
                },
                enabled = valid && !saving,
                focusRequester = saveFocus,
                buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
            ) { Text("Save") }
        },
        dismissButton = {
            FocusedTextButton(
                onClick = onDismiss,
                enabled = !saving,
                focusRequester = cancelFocus,
                buttonModifier = Modifier.focusLoop(saveFocus, saveFocus),
            ) { Text("Cancel") }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GlobalRoleDropdown(
    role: GlobalUserRole,
    enabled: Boolean,
    onSelect: (GlobalUserRole) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = role.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Global role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GlobalUserRole.entries.forEach { option ->
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
            tournament.name.contains(searchText.trim(), ignoreCase = true)
    }
    val selectedCount = tournaments.count { assignments[it.id] == true }

    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    LaunchedEffect(searchText, showSelectedOnly) {
        if (filteredTournaments.isNotEmpty()) listState.scrollToItem(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        title = { Text("Assign tournaments") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search by name") },
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
                        label = { Text("Selected only") },
                    )
                    Text(
                        text = "$selectedCount selected",
                        modifier = Modifier.weight(1f).padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
                if (filteredTournaments.isEmpty()) {
                    Text(
                        text = if (showSelectedOnly && selectedCount == 0) {
                            "No tournaments are selected."
                        } else {
                            "No tournaments match the search."
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
            ) { Text("Close") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FocusedTextButton(
                    onClick = {
                        filteredTournaments.forEach { onAssignmentChange(it.id, true) }
                    },
                    enabled = enabled && filteredTournaments.isNotEmpty(),
                ) { Text("Select results") }
                FocusedTextButton(
                    onClick = {
                        tournaments.forEach { onAssignmentChange(it.id, false) }
                    },
                    enabled = enabled && selectedCount > 0,
                ) { Text("Clear all") }
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
        title = { Text("Disable account?") },
        text = { Text("${user.email} will not be able to sign in. Its profile and history will remain.") },
        confirmButton = {
            FocusedButton(
                onClick = onConfirm,
                enabled = !saving,
                focusRequester = disableFocus,
                buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
            ) { Text("Disable") }
        },
        dismissButton = {
            FocusedTextButton(
                onClick = onDismiss,
                enabled = !saving,
                focusRequester = cancelFocus,
                buttonModifier = Modifier.focusLoop(disableFocus, disableFocus),
            ) { Text("Cancel") }
        },
    )
}

private fun GlobalUserRole.label(): String = when (this) {
    GlobalUserRole.EDITOR -> "Editor"
    GlobalUserRole.ADMIN -> "Admin"
}

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
