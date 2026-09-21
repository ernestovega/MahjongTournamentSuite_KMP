package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun GlobalUsersScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    val scope = rememberCoroutineScope()
    var adminStatus by remember { mutableStateOf<AdminStatus?>(null) }
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDisabledUser by remember { mutableStateOf<ManagedUser?>(null) }
    var saving by remember { mutableStateOf(false) }
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
            (selectedTournamentId == null || user.tournamentAssignments.any {
                it.tournamentId == selectedTournamentId
            })
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            when (val status = presenter.loadAdminStatus()) {
                is AppResult.Success -> {
                    adminStatus = status.value
                    if (status.value.isSuperadmin) {
                        when (val result = presenter.loadUsers()) {
                            is AppResult.Success -> users = result.value
                            is AppResult.Failure -> error = result.error.toUiMessage()
                        }
                        when (val result = presenter.loadTournaments()) {
                            is AppResult.Success -> tournaments = result.value
                            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
                        }
                    } else {
                        error = "Only superadmins can open the Users screen."
                    }
                }
                is AppResult.Failure -> error = status.error.toUiMessage()
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
                        notice = "User created. Firebase sent a password-reset email to ${result.value.email}."
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
    LaunchedEffect(loading, adminStatus) {
        if (!loading && adminStatus?.isSuperadmin == true && restoreUid == null) addFocusRequester.requestFocus()
    }
    LaunchedEffect(editingUser, showCreateDialog, pendingDisabledUser, restoreUid, users) {
        if (editingUser == null && !showCreateDialog && pendingDisabledUser == null) {
            val uid = restoreUid
            if (uid == null) {
                addFocusRequester.requestFocus()
            } else {
                rowFocusRequesters[uid]?.requestFocus()
                restoreUid = null
            }
        }
    }

    AppScaffold(
        title = "Users",
        isLoading = loading || saving,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onRefresh = ::refresh,
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
            error?.let { AppErrorMessage(it) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (adminStatus?.isSuperadmin == true) {
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
                    selectedTournamentId = selectedTournamentId,
                    onTournamentSelected = { tournament ->
                        selectedTournamentId = tournament?.id
                        tournamentFilterText = tournament?.name.orEmpty()
                        tournamentFilterExpanded = false
                    },
                )
                SectionCard(
                    title = "Users",
                    subtitle = if (loading) "Loading…" else "${filteredUsers.size} of ${users.size} users",
                ) {
                    UsersTable(
                        users = filteredUsers,
                        enabled = !loading && !saving,
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
            original = original,
            tournaments = tournaments,
            isSelf = original?.uid == adminStatus?.uid,
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
                if (user.disabled) {
                    setDisabled(user, false)
                } else {
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
            DataTableRow(
                modifier = Modifier.focusRequester(rowFocusRequesters.getValue(user.uid)),
                onClick = { if (enabled) onEdit(user) },
                clickFocusable = enabled,
            ) {
                Text(user.alias.ifBlank { "—" }, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    selectedTournamentId: String?,
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
                            val typedCharacter = event.key.typeaheadCharacter()
                            when {
                                typedCharacter != null -> {
                                    onTournamentFilterTextChange(tournamentFilterText + typedCharacter)
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
                    DropdownMenuItem(
                        text = { Text("All tournaments") },
                        onClick = { onTournamentSelected(null) },
                    )
                    tournaments.filter {
                        tournamentFilterText.isBlank() || it.name.startsWith(tournamentFilterText, ignoreCase = true)
                    }.forEach { tournament ->
                        DropdownMenuItem(
                            text = { Text(tournament.name) },
                            onClick = { onTournamentSelected(tournament) },
                        )
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
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, GlobalUserRole, List<TournamentAssignment>) -> Unit,
    onResetPassword: (String) -> Unit,
    onChangeDisabled: (ManagedUser) -> Unit,
) {
    var email by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var alias by remember(original) { mutableStateOf(original?.alias.orEmpty()) }
    var confirmEmail by remember(original) { mutableStateOf(original?.email.orEmpty()) }
    var role by remember(original) { mutableStateOf(original?.role ?: GlobalUserRole.REGULAR) }
    val assignmentRoles = remember(original) {
        mutableStateMapOf<String, TournamentRole?>().apply {
            original?.tournamentAssignments?.forEach { put(it.tournamentId, it.role) }
        }
    }
    val emailFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val normalizedEmail = email.trim()
    val emailsMatch = original != null || normalizedEmail.equals(confirmEmail.trim(), ignoreCase = true)
    val valid = original != null || (normalizedEmail.contains("@") && emailsMatch)
    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.appFocusGroup(),
        title = { Text(if (original == null) "Add user" else "Edit user") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { if (original == null) email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = original == null && !saving,
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
                GlobalRoleDropdown(role = role, enabled = !saving && !isSelf, onSelect = { role = it })
                if (role != GlobalUserRole.SUPERADMIN) {
                    Text("Tournament access", style = MaterialTheme.typography.titleSmall)
                    if (tournaments.isEmpty()) {
                        Text("No tournaments are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        tournaments.forEach { tournament ->
                            TournamentAccessDropdown(
                                tournament = tournament,
                                role = assignmentRoles[tournament.id],
                                enabled = !saving,
                                onSelect = { selected -> assignmentRoles[tournament.id] = selected },
                            )
                        }
                    }
                }
                if (original == null) {
                    Text("Firebase will send a password-reset email after creation.")
                } else if (!isSelf) {
                    FocusedTextButton(
                        onClick = { onResetPassword(original.email) },
                        enabled = !saving,
                    ) { Text("Send password-reset email") }
                    FocusedTextButton(
                        onClick = { onChangeDisabled(original) },
                        enabled = !saving,
                    ) { Text(if (original.disabled) "Enable user" else "Disable user") }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = {
                    val assignments = if (role == GlobalUserRole.SUPERADMIN) {
                        emptyList()
                    } else {
                        tournaments.mapNotNull { tournament ->
                            assignmentRoles[tournament.id]?.let { assignmentRole ->
                                TournamentAssignment(tournament.id, tournament.name, assignmentRole)
                            }
                        }
                    }
                    onSave(normalizedEmail, alias.trim(), role, assignments)
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
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
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
@OptIn(ExperimentalMaterial3Api::class)
private fun TournamentAccessDropdown(
    tournament: Tournament,
    role: TournamentRole?,
    enabled: Boolean,
    onSelect: (TournamentRole?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = role?.label() ?: "No access",
            onValueChange = {},
            readOnly = true,
            label = { Text(tournament.name) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            enabled = enabled,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf<TournamentRole?>(null, TournamentRole.READER, TournamentRole.EDITOR, TournamentRole.ADMIN)
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option?.label() ?: "No access") },
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
        title = { Text("Disable user?") },
        text = { Text("${user.email} will not be able to sign in. The profile and audit history will remain.") },
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
    GlobalUserRole.REGULAR -> "User"
    GlobalUserRole.SUPERADMIN -> "Superadmin"
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

private fun TournamentRole.label(): String = when (this) {
    TournamentRole.READER -> "Reader"
    TournamentRole.EDITOR -> "Editor"
    TournamentRole.ADMIN -> "Tournament admin"
}
