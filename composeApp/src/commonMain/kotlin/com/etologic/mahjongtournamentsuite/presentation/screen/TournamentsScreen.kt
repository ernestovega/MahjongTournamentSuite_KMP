package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.domain.validation.TournamentDateRangeValidator
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppVersionLabel
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformHorizontalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformVerticalScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorField
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentColorPickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentDateRangePickerDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentIdCardPreviewButton
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoCropDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoLibraryDialog
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentLogoPreview
import com.etologic.mahjongtournamentsuite.presentation.components.AgendaItemEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.RoundScheduleEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentScheduleEditor
import com.etologic.mahjongtournamentsuite.presentation.components.adjustedEndDate
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.emaCountryFlagCode
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.formatByteSize
import com.etologic.mahjongtournamentsuite.presentation.components.textFieldFocusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.toDisplayTournamentDate
import com.etologic.mahjongtournamentsuite.presentation.components.toIsoTournamentDateOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentColorOrNull
import com.etologic.mahjongtournamentsuite.presentation.components.synchronizeRoundScheduleRows
import com.etologic.mahjongtournamentsuite.presentation.components.toEditorRow
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentAgendaItems
import com.etologic.mahjongtournamentsuite.presentation.components.toTournamentRoundSchedules
import com.etologic.mahjongtournamentsuite.presentation.components.tournamentScheduleEditorError
import com.etologic.mahjongtournamentsuite.presentation.platform.SelectedImage
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.rememberImagePicker
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.VersionPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiIsoDateTimeOrDash
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton

@Composable
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
fun TournamentsScreen(
    navController: NavHostController,
) {
    val presenter = koinInject<TournamentsPresenter>()
    val versionPresenter = koinInject<VersionPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }

    var isRefreshing by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var backendVersion by remember { mutableStateOf<String?>(null) }
    var editDialogTournament by remember { mutableStateOf<Tournament?>(null) }
    var showCreateTournamentDialog by remember { mutableStateOf(false) }
    var restoreNewTournamentFocus by remember { mutableStateOf(false) }
    var tournamentFocusApplied by remember { mutableStateOf(false) }
    var lastFocusedTournamentId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedControl by rememberSaveable { mutableStateOf<String?>(null) }
    val playerBaseFocusRequester = remember { FocusRequester() }
    val usersFocusRequester = remember { FocusRequester() }
    val newTournamentFocusRequester = remember { FocusRequester() }

    fun refresh(force: Boolean = false) {
        coroutineScope.launch {
            isRefreshing = true
            errorMessage = null

            when (val profileResult = presenter.loadProfile(force)) {
                is AppResult.Success -> store.profile.value = profileResult.value
                is AppResult.Failure -> errorMessage = profileResult.error.toUiMessage()
            }

            when (val adminResult = presenter.loadAdminStatus(force)) {
                is AppResult.Success -> store.adminStatus.value = adminResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    adminResult.error.toUiMessage()
            }

            when (val tournamentsResult = presenter.loadTournaments(force)) {
                is AppResult.Success -> {
                    store.upsertTournaments(tournamentsResult.value)
                }

                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    tournamentsResult.error.toUiMessage()
            }

            when (val countriesResult = presenter.loadCountries(force)) {
                is AppResult.Success -> countries = countriesResult.value
                is AppResult.Failure -> if (errorMessage == null) errorMessage =
                    countriesResult.error.toUiMessage()
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
                    if (result.value.any { !it.isAssigned }) {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
                            ),
                        )
                        navController.navigate(PlayersRoute(tournamentId = tournament.id))
                    } else {
                        navController.navigate(
                            TournamentRoute(
                                tournamentId = tournament.id,
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

    LaunchedEffect(versionPresenter) {
        backendVersion = versionPresenter.loadBackendVersion()
    }

    val roleLabel = adminStatus?.let {
        when (it.role) {
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.ADMIN -> "Admin"
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.EDITOR -> "Editor"
        }
    }
    val canConfigureTournaments = adminStatus?.canConfigureTournaments == true
    val canDeleteTournaments = adminStatus?.canDeleteTournaments == true
    val canEditTournament = canConfigureTournaments || canDeleteTournaments
    val tournamentFocusRequesters = remember(tournaments.map { it.id }) {
        tournaments.map { FocusRequester() }
    }

    LaunchedEffect(isRefreshing, tournaments.map { it.id }) {
        if (!tournamentFocusApplied && !isRefreshing) {
            withFrameNanos { }
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
                        tournamentFocusRequesters.getOrNull(index)?.requestFocus()
                    } else {
                        usersFocusRequester.requestFocus()
                    }
                }
            }
            tournamentFocusApplied = true
        }
    }

    LaunchedEffect(showCreateTournamentDialog, restoreNewTournamentFocus) {
        if (!showCreateTournamentDialog && restoreNewTournamentFocus) {
            restoreNewTournamentFocus = false
            newTournamentFocusRequester.requestFocus()
        }
    }

    if (showCreateTournamentDialog) {
        CreateTournamentDialog(
            onDismiss = {
                showCreateTournamentDialog = false
                restoreNewTournamentFocus = true
            },
            onCreated = { tournament ->
                showCreateTournamentDialog = false
                navController.navigate(
                    TournamentRoute(
                        tournamentId = tournament.id,
                    ),
                )
                navController.navigate(PlayersRoute(tournamentId = tournament.id))
            },
        )
    }

    editDialogTournament?.let { tournament ->
        EditTournamentDialog(
            tournament = tournament,
            onDismiss = { editDialogTournament = null },
        )
    }


    AppScaffold(
        title = "Tournaments",
        isLoading = isRefreshing || isLoading,
        showBackgroundLogo = false,
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
                onAppUsers = if (adminStatus?.canManageUsers == true) {
                    {
                        lastFocusedControl = "users"
                        navController.navigate(UsersRoute)
                    }
                } else {
                    null
                },
                onTimer = { openTimer(navController) },
                usersFocusRequester = usersFocusRequester,
            )
        },
        actions = {
            AppTopBarActions(
                onPlayerBase = {
                    lastFocusedControl = "player-base"
                    navController.navigate(PlayerBaseRoute)
                },
                onNewTournament = if (adminStatus?.canCreateTournaments == true) {
                    {
                        lastFocusedControl = "new-tournament"
                        showCreateTournamentDialog = true
                    }
                } else {
                    null
                },
                playerBaseFocusRequester = playerBaseFocusRequester,
                newTournamentFocusRequester = newTournamentFocusRequester,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                val actionCellMinWidth = 56.dp
                                val flexibleColumns = 5
                                val columnsMinWidth = (cellMinWidth * flexibleColumns) + featuresGroupMinWidth(cellMinWidth) +
                                    ColorColumnWidth + CountryColumnWidth + ShortNameColumnWidth + actionCellMinWidth
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
                                                    // The lazy item can compose before the requester list catches up with the tournaments list.
                                                    .then(
                                                        tournamentFocusRequesters.getOrNull(index)
                                                            ?.let { Modifier.focusRequester(it) }
                                                            ?: Modifier,
                                                    )
                                                    .onFocusChanged {
                                                        if (it.isFocused) lastFocusedTournamentId = tournament.id
                                                        if (it.isFocused) lastFocusedControl = "tournament"
                                                    },
                                                tournament = tournament,
                                                enabled = !isLoading,
                                                cellMinWidth = cellMinWidth,
                                                actionCellMinWidth = actionCellMinWidth,
                                                showEdit = canEditTournament,
                                                onClick = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    openTournament(tournament)
                                                },
                                                onEdit = {
                                                    lastFocusedTournamentId = tournament.id
                                                    lastFocusedControl = "tournament"
                                                    editDialogTournament = tournament
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
                AppErrorDialog(
                    message = message,
                    onDismiss = { errorMessage = null },
                )
            }
            }

            AppVersionLabel(
                backendVersion = backendVersion,
                modifier = Modifier.align(Alignment.BottomStart),
            )
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
                text = user.alias.trim().ifBlank { user.toUiName() },
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
    AppTopBarButton(
        text = "Logout",
        icon = Icons.AutoMirrored.Filled.Logout,
        onClick = {
            coroutineScope.launch {
                presenter.signOut()
                navController.navigate(SignInRoute) {
                    popUpTo(TournamentsRoute) { inclusive = true }
                }
            }
        },
    )
}

@Composable
private fun TournamentTableHeader(
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
) {
    DataTableHeaderRow {
        HeaderCell(text = "Color", minWidth = cellMinWidth, weight = 0f, width = ColorColumnWidth, textAlign = TextAlign.Center)
        HeaderCell(text = "Country", minWidth = cellMinWidth, weight = 0f, width = CountryColumnWidth, textAlign = TextAlign.Center)
        HeaderCell(text = "City", minWidth = cellMinWidth, weight = 1.0f)
        HeaderCell(text = "Logo", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "Name", minWidth = cellMinWidth, weight = 2.0f)
        HeaderCell(text = "Short name", minWidth = cellMinWidth, weight = 0f, width = ShortNameColumnWidth)
        HeaderCell(text = "Players", minWidth = cellMinWidth, weight = .5f, textAlign = TextAlign.Center)
        HeaderCell(text = "From-to", minWidth = cellMinWidth, weight = 1.8f, textAlign = TextAlign.Center)
        Column(
            modifier = Modifier.weight(FeaturesGroupWeight).widthIn(min = featuresGroupMinWidth(cellMinWidth)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Features",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCell(text = "Teams", minWidth = cellMinWidth, weight = TeamsWeight, textAlign = TextAlign.Center)
                HeaderCell(text = "Best\nhands", minWidth = FeatureColumnMinWidth, weight = FeatureWeight, textAlign = TextAlign.Center, maxLines = 2)
                HeaderCell(text = "Chicken\nhands", minWidth = FeatureColumnMinWidth, weight = FeatureWeight, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
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
    enabled: Boolean,
    cellMinWidth: Dp,
    actionCellMinWidth: Dp,
    showEdit: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    DataTableRow(
        modifier = modifier,
        onClick = if (enabled) onClick else null,
    ) {
        TournamentColorCell(
            colorValue = tournament.primaryColor,
            minWidth = cellMinWidth,
        )
        TournamentCountryCell(
            countryCode = tournament.hostCountry,
            minWidth = cellMinWidth,
        )
        BodyCell(text = tournament.hostCity.ifBlank { "—" }, minWidth = cellMinWidth, weight = 1.0f)
        TournamentLogoCell(
            logoUrl = tournament.associationLogoUrl,
            tournamentName = tournament.name,
            minWidth = cellMinWidth,
        )
        BodyCell(text = tournament.name, minWidth = cellMinWidth, weight = 2.0f)
        BodyCell(
            text = tournament.shortName.ifBlank { "—" },
            minWidth = cellMinWidth,
            weight = 0f,
            width = ShortNameColumnWidth,
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
            text = tournamentDateRange(tournament),
            minWidth = cellMinWidth,
            weight = 1.8f,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.weight(FeaturesGroupWeight).widthIn(min = featuresGroupMinWidth(cellMinWidth)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeatureStateCell(
                enabled = tournament.isTeams,
                enabledDescription = "Team tournament",
                disabledDescription = "Individual tournament",
                minWidth = cellMinWidth,
                weight = TeamsWeight,
            )
            FeatureStateCell(
                enabled = tournament.countBestHands,
                enabledDescription = "Best hands counted",
                disabledDescription = "Best hands not counted",
                minWidth = FeatureColumnMinWidth,
                weight = FeatureWeight,
            )
            FeatureStateCell(
                enabled = tournament.countChickenHands,
                enabledDescription = "Chicken hands counted",
                disabledDescription = "Chicken hands not counted",
                minWidth = FeatureColumnMinWidth,
                weight = FeatureWeight,
            )
        }
        Box(
            modifier = Modifier.width(actionCellMinWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showEdit) {
                IconButton(
                    enabled = enabled,
                    onClick = onEdit,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${tournament.name}",
                    )
                }
            }
        }
    }
}

private fun tournamentDateRange(tournament: Tournament): String {
    val start = tournament.eventStartDate.toDisplayTournamentDate()
    val end = tournament.eventEndDate.toDisplayTournamentDate()
    return when {
        start.isBlank() && end.isBlank() -> "—"
        start.isBlank() -> end
        end.isBlank() -> start
        start == end -> start
        else -> "$start – $end"
    }
}

@Composable
private fun RowScope.TournamentCountryCell(
    countryCode: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier.width(CountryColumnWidth),
        contentAlignment = Alignment.Center,
    ) {
        CountryFlag(
            code = emaCountryFlagCode(countryCode).orEmpty(),
            contentDescription = countryCode.ifBlank { "No host country" },
        )
    }
}

@Composable
private fun RowScope.TournamentLogoCell(
    logoUrl: String?,
    tournamentName: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier
            .weight(.5f)
            .widthIn(min = minWidth)
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = MaterialTheme.shapes.small
        if (logoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .semantics { contentDescription = "No logo for $tournamentName" },
            )
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo for $tournamentName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(shape),
            )
        }
    }
}

@Composable
private fun RowScope.TournamentColorCell(
    colorValue: String,
    minWidth: Dp,
) {
    Box(
        modifier = Modifier
            .width(ColorColumnWidth)
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = colorValue.toTournamentColorOrNull()
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small,
                )
                .semantics { contentDescription = "Tournament color $colorValue" },
        )
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    width: Dp? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (width != null) Modifier.width(width) else Modifier.weight(weight).widthIn(min = minWidth),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
private fun RowScope.BodyCell(
    text: String,
    minWidth: Dp,
    weight: Float,
    width: Dp? = null,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = if (width != null) Modifier.width(width) else Modifier.weight(weight).widthIn(min = minWidth),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/** A yes or no cell: a check for an enabled feature and a cross for a disabled one. */
@Composable
private fun RowScope.FeatureStateCell(
    enabled: Boolean,
    enabledDescription: String,
    disabledDescription: String,
    minWidth: Dp,
    weight: Float,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .widthIn(min = minWidth),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = if (enabled) enabledDescription else disabledDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

/** Minimum width of the Best hands and Chicken hands columns. It fits their titles. */
private val FeatureColumnMinWidth = 96.dp
private const val TeamsWeight = .5f
private const val FeatureWeight = .8f

/** The Features group holds the Teams, Best hands and Chicken hands columns. Its weight is their sum. */
private const val FeaturesGroupWeight = TeamsWeight + FeatureWeight * 2

private fun featuresGroupMinWidth(cellMinWidth: Dp): Dp = cellMinWidth + (FeatureColumnMinWidth * 2) + 24.dp
private val ColorColumnWidth = 44.dp
private val CountryColumnWidth = 56.dp
private val ShortNameColumnWidth = 88.dp

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
