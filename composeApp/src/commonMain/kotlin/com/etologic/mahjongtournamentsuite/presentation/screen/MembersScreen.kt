package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRole
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.MembersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TournamentMembersScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<MembersPresenter>()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var adminStatus by remember { mutableStateOf<AdminStatus?>(null) }
    var members by remember { mutableStateOf<List<TournamentMember>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var lookupIdentifier by remember { mutableStateOf("") }
    var lookedUpUser by remember { mutableStateOf<UserProfile?>(null) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            lookedUpUser = null

            when (val statusResult = presenter.loadAdminStatus()) {
                is AppResult.Success -> adminStatus = statusResult.value
                is AppResult.Failure -> errorMessage = statusResult.error.toUiMessage()
            }

            when (val membersResult = presenter.loadMembers(tournamentId)) {
                is AppResult.Success -> members = membersResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage = membersResult.error.toUiMessage()
            }

            isLoading = false
        }
    }

    fun upsert(uid: String, role: TournamentRole) {
        coroutineScope.launch {
            errorMessage = null
            when (val result = presenter.upsertMember(
                tournamentId = tournamentId,
                uid = uid,
                role = role,
            )) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
        }
    }

    fun remove(uid: String) {
        coroutineScope.launch {
            errorMessage = null
            when (val result = presenter.removeMember(
                tournamentId = tournamentId,
                uid = uid,
            )) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
        }
    }

    LaunchedEffect(presenter, tournamentId) {
        refresh()
    }

    AppScaffold(
        title = "Members",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions { refresh() } },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            errorMessage?.let { message ->
                AppErrorMessage(message = message)
            }

            if (adminStatus?.isSuperadmin == true) {
                SectionCard(
                    title = "Add member",
                    subtitle = "Lookup by email or EMA id and assign a role",
                    content = {
                        OutlinedTextField(
                            value = lookupIdentifier,
                            onValueChange = { lookupIdentifier = it },
                            label = { Text("Email or emaId") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                enabled = !isLoading,
                                onClick = {
                                    coroutineScope.launch {
                                        errorMessage = null
                                        lookedUpUser = null
                                        when (val result = presenter.lookupUser(lookupIdentifier.trim())) {
                                            is AppResult.Success -> lookedUpUser = result.value
                                            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
                                        }
                                    }
                                },
                            ) {
                                Text("Lookup user")
                            }
                            Text(
                                text = "Assign Reader/Editor/Admin after lookup.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        lookedUpUser?.let { user ->
                            HorizontalDivider()
                            Text(
                                text = "${user.email} • EMA ${user.emaId}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "uid: ${user.uid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RoleDropdown(
                                enabled = !isLoading,
                                buttonText = "Assign role",
                                currentRole = null,
                                onSelect = { role -> upsert(uid = user.uid, role = role) },
                            )
                        }
                    },
                )
            } else {
                SectionCard(
                    title = "Permissions",
                    subtitle = "Only superadmins can add or remove members",
                    content = {
                        Text(
                            text = "Ask a superadmin to grant access if you need to manage this tournament.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SectionCard(
                title = "Current members",
                subtitle = if (isLoading) "Loading…" else "${members.size} members",
                content = {
                    if (!isLoading && members.isEmpty()) {
                        Text(
                            text = "No members yet.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Add at least one Editor/Admin so the tournament can be managed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MembersTable(
                            members = members,
                            enabled = !isLoading,
                            onChangeRole = { uid, role -> upsert(uid = uid, role = role) },
                            onRemove = { uid -> remove(uid) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun MembersTable(
    members: List<TournamentMember>,
    enabled: Boolean,
    onChangeRole: (uid: String, role: TournamentRole) -> Unit,
    onRemove: (uid: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            DataTableHeaderRow {
                Text(
                    text = "User",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.65f),
                )
                Text(
                    text = "Role",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                )
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            DataTableDivider()
        }

        items(members, key = { it.uid }) { member ->
            DataTableRow {
                Text(
                    text = member.uid,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.role.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                )
                RowActionsMenu(
                    enabled = enabled,
                    items = listOf(
                        RowActionMenuItem(
                            label = "Set role: READER",
                            enabled = member.role != TournamentRole.READER,
                            onClick = { onChangeRole(member.uid, TournamentRole.READER) },
                        ),
                        RowActionMenuItem(
                            label = "Set role: EDITOR",
                            enabled = member.role != TournamentRole.EDITOR,
                            onClick = { onChangeRole(member.uid, TournamentRole.EDITOR) },
                        ),
                        RowActionMenuItem(
                            label = "Set role: ADMIN",
                            enabled = member.role != TournamentRole.ADMIN,
                            onClick = { onChangeRole(member.uid, TournamentRole.ADMIN) },
                        ),
                        RowActionMenuItem(
                            label = "Remove",
                            enabled = enabled,
                            onClick = { onRemove(member.uid) },
                        ),
                    ),
                    modifier = Modifier.width(44.dp),
                )
            }
            DataTableDivider()
        }
    }
}

@Composable
private fun RoleDropdown(
    enabled: Boolean,
    buttonText: String,
    currentRole: TournamentRole?,
    onSelect: (TournamentRole) -> Unit,
) {
    var isMenuOpen by remember(currentRole) { mutableStateOf(false) }
    AppTextButton(
        enabled = enabled,
        onClick = { isMenuOpen = true },
        modifier = Modifier.width(104.dp),
    ) {
        Text(currentRole?.name ?: buttonText)
    }
    DropdownMenu(
        expanded = isMenuOpen,
        onDismissRequest = { isMenuOpen = false },
    ) {
        TournamentRole.entries.forEach { role ->
            DropdownMenuItem(
                text = { Text(role.name) },
                onClick = {
                    isMenuOpen = false
                    onSelect(role)
                },
            )
        }
    }
}
