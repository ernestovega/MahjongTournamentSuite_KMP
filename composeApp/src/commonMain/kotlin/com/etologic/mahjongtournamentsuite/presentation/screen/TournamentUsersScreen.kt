package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.TournamentMember
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TournamentUsersScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<UsersPresenter>()
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var members by remember { mutableStateOf<List<TournamentMember>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lookupIdentifier by remember { mutableStateOf("") }
    var lookedUpUser by remember { mutableStateOf<UserProfile?>(null) }
    val lookupFocusRequester = remember { FocusRequester() }
    var initialLookupFocusPending by rememberSaveable(tournamentId) { mutableStateOf(true) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            lookedUpUser = null
            when (val result = presenter.loadMembers(tournamentId)) {
                is AppResult.Success -> members = result.value
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
            isLoading = false
        }
    }

    fun assign(uid: String) {
        coroutineScope.launch {
            errorMessage = null
            when (val result = presenter.upsertMember(tournamentId = tournamentId, uid = uid)) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
        }
    }

    fun remove(uid: String) {
        coroutineScope.launch {
            errorMessage = null
            when (val result = presenter.removeMember(tournamentId = tournamentId, uid = uid)) {
                is AppResult.Success -> refresh()
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
        }
    }

    LaunchedEffect(presenter, tournamentId) { refresh() }
    LaunchedEffect(isLoading) {
        if (initialLookupFocusPending && !isLoading && errorMessage == null) {
            lookupFocusRequester.requestFocus()
            initialLookupFocusPending = false
        }
    }

    AppScaffold(
        title = "Tournament editors",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = ::refresh) },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            errorMessage?.let {
                AppErrorDialog(
                    message = it,
                    onDismiss = { errorMessage = null },
                )
            }

            SectionCard(
                title = "Assign editor",
                subtitle = "Editors can assign other editor accounts to this tournament",
            ) {
                OutlinedTextField(
                    value = lookupIdentifier,
                    onValueChange = { lookupIdentifier = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth().focusRequester(lookupFocusRequester),
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
                                when (val result = presenter.lookupUser(lookupIdentifier.trim(), tournamentId)) {
                                    is AppResult.Success -> lookedUpUser = result.value
                                    is AppResult.Failure -> errorMessage = result.error.toUiMessage()
                                }
                            }
                        },
                    ) { Text("Look up account") }
                    Text(
                        text = "Create new accounts from the App Users screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                lookedUpUser?.let { user ->
                    HorizontalDivider()
                    Text(user.email, style = MaterialTheme.typography.titleSmall)
                    Button(
                        enabled = !isLoading && members.none { it.uid == user.uid },
                        onClick = { assign(user.uid) },
                    ) { Text(if (members.any { it.uid == user.uid }) "Already assigned" else "Assign editor") }
                }
            }

            SectionCard(
                title = "Assigned accounts",
                subtitle = if (isLoading) "Loading…" else "${members.size} accounts",
            ) {
                if (!isLoading && members.isEmpty()) {
                    Text("No editor accounts are assigned.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    TournamentUsersTable(
                        members = members,
                        enabled = !isLoading,
                        onRemove = ::remove,
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentUsersTable(
    members: List<TournamentMember>,
    enabled: Boolean,
    onRemove: (uid: String) -> Unit,
) {
    LazyColumnWithScrollbar(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            DataTableHeaderRow {
                Text("Account", modifier = Modifier.weight(0.65f), style = MaterialTheme.typography.labelLarge)
                Text("Role", modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelLarge)
                Text("", modifier = Modifier.width(88.dp))
            }
            DataTableDivider()
        }

        items(members, key = { it.uid }) { member ->
            DataTableRow {
                Text(
                    text = member.email.ifBlank { member.uid },
                    modifier = Modifier.weight(0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.role.label(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.25f),
                )
                if (member.role == GlobalUserRole.EDITOR) {
                    RowActionsMenu(
                        enabled = enabled,
                        items = listOf(
                            RowActionMenuItem(
                                label = "Remove",
                                enabled = enabled,
                                onClick = { onRemove(member.uid) },
                            ),
                        ),
                        modifier = Modifier.width(88.dp),
                    )
                } else {
                    Text(
                        text = "Global",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(88.dp),
                    )
                }
            }
            DataTableDivider()
        }
    }
}

private fun GlobalUserRole.label(): String = when (this) {
    GlobalUserRole.EDITOR -> "Editor"
    GlobalUserRole.ADMIN -> "Admin"
}
