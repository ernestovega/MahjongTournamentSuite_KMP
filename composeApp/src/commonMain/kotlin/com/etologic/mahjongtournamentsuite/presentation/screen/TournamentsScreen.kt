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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.presentation.CreateTournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.textFieldFocusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformHorizontalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
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

    var isRefreshing by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleteDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var renameDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var renameValue by remember { mutableStateOf(TextFieldValue()) }
    var settingsShortName by remember { mutableStateOf("") }
    var settingsPrimaryColor by remember { mutableStateOf("#02B16B") }
    var settingsLogo by remember { mutableStateOf<SelectedImage?>(null) }
    var removeSettingsLogo by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var tournamentFocusApplied by remember { mutableStateOf(false) }
    var lastFocusedTournamentId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedControl by rememberSaveable { mutableStateOf<String?>(null) }
    val playerBaseFocusRequester = remember { FocusRequester() }
    val usersFocusRequester = remember { FocusRequester() }
    val newTournamentFocusRequester = remember { FocusRequester() }
    val renameFieldFocusRequester = remember { FocusRequester() }
    val renameConfirmFocusRequester = remember { FocusRequester() }
    val renameCancelFocusRequester = remember { FocusRequester() }
    val settingsLogoPicker = rememberImagePicker(
        onImageSelected = { image ->
            if (image.contentType !in setOf("image/jpeg", "image/png")) {
                renameError = "Association logo must be a JPEG or PNG image."
            } else if (image.bytes.size > 2 * 1024 * 1024) {
                renameError = "Association logo must be 2 MB or smaller."
            } else {
                settingsLogo = image
                removeSettingsLogo = false
                renameError = null
            }
        },
        onError = { message -> renameError = message },
    )

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
                }

                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    tournamentsResult.error.toUiMessage()
            }

            isRefreshing = false
        }
    }

    fun openTournament(tournament: Tournament) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val result = presenter.loadPlayers(tournament.id)) {
                is AppResult.Success -> {
                    store.upsertPlayers(tournament.id, result.value)
                    if (result.value.any { it.assignedEmaId == null }) {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name,
                            ),
                        )
                        navController.navigate(PlayersRoute(tournamentId = tournament.id))
                    } else {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name,
                            ),
                        )
                    }
                }

                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }

            isLoading = false
        }
    }

    LaunchedEffect(presenter) {
        refresh()
    }

    val roleLabel = adminStatus?.let {
        when (it.role) {
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.ADMIN -> "Admin"
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.EDITOR -> "Editor"
        }
    }
    val canConfigureTournaments = adminStatus?.canConfigureTournaments == true
    val canDeleteTournaments = adminStatus?.canDeleteTournaments == true
    val tournamentFocusRequesters = remember(tournaments.map { it.id }) {
        tournaments.map { FocusRequester() }
    }

    LaunchedEffect(isRefreshing, tournaments.map { it.id }) {
        if (!tournamentFocusApplied && !isRefreshing) {
            when (lastFocusedControl) {
                "player-base" -> playerBaseFocusRequester.requestFocus()
                "users" -> if (adminStatus?.canManageUsers == true) {
                    usersFocusRequester.requestFocus()
                } else {
                    newTournamentFocusRequester.requestFocus()
                }
                "new-tournament" -> if (adminStatus?.canCreateTournaments == true) {
                    newTournamentFocusRequester.requestFocus()
                } else {
                    usersFocusRequester.requestFocus()
                }
                else -> {
                    if (tournamentFocusRequesters.isNotEmpty()) {
                        val index = tournaments.indexOfFirst { it.id == lastFocusedTournamentId }.coerceAtLeast(0)
                        tournamentFocusRequesters[index].requestFocus()
                    } else {
                        usersFocusRequester.requestFocus()
                    }
                }
            }
            tournamentFocusApplied = true
        }
    }

    renameDialogTournament?.let { tournament ->
        LaunchedEffect(tournament.id) {
            renameFieldFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isLoading) renameDialogTournament = null },
            title = { Text("Edit tournament") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = {
                            renameValue = it
                            renameError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFieldFocusRequester),
                        label = { Text("Tournament name") },
                        singleLine = true,
                        isError = renameError != null,
                    )
                    OutlinedTextField(
                        value = settingsShortName,
                        onValueChange = {
                            settingsShortName = it.take(10)
                            renameError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Short name") },
                        supportingText = { Text("Maximum 10 characters") },
                        singleLine = true,
                        isError = renameError != null,
                    )
                    OutlinedTextField(
                        value = settingsPrimaryColor,
                        onValueChange = {
                            settingsPrimaryColor = it.take(7)
                            renameError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Primary color") },
                        supportingText = { Text("#RRGGBB") },
                        singleLine = true,
                        isError = renameError != null,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = settingsLogoPicker::launch) {
                            Text(if (settingsLogo == null) "Choose logo" else "Change logo")
                        }
                        Text(
                            text = when {
                                settingsLogo != null -> settingsLogo?.fileName.orEmpty()
                                removeSettingsLogo -> "Logo will be removed"
                                tournament.associationLogoUrl != null -> "Current logo"
                                else -> "No logo"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (settingsLogo != null || (!removeSettingsLogo && tournament.associationLogoUrl != null)) {
                            FocusedTextButton(
                                onClick = {
                                    settingsLogo = null
                                    removeSettingsLogo = true
                                },
                            ) { Text("Remove") }
                        }
                    }
                    renameError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isLoading && renameValue.text.trim().isNotEmpty() && settingsShortName.isNotBlank(),
                    onClick = {
                        val newName = renameValue.text.trim()
                        val newShortName = settingsShortName.trim()
                        val newPrimaryColor = settingsPrimaryColor.trim().uppercase()
                        if (newShortName.isEmpty() || newShortName.length > 10) {
                            renameError = "Short name must contain 1 to 10 characters."
                            return@Button
                        }
                        if (!Regex("^#[0-9A-F]{6}$").matches(newPrimaryColor)) {
                            renameError = "Primary color must use #RRGGBB format."
                            return@Button
                        }
                        coroutineScope.launch {
                            isLoading = true
                            renameError = null
                            when (val result = presenter.updateTournamentSettings(
                                tournamentId = tournament.id,
                                name = newName,
                                shortName = newShortName,
                                primaryColor = newPrimaryColor,
                                associationLogoContentType = settingsLogo?.contentType,
                                associationLogoBytes = settingsLogo?.bytes,
                                removeAssociationLogo = removeSettingsLogo,
                            )) {
                                is AppResult.Success -> {
                                    renameDialogTournament = null
                                    store.updateTournament(result.value)
                                    refresh()
                                }

                                is AppResult.Failure -> renameError = result.error.toUiMessage()
                            }
                            isLoading = false
                        }
                    },
                    focusRequester = renameConfirmFocusRequester,
                    buttonModifier = Modifier,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                FocusedTextButton(
                    enabled = !isLoading,
                    onClick = { renameDialogTournament = null },
                    focusRequester = renameCancelFocusRequester,
                    buttonModifier = Modifier,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    deleteDialogTournament?.let { tournament ->
        val deleteFocusRequester = remember(tournament.id) { FocusRequester() }
        val cancelFocusRequester = remember(tournament.id) { FocusRequester() }

        LaunchedEffect(tournament.id) {
            deleteFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!isLoading) deleteDialogTournament = null },
            title = { Text("Delete tournament") },
            text = { Text("This will permanently delete \"${tournament.name}\" (ID: ${tournament.id}).") },
            confirmButton = {
                Button(
                    enabled = !isLoading,
                    focusRequester = deleteFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = cancelFocusRequester,
                        next = cancelFocusRequester,
                    ),
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
                    focusRequester = cancelFocusRequester,
                    buttonModifier = Modifier.focusLoop(
                        previous = deleteFocusRequester,
                        next = deleteFocusRequester,
                    ),
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
                onPlayerBase = {
                    lastFocusedControl = "player-base"
                    navController.navigate(PlayerBaseRoute)
                },
                onUsers = if (adminStatus?.canManageUsers == true) {
                    {
                        lastFocusedControl = "users"
                        navController.navigate(UsersRoute())
                    }
                } else {
                    null
                },
                onRefresh = { refresh() },
                onNewTournament = if (adminStatus?.canCreateTournaments == true) {
                    {
                        lastFocusedControl = "new-tournament"
                        navController.navigate(CreateTournamentRoute)
                    }
                } else {
                    null
                },
                playerBaseFocusRequester = playerBaseFocusRequester,
                usersFocusRequester = usersFocusRequester,
                newTournamentFocusRequester = newTournamentFocusRequester,
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
                                val numColumns = 12
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
                                        itemsIndexed(tournaments, key = { _, tournament -> tournament.id }) { index, tournament ->
                                            TournamentTableRow(
                                                modifier = Modifier
                                                    .focusRequester(tournamentFocusRequesters[index])
                                                    .onFocusChanged {
                                                        if (it.isFocused) lastFocusedTournamentId = tournament.id
                                                        if (it.isFocused) lastFocusedControl = "tournament"
                                                    },
                                                tournament = tournament,
                                                createdByName = tournament.createdByName,
                                                enabled = !isLoading,
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                                showRename = canConfigureTournaments,
                                                showDelete = canDeleteTournaments,
                                                onClick = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    openTournament(tournament)
                                                },
                                                onDelete = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    deleteDialogTournament = tournament
                                                },
                                                onRename = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    renameValue = TextFieldValue(
                                                        text = tournament.name,
                                                        selection = TextRange(tournament.name.length),
                                                    )
                                                    renameError = null
                                                    settingsShortName = tournament.shortName.ifBlank {
                                                        tournament.name.take(10)
                                                    }
                                                    settingsPrimaryColor = tournament.primaryColor
                                                    settingsLogo = null
                                                    removeSettingsLogo = false
                                                    renameDialogTournament = tournament
                                                },
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

                                        PlatformHorizontalScrollbar(
                                            scrollState = scrollState,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(12.dp),
                                        )
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
        HeaderCell(text = "Created by", minWidth = cellMinWidth, weight = .6f)
        HeaderCell(text = "Teams", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Players", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Rounds", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "From", minWidth = cellMinWidth, weight = 1.0f)
        HeaderCell(text = "To", minWidth = cellMinWidth, weight = 1.0f)
        HeaderCell(
            text = "Tries",
            minWidth = cellMinWidth,
            weight = .6f,
            textAlign = TextAlign.Center,
        )
        HeaderCell(text = "Created", minWidth = cellMinWidth, weight = 1.05f)
        HeaderCell(text = "Updated", minWidth = cellMinWidth, weight = 1.05f)
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
    modifier: Modifier = Modifier,
    tournament: Tournament,
    createdByName: String?,
    enabled: Boolean,
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
    showRename: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    DataTableRow(
        modifier = modifier,
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
            text = createdByName ?: "—",
            minWidth = cellMinWidth,
            weight = .6f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = if (tournament.isTeams) "Yes" else "No",
            minWidth = cellMinWidth,
            weight = .5f,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.numPlayers.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
            textAlign = TextAlign.Center,
        )
        BodyCell(
            text = tournament.numRounds.toString(),
            minWidth = cellMinWidth,
            weight = .5f,
            textAlign = TextAlign.Center,
        )
        BodyCell(
            text = tournament.eventStartDate ?: "—",
            minWidth = cellMinWidth,
            weight = 1.0f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.eventEndDate ?: "—",
            minWidth = cellMinWidth,
            weight = 1.0f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.numTries.toString(),
            minWidth = cellMinWidth,
            weight = .6f,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.createdAt.toUiIsoDateTimeOrDash(),
            minWidth = cellMinWidth,
            weight = 1.05f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BodyCell(
            text = tournament.updatedAt.toUiIsoDateTimeOrDash(),
            minWidth = cellMinWidth,
            weight = 1.05f,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier.width(actionCellMinWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val actionItems = buildList {
                if (showRename) {
                    add(RowActionMenuItem(label = "Edit", onClick = onRename))
                }
                if (showDelete) {
                    add(
                        RowActionMenuItem(
                            label = "Delete",
                            enabled = enabled,
                            onClick = onDelete,
                        ),
                    )
                }
            }
            if (actionItems.isNotEmpty()) {
                RowActionsMenu(
                    enabled = enabled,
                    items = actionItems,
                    modifier = Modifier.height(40.dp),
                    buttonIcon = Icons.Default.MoreVert,
                    buttonIconSize = 36.dp,
                )
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
