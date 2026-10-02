package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.InfoTooltipIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ManualScoreTotalConfirmationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.platform.openRankings
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions

private sealed class PendingUnsavedAction {
    data object Back : PendingUnsavedAction()
    data object Refresh : PendingUnsavedAction()
    data class SelectRound(val roundId: Int) : PendingUnsavedAction()
    data class SelectTable(val tableId: Int) : PendingUnsavedAction()
    data object NavigatePlayers : PendingUnsavedAction()
    data object NavigateTeams : PendingUnsavedAction()
    data object OpenRankings : PendingUnsavedAction()
    data object OpenTimer : PendingUnsavedAction()
    data object ExportResults : PendingUnsavedAction()
}

@Composable
private fun LegacyTournamentScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<TablesPresenter>()
    val tablePresenter = koinInject<TableManagerPresenter>()
    val rankingPresenter = koinInject<RankingPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()
    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    val isTeamsTournament = tournaments.firstOrNull { it.id == tournamentId }?.isTeams == true

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rounds by remember { mutableStateOf<List<TournamentRound>>(emptyList()) }
    var initialTableSelectionPending by rememberSaveable(tournamentId) { mutableStateOf(true) }
    var selectedRoundId by remember { mutableStateOf<Int?>(null) }
    var allTables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var selectedTableId by remember { mutableStateOf<Int?>(null) }
    var playerNamesById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var tableState by remember { mutableStateOf<TableState?>(null) }
    var hands by remember { mutableStateOf<List<TableHand>>(emptyList()) }
    var pendingUnsavedAction by remember { mutableStateOf<PendingUnsavedAction?>(null) }
    var manualScoreTotalToConfirm by remember { mutableStateOf<Long?>(null) }
    var actionAfterManualScoreConfirmation by remember { mutableStateOf<PendingUnsavedAction?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var restoreResetFocus by remember { mutableStateOf(false) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    val initialRoundFocusRequester = remember { FocusRequester() }
    val playersFocusRequester = remember { FocusRequester() }
    val teamsFocusRequester = remember { FocusRequester() }
    val timerFocusRequester = remember { FocusRequester() }
    val rankingFocusRequester = remember { FocusRequester() }
    val exportFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    var screenFocusApplied by remember { mutableStateOf(false) }
    var lastFocusedControl by rememberSaveable(tournamentId) { mutableStateOf<String?>(null) }

    suspend fun loadSelectedTable(force: Boolean = false) {
        val roundId = selectedRoundId ?: return
        val tableId = selectedTableId ?: return

        when (val tableResult = tablePresenter.loadTableWithHands(tournamentId, roundId, tableId, force)) {
            is AppResult.Success -> {
                tableState = tableResult.value.first
                hands = tableResult.value.second
            }

            is AppResult.Failure -> {
                errorMessage = tableResult.error.toUiMessage()
            }
        }
    }

    suspend fun selectRound(roundId: Int) {
        if (roundId == selectedRoundId) return

        selectedRoundId = roundId
        selectedTableId = null
        tableState = null
        hands = emptyList()

        isLoading = true
        errorMessage = null

        when (val tablesResult = presenter.loadTables(tournamentId, roundId)) {
            is AppResult.Success -> {
                tables = tablesResult.value.sortedBy { it.tableId }
                allTables = allTables.filterNot { it.roundId == roundId } + tables
                selectedTableId = tables.firstOrNull()?.tableId
            }

            is AppResult.Failure -> errorMessage = tablesResult.error.toUiMessage()
        }

        loadSelectedTable()
        isLoading = false
    }

    suspend fun selectTable(tableId: Int) {
        if (selectedRoundId == null) return
        if (tableId == selectedTableId) return

        selectedTableId = tableId
        tableState = null
        hands = emptyList()

        isLoading = true
        errorMessage = null
        loadSelectedTable()
        isLoading = false
    }

    suspend fun refresh(force: Boolean = false) {
        val chooseInitialTable = initialTableSelectionPending
        isLoading = true
        errorMessage = null

        val basePlayersByEma = when (val basePlayersResult = presenter.loadBasePlayers(force)) {
            is AppResult.Success -> basePlayersResult.value.associateBy { it.emaId }
            is AppResult.Failure -> {
                errorMessage = basePlayersResult.error.toUiMessage()
                emptyMap()
            }
        }

        when (val playersResult = presenter.loadPlayers(tournamentId, force)) {
            is AppResult.Success -> {
                playerNamesById = playersResult.value.associate { slot ->
                    val assignedName = slot.assignedEmaId
                        ?.let(basePlayersByEma::get)
                        ?.displayName
                        ?: slot.nonMember?.displayName
                    slot.id to (assignedName ?: "Player ${slot.id}")
                }
            }

            is AppResult.Failure -> {
                playerNamesById = emptyMap()
                if (errorMessage == null) errorMessage = playersResult.error.toUiMessage()
            }
        }

        val roundsResult = presenter.loadRounds(tournamentId, force)
        val newRounds = when (roundsResult) {
            is AppResult.Success -> roundsResult.value.sortedBy { it.roundId }
            is AppResult.Failure -> {
                errorMessage = roundsResult.error.toUiMessage()
                isLoading = false
                return
            }
        }

        rounds = newRounds
        val nextRoundId = selectedRoundId
            ?.takeIf { id -> newRounds.any { it.roundId == id } }
            ?: newRounds.firstOrNull()?.roundId
        selectedRoundId = nextRoundId

        val roundId = nextRoundId
        if (roundId == null) {
            tables = emptyList()
            selectedTableId = null
            tableState = null
            hands = emptyList()
            isLoading = false
            return
        }

        when (val tablesResult = presenter.loadTables(tournamentId, roundId = null, force = force)) {
            is AppResult.Success -> {
                allTables = tablesResult.value.sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
                tables = allTables.filter { it.roundId == roundId }
                if (chooseInitialTable) {
                    val initialTable = firstTournamentTableToOpen(allTables)
                    val initialRound = initialTable?.roundId ?: roundId
                    if (initialRound != roundId && newRounds.any { it.roundId == initialRound }) {
                        selectedRoundId = initialRound
                        tables = allTables.filter { it.roundId == initialRound }
                    }
                }
            }

            is AppResult.Failure -> errorMessage = tablesResult.error.toUiMessage()
        }

        val initialTable = if (chooseInitialTable) firstTournamentTableToOpen(allTables) else null
        val nextTableId = if (chooseInitialTable) {
            initialTable
                ?.takeIf { it.roundId == selectedRoundId }
                ?.tableId
                ?: tables.firstOrNull()?.tableId
        } else {
            selectedTableId
                ?.takeIf { id -> tables.any { it.tableId == id } }
                ?: tables.firstOrNull()?.tableId
        }
        selectedTableId = nextTableId
        if (chooseInitialTable) initialTableSelectionPending = false

        tableState = null
        hands = emptyList()
        loadSelectedTable(force)

        isLoading = false
    }

    LaunchedEffect(tournamentId) {
        refresh()
    }

    val table = tableState
    val editorState = remember(table, hands) { table?.let { TableManagerEditorState.from(it, hands) } }
    val hasUnsavedChanges = editorState?.hasUnsavedChanges == true
    val timerInitialRound = allTables
        .asSequence()
        .filter { it.isCompleted || it.hasProgress }
        .maxOfOrNull { it.roundId }
        ?.plus(1)
        ?: 1
    val roleLabel = adminStatus?.let {
        when (it.role) {
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.ADMIN -> "Admin"
            com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole.EDITOR -> "Editor"
        }
    }

    LaunchedEffect(isLoading, rounds.map { it.roundId }, selectedRoundId) {
        if (!screenFocusApplied && !isLoading && rounds.isNotEmpty()) {
            when (lastFocusedControl) {
                "players" -> playersFocusRequester.requestFocus()
                "teams" -> teamsFocusRequester.requestFocus()
                "timer" -> timerFocusRequester.requestFocus()
                "ranking" -> rankingFocusRequester.requestFocus()
                "export" -> exportFocusRequester.requestFocus()
                else -> initialRoundFocusRequester.requestFocus()
            }
            screenFocusApplied = true
        }
    }

    LaunchedEffect(showResetConfirmation, restoreResetFocus, isLoading, editorState) {
        if (!showResetConfirmation && restoreResetFocus && !isLoading && editorState != null) {
            restoreResetFocus = false
            resetFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(manualScoreTotalToConfirm, restoreSaveFocus, isLoading) {
        if (manualScoreTotalToConfirm == null && restoreSaveFocus && !isLoading) {
            restoreSaveFocus = false
            saveFocusRequester.requestFocus()
        }
    }

    suspend fun saveChanges(): Boolean {
        val editor = editorState ?: return true
        val roundId = selectedRoundId ?: return true
        val tableId = selectedTableId ?: return true

        val tablePatch = editor.buildApplicationTablePatch()
        val handPatches = editor.buildHandPatches()
        if (tablePatch.isEmpty() && handPatches.isEmpty()) return true

        isLoading = true
        errorMessage = null
        try {
            return when (val result = tablePresenter.saveTableState(
                tournamentId = tournamentId,
                roundId = roundId,
                tableId = tableId,
                expectedVersion = editor.version,
                tablePatch = tablePatch,
                handPatches = handPatches.toMap(),
            )) {
                is AppResult.Success -> {
                    tableState = result.value.first
                    hands = result.value.second
                    true
                }
                is AppResult.Failure -> {
                    errorMessage = result.error.toUiMessage()
                    false
                }
            }
        } finally {
            isLoading = false
        }
    }

    suspend fun resetCurrentTable() {
        val roundId = selectedRoundId ?: return
        val tableId = selectedTableId ?: return

        isLoading = true
        errorMessage = null
        when (val result = tablePresenter.resetTable(tournamentId, roundId, tableId)) {
            is AppResult.Success -> {
                showResetConfirmation = false
                restoreResetFocus = true
                tableState = null
                hands = emptyList()
                refresh()
            }
            is AppResult.Failure -> {
                showResetConfirmation = false
                restoreResetFocus = true
                errorMessage = result.error.toUiMessage()
                isLoading = false
            }
        }
    }

    suspend fun exportResults() {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        try {
            when (val result = rankingPresenter.load(tournamentId)) {
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
                is AppResult.Success -> {
                    val snapshot = result.value
                    val exportValidationError = officialResultsExportError(snapshot.rankingTables)
                    if (exportValidationError != null) {
                        errorMessage = exportValidationError
                        return
                    }
                    val unassigned = snapshot.tournamentPlayers.filterNot { it.isAssigned }
                    if (unassigned.isNotEmpty()) {
                        errorMessage = "Assign an EMA player or non-member to every tournament slot before export."
                        return
                    }
                    val startDate = snapshot.tournament.eventStartDate ?: snapshot.tournament.createdAt?.take(10) ?: "undated"
                    val endDate = snapshot.tournament.eventEndDate ?: startDate
                    when (val report = rankingPresenter.generateEmaReport(tournamentId, snapshot.rankings.players)) {
                        is AppResult.Failure -> errorMessage = report.error.toUiMessage()
                        is AppResult.Success -> {
                            val fileName = "${safeFileName(snapshot.tournament.name)}_${startDate}_$endDate.xlsx"
                            saveBinaryFile(
                                fileName = fileName,
                                content = report.value,
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            errorMessage = "The EMA results file could not be saved."
        } finally {
            isLoading = false
        }
    }

    suspend fun performUnsavedAction(action: PendingUnsavedAction) {
        when (action) {
            PendingUnsavedAction.Back -> navController.popBackStack()
            PendingUnsavedAction.Refresh -> refresh(force = true)
            is PendingUnsavedAction.SelectRound -> selectRound(action.roundId)
            is PendingUnsavedAction.SelectTable -> selectTable(action.tableId)
            PendingUnsavedAction.NavigatePlayers -> navController.navigate(PlayersRoute(tournamentId = tournamentId))
            PendingUnsavedAction.NavigateTeams -> navController.navigate(TeamsRoute(tournamentId = tournamentId))
            PendingUnsavedAction.OpenRankings -> openRankings(navController, tournamentId)
            PendingUnsavedAction.OpenTimer -> openTimer(navController, timerInitialRound)
            PendingUnsavedAction.ExportResults -> exportResults()
        }
    }

    fun requestUnsavedAction(action: PendingUnsavedAction) {
        if (isLoading) {
            if (action == PendingUnsavedAction.Back) navController.popBackStack()
            return
        }
        if (hasUnsavedChanges) {
            pendingUnsavedAction = action
            return
        }
        coroutineScope.launch { performUnsavedAction(action) }
    }

    fun saveAndContinue(action: PendingUnsavedAction?) {
        coroutineScope.launch {
            if (saveChanges()) {
                pendingUnsavedAction = null
                action?.let { performUnsavedAction(it) }
            }
        }
    }

    fun requestSave(action: PendingUnsavedAction? = null) {
        if (isLoading) return
        val nonZeroTotal = editorState?.nonZeroManualScoreTotal
        if (nonZeroTotal != null) {
            actionAfterManualScoreConfirmation = action
            manualScoreTotalToConfirm = nonZeroTotal
            return
        }
        saveAndContinue(action)
    }

    pendingUnsavedAction?.takeIf { manualScoreTotalToConfirm == null }?.let { action ->
        UnsavedChangesDialog(
            isSaving = isLoading,
            onSave = { requestSave(action) },
            onDiscard = {
                pendingUnsavedAction = null
                coroutineScope.launch { performUnsavedAction(action) }
            },
            onCancel = { pendingUnsavedAction = null },
        )
    }

    manualScoreTotalToConfirm?.let { total ->
        ManualScoreTotalConfirmationDialog(
            total = total,
            onConfirm = {
                val action = actionAfterManualScoreConfirmation
                actionAfterManualScoreConfirmation = null
                manualScoreTotalToConfirm = null
                saveAndContinue(action)
            },
            onCancel = {
                val wasDirectSave = actionAfterManualScoreConfirmation == null
                actionAfterManualScoreConfirmation = null
                manualScoreTotalToConfirm = null
                restoreSaveFocus = wasDirectSave
            },
        )
    }

    if (showResetConfirmation) {
        selectedRoundId?.let { resetRoundId ->
            selectedTableId?.let { resetTableId ->
                ResetTableDialog(
                    roundId = resetRoundId,
                    tableId = resetTableId,
                    isResetting = isLoading,
                    onConfirm = { coroutineScope.launch { resetCurrentTable() } },
                    onCancel = {
                        showResetConfirmation = false
                        restoreResetFocus = true
                    },
                )
            }
        }
    }

    errorMessage?.let { message ->
        AppErrorDialog(
            message = message,
            onDismiss = { errorMessage = null },
        )
    }

    AppScaffold(
        title = tournaments.firstOrNull { it.id == tournamentId }
            ?.name
            ?.ifBlank { "Tournament" }
            ?: "Tournament",
        isLoading = isLoading,
        onBack = { requestUnsavedAction(PendingUnsavedAction.Back) },
        leadingActions = {
            ProfileInfo(profile, roleLabel)
            AppTopBarLeadingActions(
                onTimer = {
                    lastFocusedControl = "timer"
                    openTimer(navController, timerInitialRound)
                },
                onRanking = {
                    lastFocusedControl = "ranking"
                    openRankings(navController, tournamentId)
                },
                timerFocusRequester = timerFocusRequester,
                rankingFocusRequester = rankingFocusRequester,
            )
        },
        actions = {
            AppTopBarActions(
                onTeams = if (isTeamsTournament) {
                    {
                        lastFocusedControl = "teams"
                        requestUnsavedAction(PendingUnsavedAction.NavigateTeams)
                    }
                } else {
                    null
                },
                onPlayers = {
                    lastFocusedControl = "players"
                    requestUnsavedAction(PendingUnsavedAction.NavigatePlayers)
                },
                onEmaReport = {
                    lastFocusedControl = "export"
                    requestUnsavedAction(PendingUnsavedAction.ExportResults)
                },
                playersFocusRequester = playersFocusRequester,
                teamsFocusRequester = teamsFocusRequester,
                exportFocusRequester = exportFocusRequester,
            )
            if (
                adminStatus?.isAdmin == true &&
                selectedRoundId != null &&
                selectedTableId != null &&
                editorState != null
            ) {
                AppTopBarButton(
                    text = "Reset table",
                    icon = Icons.Default.RestartAlt,
                    onClick = { showResetConfirmation = true },
                    enabled = !isLoading,
                    focusRequester = resetFocusRequester,
                    textColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1600.dp)
                    .fillMaxSize()
                    .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoundTableSidebar(
                    rounds = rounds,
                    allTables = allTables,
                    selectedRoundId = selectedRoundId,
                    tables = tables,
                    selectedTableId = selectedTableId,
                    enabled = !isLoading,
                    isLoading = isLoading,
                    onSelectRound = { roundId -> requestUnsavedAction(PendingUnsavedAction.SelectRound(roundId)) },
                    onSelectTable = { tableId -> requestUnsavedAction(PendingUnsavedAction.SelectTable(tableId)) },
                    initialRoundFocusRequester = initialRoundFocusRequester,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val saveButtonHeight = 64.dp
                    val listState = rememberLazyListState()
                    val showOverlaySave = hasUnsavedChanges

                    LazyColumnWithScrollbar(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = if (showOverlaySave) saveButtonHeight + 32.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            if (selectedRoundId != null && selectedTableId != null && editorState != null) {
                                TableManagerContent(
                                    editor = editorState,
                                    enabled = !isLoading,
                                    playerNamesById = playerNamesById,
                                    showSeatPlayerIds = true,
                                )
                            } else if (!isLoading) {
                                Text(
                                    text = "Select a round and table to manage hands.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                    }

                    if (showOverlaySave) {
                        val colors = MaterialTheme.colorScheme
                        Button(
                            onClick = { requestSave() },
                            enabled = !isLoading,
                            focusRequester = saveFocusRequester,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(saveButtonHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.tertiary,
                                contentColor = colors.onTertiary,
                                disabledContainerColor = colors.tertiary.copy(alpha = 0.6f),
                                disabledContentColor = colors.onTertiary.copy(alpha = 0.6f),
                            ),
                            border = BorderStroke(1.dp, colors.tertiary),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundTableSidebar(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    selectedRoundId: Int?,
    tables: List<TournamentTable>,
    selectedTableId: Int?,
    enabled: Boolean,
    isLoading: Boolean,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
    initialRoundFocusRequester: FocusRequester? = null,
) {
    val roundsListState = rememberLazyListState()
    val tablesListState = rememberLazyListState()
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 260.dp, max = 360.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rounds.isEmpty()) {
                Text(
                    text = if (isLoading) "Loading rounds…" else "No rounds yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Rounds",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompletionStatusInfoIcon(
                            description = "Empty: all tables are empty.\nProgress: at least one table has data.\nManual: at least one table has valid saved Manual Scores.\nCompleted: all tables are completed without a manual mode.",
                        )
                    }
                    LazyColumnWithScrollbar(
                        state = roundsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(rounds, key = { _, round -> round.roundId }) { index, round ->
                            RoundTableSidebarItem(
                                label = "Round ${round.roundId}",
                                status = roundCompletionStatus(allTables.filter { it.roundId == round.roundId }),
                                selected = round.roundId == selectedRoundId,
                                enabled = enabled,
                                onClick = { onSelectRound(round.roundId) },
                                focusRequester = if (
                                    round.roundId == selectedRoundId || selectedRoundId == null && index == 0
                                ) {
                                    initialRoundFocusRequester
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tables",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompletionStatusInfoIcon(
                            description = "Empty: no table data.\nProgress: table data exists.\nManual: valid Manual Scores are saved.\nCompleted: hands are completed without manual mode.",
                        )
                    }

                    when {
                        selectedRoundId == null -> {
                            Text(
                                text = "Select a round.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        tables.isEmpty() -> {
                            Text(
                                text = if (isLoading) "Loading tables…" else "No tables for this round yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            LazyColumnWithScrollbar(
                                state = tablesListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(tables, key = { it.tableId }) { table ->
                                    RoundTableSidebarItem(
                                        label = "Table ${table.tableId}",
                                        status = tableCompletionStatus(table),
                                        selected = table.tableId == selectedTableId,
                                        enabled = enabled,
                                        onClick = { onSelectTable(table.tableId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundTableSidebarItem(
    label: String,
    status: CompletionStatus,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        ).activateOnEnter(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Icon(
                    imageVector = status.icon,
                    contentDescription = status.label,
                    tint = if (selected) LocalContentColor.current else status.color,
                )
            }
        }
    }
}

internal enum class CompletionStatus(
    val label: String,
    val icon: ImageVector,
    val color: Color,
) {
    Empty("Empty", Icons.Default.RadioButtonUnchecked, Color.Gray),
    InProgress("In progress", Icons.Default.Timelapse, Color(0xFFB26A00)),
    Manual("Manual", Icons.Default.Check, Color(0xFF1565C0)),
    Completed("Completed", Icons.Default.DoneAll, Color(0xFF2E7D32)),
}

@Composable
private fun CompletionStatusInfoIcon(description: String) {
    InfoTooltipIcon(
        description = description,
        contentDescription = "Show completion status information",
    )
}

internal fun tableCompletionStatus(table: TournamentTable): CompletionStatus = when {
    table.hasValidManualTotals -> CompletionStatus.Manual
    table.isCompleted -> CompletionStatus.Completed
    table.hasProgress -> CompletionStatus.InProgress
    else -> CompletionStatus.Empty
}

internal fun firstTournamentTableToOpen(tables: List<TournamentTable>): TournamentTable? {
    val orderedTables = tables.sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
    return orderedTables.firstOrNull { table ->
        tableCompletionStatus(table) in setOf(CompletionStatus.Empty, CompletionStatus.InProgress)
    } ?: orderedTables.firstOrNull()
}

internal fun roundCompletionStatus(tables: List<TournamentTable>): CompletionStatus = when {
    tables.isEmpty() -> CompletionStatus.Empty
    tables.any { tableCompletionStatus(it) == CompletionStatus.Manual } -> CompletionStatus.Manual
    tables.all { tableCompletionStatus(it) == CompletionStatus.Completed } -> CompletionStatus.Completed
    tables.any { tableCompletionStatus(it) == CompletionStatus.InProgress } -> CompletionStatus.InProgress
    else -> CompletionStatus.Empty
}

private fun safeFileName(value: String): String = value
    .trim()
    .replace(Regex("[^A-Za-z0-9._ -]"), "_")
    .replace(Regex("\\s+"), "_")
    .trim('_')
    .ifBlank { "tournament" }
