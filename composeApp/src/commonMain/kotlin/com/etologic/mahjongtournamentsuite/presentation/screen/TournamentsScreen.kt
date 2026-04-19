package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.CreateTournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.MembersRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformHorizontalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiIsoDateTimeOrDash
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun TournamentsScreen(
    navController: NavHostController,
) {
    val presenter = koinInject<TournamentsPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleteDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var createdByNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun refresh() {
        coroutineScope.launch {
            isRefreshing = true
            errorMessage = null

            when (val profileResult = presenter.loadProfile()) {
                is AppResult.Success -> store.profile.value = profileResult.value
                is AppResult.Failure -> errorMessage = profileResult.error.toUiMessage()
            }

            when (val adminResult = presenter.loadAdminStatus()) {
                is AppResult.Success -> store.adminStatus.value = adminResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    adminResult.error.toUiMessage()
            }

            when (val tournamentsResult = presenter.loadTournaments()) {
                is AppResult.Success -> {
                    store.upsertTournaments(tournamentsResult.value)

                    if (store.adminStatus.value?.isSuperadmin == true) {
                        val uids = tournamentsResult.value
                            .mapNotNull { it.createdByUid?.trim()?.takeIf(String::isNotBlank) }
                            .distinct()
                            .filterNot(createdByNames::containsKey)

                        if (uids.isNotEmpty()) {
                            val next = createdByNames.toMutableMap()
                            for (uid in uids) {
                                when (val lookup = presenter.lookupUser(uid)) {
                                    is AppResult.Success -> next[uid] = lookup.value.toUiName()
                                    is AppResult.Failure -> next[uid] = uid
                                }
                            }
                            createdByNames = next.toMap()
                        }
                    }
                }

                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    tournamentsResult.error.toUiMessage()
            }

            isRefreshing = false
        }
    }

    LaunchedEffect(presenter) {
        refresh()
    }

    val roleLabel = adminStatus?.let { if (it.isSuperadmin) "Superadmin" else "Member" }
    val canDeleteTournaments = adminStatus?.isSuperadmin == true

    deleteDialogTournament?.let { tournament ->
        AlertDialog(
            onDismissRequest = { if (!isLoading) deleteDialogTournament = null },
            title = { Text("Delete tournament") },
            text = { Text("This will permanently delete \"${tournament.name}\" (ID: ${tournament.id}).") },
            confirmButton = {
                Button(
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        coroutineScope.launch {
                            errorMessage = null
                            isLoading = true

                            when (val result = presenter.deleteTournament(tournament.id)) {
                                is AppResult.Success -> {
                                    deleteDialogTournament = null
                                    store.removeTournament(tournament.id)
                                    refresh()
                                }

                                is AppResult.Failure -> {
                                    errorMessage = result.error.toUiMessage()
                                }
                            }

                            isLoading = false
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    enabled = !isLoading,
                    onClick = { deleteDialogTournament = null },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    AppScaffold(
        title = "Tournaments",
        isLoading = isRefreshing || isLoading,
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoutButton(coroutineScope, presenter, navController)
                ProfileInfo(profile, roleLabel)
            }
        },
        leadingActions = {
            AppTopBarLeadingActions(
                showThemeToggle = true,
                onTimer = { openTimer(navController) },
            )
        },
        actions = {
            AppTopBarActions(
                onMembers = { navController.navigate(MembersRoute()) },
                onRefresh = { refresh() },
                onNewTournament = { navController.navigate(CreateTournamentRoute) },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 24.dp, vertical = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionCard(
                content = {
                    when {
                        !isRefreshing && tournaments.isEmpty() -> {
                            Text(
                                text = "No tournaments yet.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        else -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val scrollState = rememberScrollState()
                                val listState = rememberLazyListState()
                                val cellMinWidth = 56.dp
                                val numColumns = 10
                                val actionCellMinWidth = 96.dp
                                val columnsMinWidth = (cellMinWidth * (numColumns - 1)) + actionCellMinWidth
                                val columnSpacing = 12.dp
                                val baseMinWidth = columnsMinWidth + (columnSpacing * (numColumns - 1))
                                val needsHorizontalScroll = baseMinWidth > maxWidth

                                val tableContent: @Composable (Modifier) -> Unit = { modifier ->
                                    LazyColumn(
                                        state = listState,
                                        modifier = modifier,
                                    ) {
                                        item {
                                            TournamentTableHeader(
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                            )
                                            DataTableDivider()
                                        }
                                        items(tournaments, key = { it.id }) { tournament ->
                                            TournamentTableRow(
                                                tournament = tournament,
                                                createdByName = tournament.createdByUid?.let { createdByNames[it] },
                                                enabled = !isLoading,
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                                showDelete = canDeleteTournaments,
                                                onClick = {
                                                    navController.navigate(
                                                        TournamentRoute(
                                                            tournamentId = tournament.id,
                                                            tournamentName = tournament.name,
                                                        ),
                                                    )
                                                },
                                                onDelete = { deleteDialogTournament = tournament },
                                            )
                                            DataTableDivider()
                                        }
                                    }
                                }

                                if (needsHorizontalScroll) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(scrollState)
                                                .pointerInput(scrollState) {
                                                    detectHorizontalDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        scrollState.dispatchRawDelta(-dragAmount)
                                                    }
                                                },
                                        ) {
                                            tableContent(Modifier.width(baseMinWidth))
                                        }

                                        PlatformVerticalScrollbar(
                                            listState = listState,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(12.dp),
                                        )

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(12.dp),
                                        ) {
                                            PlatformHorizontalScrollbar(
                                                scrollState = scrollState,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        tableContent(Modifier.fillMaxWidth())

                                        PlatformVerticalScrollbar(
                                            listState = listState,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )

            errorMessage?.let { message ->
                AppErrorMessage(message = message)
            }
        }
    }
}

@Composable
fun ProfileInfo(
    profile: UserProfile?,
    roleLabel: String?
) {
    profile?.let { user ->
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = user.toUiName(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            roleLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LogoutButton(
    coroutineScope: CoroutineScope,
    presenter: TournamentsPresenter,
    navController: NavHostController
) {
    AppTextButton(
        onClick = {
            coroutineScope.launch {
                presenter.signOut()
                navController.navigate(SignInRoute) {
                    popUpTo(TournamentsRoute) { inclusive = true }
                }
            }
        },
    ) {
        Text(
            text = "Logout",
            color = Color.White,
        )
    }
}

@Composable
private fun TournamentTableHeader(
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
) {
    DataTableHeaderRow {
        HeaderCell(text = "Name", minWidth = cellMinWidth, weight = 2.0f)
        HeaderCell(text = "ID", minWidth = cellMinWidth, weight = 1.1f)
        HeaderCell(text = "Created by", minWidth = cellMinWidth, weight = 1.1f)
        HeaderCell(text = "Teams", minWidth = cellMinWidth, weight = .5f)
        HeaderCell(text = "Players", minWidth = cellMinWidth, weight = .5f)
        HeaderCell(text = "Rounds", minWidth = cellMinWidth, weight = .5f)
        HeaderCell(
            text = "Tries",
            minWidth = cellMinWidth,
            weight = .6f,
            textAlign = TextAlign.Start
        )
        HeaderCell(text = "Created", minWidth = cellMinWidth, weight = .8f)
        HeaderCell(text = "Updated", minWidth = cellMinWidth, weight = .8f)
        Text(
            text = "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(actionCellMinWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TournamentTableRow(
    tournament: Tournament,
    createdByName: String?,
    enabled: Boolean,
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    DataTableRow(
        onClick = if (enabled) onClick else null,
    ) {
        BodyCell(text = tournament.name, minWidth = cellMinWidth, weight = 2.0f)
        BodyCell(
            text = tournament.id,
            minWidth = cellMinWidth,
            weight = 1.1f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = createdByName
                ?: tournament.createdByUid?.takeIf(String::isNotBlank)
                ?: "—",
            minWidth = cellMinWidth,
            weight = 1.1f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = if (tournament.isTeams) "Yes" else "No",
            minWidth = cellMinWidth,
            weight = .5f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.numPlayers.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
        )
        BodyCell(
            text = tournament.numRounds.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
        )
        BodyCell(
            text = tournament.numTries.toString(),
            minWidth = cellMinWidth,
            weight = .6f,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.createdAt.toUiIsoDateTimeOrDash(),
            minWidth = cellMinWidth,
            weight = .8f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.updatedAt.toUiIsoDateTimeOrDash(),
            minWidth = cellMinWidth,
            weight = .8f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier.width(actionCellMinWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showDelete) {
                Button(
                    enabled = enabled,
                    onClick = onDelete,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        text = "Delete",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
private fun RowScope.BodyCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

private fun UserProfile.toUiName(): String {
    val rawName = email
        .substringBefore("@")
        .replace('.', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercaseChar().toString() }
        }

    return rawName.ifBlank { email }
}
