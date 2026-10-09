package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.withFrameNanos
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentEditTitleAction
import com.etologic.mahjongtournamentsuite.domain.model.AppError
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
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.FocusHighlightContainer
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ResetTableDialog
import com.etologic.mahjongtournamentsuite.presentation.components.HandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.TableStatBadges
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.components.activateOnEnter
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.platform.openRankings
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed class RoundEditorAction {
    data object Back : RoundEditorAction()
    data class SelectRound(val roundId: Int) : RoundEditorAction()
    data class SelectTable(val tableId: Int) : RoundEditorAction()
    /** Opens the next table that needs data. It runs after the current table is saved. */
    data class NextTable(val fromTableId: Int) : RoundEditorAction()
    data object Players : RoundEditorAction()
    data object Teams : RoundEditorAction()
    data object Rankings : RoundEditorAction()
    data object Timer : RoundEditorAction()
    data object Export : RoundEditorAction()
}

@Composable
fun TournamentScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val tablesPresenter = koinInject<TablesPresenter>()
    val tablePresenter = koinInject<TableManagerPresenter>()
    val rankingPresenter = koinInject<RankingPresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val tournaments by store.tournaments.collectAsState()
    val tournament = tournaments.firstOrNull { it.id == tournamentId }

    var isLoading by remember { mutableStateOf(true) }
    // True while the tables of the selected round load in the background. It only keeps the progress bar on.
    var isLoadingRest by remember { mutableStateOf(false) }
    var restJob by remember { mutableStateOf<Job?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rounds by remember { mutableStateOf<List<TournamentRound>>(emptyList()) }
    var allTables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var selectedRoundId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }
    var selectedTableId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }
    var tableEditors by remember { mutableStateOf<Map<Int, TableManagerEditorState>>(emptyMap()) }
    var playerNamesById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var pendingAction by remember { mutableStateOf<RoundEditorAction?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    var restoreResetFocus by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<TableSaveConflict?>(null) }
    var conflictTableId by remember { mutableStateOf<Int?>(null) }
    var conflictChoices by remember { mutableStateOf<Map<String, ConflictChoice>>(emptyMap()) }

    val saveFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val roundFocusRequester = remember { FocusRequester() }
    val scoreFocusRequester = remember { FocusRequester() }
    val selectedRoundTables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val hasUnsavedChanges = tableEditors.values.any { it.hasUnsavedChanges }
    val roleLabel = adminStatus?.let { if (it.isAdmin) "Admin" else "Editor" }

    /**
     * Loads the table summaries of the round, then selects a table.
     * The details of all tables load in the background in one request. The selectors work as soon as the summaries arrive.
     */
    suspend fun loadRound(
        roundId: Int,
        force: Boolean = false,
        firstTableId: Int? = null,
        summaries: List<TournamentTable>? = null,
    ) {
        restJob?.cancel()
        restJob = null
        isLoadingRest = false
        isLoading = true
        errorMessage = null
        tableEditors = emptyMap()
        val roundTables = if (summaries != null) {
            summaries
        } else {
            when (val result = tablesPresenter.loadTables(tournamentId, roundId, force)) {
                is AppResult.Failure -> {
                    errorMessage = result.error.toUiMessage()
                    isLoading = false
                    return
                }
                is AppResult.Success -> result.value
            }
        }.sortedBy { it.tableId }
        val tableIds = roundTables.map { it.tableId }
        val firstId = firstTableId?.takeIf { it in tableIds }
            ?: selectedTableId?.takeIf { it in tableIds }
            ?: roundTables.firstOrNull {
                tableCompletionStatus(it).let { s -> s == CompletionStatus.Empty || s == CompletionStatus.InProgress }
            }?.tableId
            ?: tableIds.firstOrNull()
        selectedTableId = firstId
        if (firstId == null) {
            isLoading = false
            return
        }
        isLoadingRest = true
        isLoading = false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            try {
                fun add(tableId: Int, detail: Pair<TableState, List<TableHand>>) {
                    // A table that is already in the map has newer data, for example after a save.
                    if (tableId !in tableEditors) {
                        tableEditors = tableEditors + (tableId to TableManagerEditorState.from(detail.first, detail.second))
                    }
                }
                // All tables of the round come in one request.
                when (val batch = tablePresenter.loadRoundTablesWithHands(tournamentId, roundId, force)) {
                    is AppResult.Success -> if (selectedRoundId == roundId) {
                        batch.value.forEach { (table, hands) -> add(table.tableId, table to hands) }
                    }
                    is AppResult.Failure -> {
                        // Fall back to one request per table, for example if the backend is older.
                        for (tableId in tableIds) {
                            val detail = tablePresenter.loadTableWithHands(tournamentId, roundId, tableId, force)
                            if (selectedRoundId != roundId) return@launch
                            when (detail) {
                                is AppResult.Success -> add(tableId, detail.value)
                                is AppResult.Failure -> errorMessage = detail.error.toUiMessage()
                            }
                        }
                    }
                }
            } finally {
                if (restJob === self) {
                    restJob = null
                    isLoadingRest = false
                }
            }
        }
        restJob = job
        job.start()
    }

    suspend fun refresh(force: Boolean = false) {
        isLoading = true
        errorMessage = null
        val basePlayers = when (val result = tablesPresenter.loadBasePlayers(force)) {
            is AppResult.Success -> result.value.associateBy { it.emaId }
            is AppResult.Failure -> emptyMap()
        }
        when (val result = tablesPresenter.loadPlayers(tournamentId, force)) {
            is AppResult.Success -> {
                playerNamesById = result.value.associate { slot ->
                    val name = slot.assignedEmaId?.let(basePlayers::get)?.displayName
                        ?: slot.nonMember?.displayName
                    slot.id to (name ?: "Player ${slot.id}")
                }
            }
            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
        }
        val newRounds = when (val result = tablesPresenter.loadRounds(tournamentId, force)) {
            is AppResult.Success -> result.value.sortedBy { it.roundId }
            is AppResult.Failure -> {
                errorMessage = result.error.toUiMessage()
                isLoading = false
                return
            }
        }
        rounds = newRounds
        val allTablesResult = tablesPresenter.loadTables(tournamentId, null, force)
        allTables = when (allTablesResult) {
            is AppResult.Success -> allTablesResult.value.sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
            is AppResult.Failure -> {
                errorMessage = allTablesResult.error.toUiMessage()
                emptyList()
            }
        }
        // On the first open, start at the first table that is not completed. A later refresh keeps the selection.
        val firstToOpen = firstTournamentTableToOpen(allTables)
        val isFirstOpen = selectedRoundId == null
        val nextRound = selectedRoundId?.takeIf { id -> newRounds.any { it.roundId == id } }
            ?: firstToOpen?.roundId
            ?: newRounds.firstOrNull()?.roundId
        selectedRoundId = nextRound
        if (nextRound == null) {
            tableEditors = emptyMap()
            isLoading = false
            return
        }
        loadRound(
            roundId = nextRound,
            force = force,
            firstTableId = firstToOpen?.takeIf { isFirstOpen && it.roundId == nextRound }?.tableId,
            summaries = if (allTablesResult is AppResult.Success) allTables.filter { it.roundId == nextRound } else null,
        )
    }

    LaunchedEffect(tournamentId) { refresh() }
    DisposableEffect(Unit) { onDispose { restJob?.cancel() } }
    val selectedTableLoaded = selectedTableId in tableEditors
    LaunchedEffect(selectedRoundId, selectedTableId, isLoading, selectedTableLoaded) {
        if (isLoading || selectedTableId == null || !selectedTableLoaded) return@LaunchedEffect
        // Wait for the first layout pass. Then the first score field takes the focus, so the keyboard flow can start.
        withFrameNanos { }
        runCatching { scoreFocusRequester.requestFocus() }
    }

    suspend fun saveTables(tableIds: Collection<Int>): Boolean {
        val current = tableEditors.filterKeys { it in tableIds }.toList()
        for ((tableId, editor) in current) {
            val tablePatch = editor.buildApplicationTablePatch()
            val handPatches = editor.buildHandPatches().toMap()
            if (tablePatch.isEmpty() && handPatches.isEmpty()) continue
            isLoading = true
            when (val result = tablePresenter.saveTableState(
                tournamentId = tournamentId,
                roundId = editor.roundId,
                tableId = tableId,
                expectedVersion = editor.version,
                tablePatch = tablePatch,
                handPatches = handPatches,
            )) {
                is AppResult.Success -> {
                    val saved = TableManagerEditorState.from(result.value.first, result.value.second)
                    tableEditors = tableEditors + (tableId to saved)
                    allTables = allTables.map { summary ->
                        if (summary.roundId == saved.roundId && summary.tableId == tableId) saved.applyTo(summary) else summary
                    }
                }
                is AppResult.Failure -> {
                    val saveError = result.error as? AppError.Conflict
                    val serverTable = saveError?.currentTable
                    if (serverTable != null) {
                        conflictTableId = tableId
                        conflict = TableSaveConflict(
                            baseTable = editor.baseTableSnapshot(),
                            baseHands = editor.baseHandsSnapshot(),
                            serverTable = serverTable,
                            serverHands = saveError.currentHands,
                            tablePatch = tablePatch,
                            handPatches = handPatches,
                        )
                        conflictChoices = emptyMap()
                    } else {
                        errorMessage = result.error.toUiMessage()
                    }
                    isLoading = false
                    return false
                }
            }
            isLoading = false
        }
        return true
    }

    suspend fun resolveConflict() {
        val currentConflict = conflict ?: return
        val tableId = conflictTableId ?: return
        isLoading = true
        val resolvedTablePatch = currentConflict.resolvedTablePatch(conflictChoices)
        val resolvedHandPatches = currentConflict.resolvedHandPatches(conflictChoices)
        val result = tablePresenter.saveTableState(
            tournamentId = tournamentId,
            roundId = currentConflict.serverTable.roundId,
            tableId = tableId,
            expectedVersion = currentConflict.serverTable.version,
            tablePatch = resolvedTablePatch,
            handPatches = resolvedHandPatches,
        )
        when (result) {
            is AppResult.Success -> {
                val saved = TableManagerEditorState.from(result.value.first, result.value.second)
                tableEditors = tableEditors + (tableId to saved)
                allTables = allTables.map { summary ->
                    if (summary.roundId == saved.roundId && summary.tableId == tableId) saved.applyTo(summary) else summary
                }
                conflict = null
                conflictTableId = null
                conflictChoices = emptyMap()
            }
            is AppResult.Failure -> {
                val next = result.error as? AppError.Conflict
                val nextTable = next?.currentTable
                if (nextTable != null) {
                    conflict = TableSaveConflict(
                        baseTable = currentConflict.serverTable,
                        baseHands = currentConflict.serverHands,
                        serverTable = nextTable,
                        serverHands = next.currentHands,
                        tablePatch = resolvedTablePatch,
                        handPatches = resolvedHandPatches,
                    )
                    conflictChoices = emptyMap()
                } else {
                    errorMessage = result.error.toUiMessage()
                }
            }
        }
        isLoading = false
    }

    suspend fun exportResults() {
        if (isLoading || isLoadingRest) return
        isLoading = true
        errorMessage = null
        when (val result = rankingPresenter.load(tournamentId)) {
            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            is AppResult.Success -> {
                val snapshot = result.value
                officialResultsExportError(snapshot.rankingTables)?.let {
                    errorMessage = it
                    isLoading = false
                    return
                }
                if (snapshot.tournamentPlayers.any { !it.isAssigned }) {
                    errorMessage = "Assign an EMA player or non-member to every tournament slot before export."
                    isLoading = false
                    return
                }
                when (val report = rankingPresenter.generateEmaReport(tournamentId, snapshot.rankings.players)) {
                    is AppResult.Failure -> errorMessage = report.error.toUiMessage()
                    is AppResult.Success -> saveBinaryFile(
                        fileName = "${tournament?.name?.replace(Regex("[^A-Za-z0-9._ -]"), "_") ?: "tournament"}.xlsx",
                        content = report.value,
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    )
                }
            }
        }
        isLoading = false
    }

    fun perform(action: RoundEditorAction) {
        // The unsaved changes dialog is done once its action runs, also after Save.
        pendingAction = null
        when (action) {
            RoundEditorAction.Back -> navController.popBackStack()
            is RoundEditorAction.SelectRound -> scope.launch { selectedRoundId = action.roundId; loadRound(action.roundId) }
            is RoundEditorAction.SelectTable -> selectedTableId = action.tableId
            is RoundEditorAction.NextTable -> selectedTableId = nextTableToOpen(
                tableIds = selectedRoundTables.map { it.tableId },
                currentId = action.fromTableId,
                needsData = { id ->
                    tableEditors[id]?.completionStatus.let {
                        it == CompletionStatus.Empty || it == CompletionStatus.InProgress
                    }
                },
            )?.takeIf { it in tableEditors } ?: selectedTableId
            RoundEditorAction.Players -> navController.navigate(PlayersRoute(tournamentId))
            RoundEditorAction.Teams -> navController.navigate(TeamsRoute(tournamentId))
            RoundEditorAction.Rankings -> openRankings(navController, tournamentId)
            RoundEditorAction.Timer -> openTimer(navController, allTables.maxOfOrNull { it.roundId }?.plus(1) ?: 1)
            RoundEditorAction.Export -> scope.launch { exportResults() }
        }
    }

    fun requestAction(action: RoundEditorAction) {
        if (isLoading) return
        // Changes stay in each table editor while the user moves between tables of the round.
        val keepsEditors = action is RoundEditorAction.SelectTable || action is RoundEditorAction.NextTable
        if (hasUnsavedChanges && !keepsEditors) pendingAction = action else perform(action)
    }

    /** Saves the given tables, or all tables with changes. Table saves are independent, so one failure keeps the other data. */
    fun requestSave(action: RoundEditorAction? = null, tableIds: List<Int>? = null) {
        val targets = tableIds ?: tableEditors.filterValues { it.hasUnsavedChanges }.keys.toList()
        scope.launch { if (saveTables(targets)) action?.let(::perform) }
    }

    fun discardAll() {
        tableEditors.values.forEach { it.discard() }
    }

    pendingAction?.let { action ->
        UnsavedChangesDialog(
            isSaving = isLoading,
            onSave = { requestSave(action) },
            onDiscard = { pendingAction = null; discardAll(); perform(action) },
            onCancel = { pendingAction = null },
        )
    }
    if (showResetConfirmation && selectedRoundId != null && selectedTableId != null) {
        ResetTableDialog(
            roundId = selectedRoundId!!,
            tableId = selectedTableId!!,
            isResetting = isLoading,
            onConfirm = {
                scope.launch {
                    isLoading = true
                    when (val result = tablePresenter.resetTable(tournamentId, selectedRoundId!!, selectedTableId!!)) {
                        is AppResult.Success -> { showResetConfirmation = false; restoreResetFocus = true; loadRound(selectedRoundId!!, true) }
                        is AppResult.Failure -> { errorMessage = result.error.toUiMessage(); showResetConfirmation = false }
                    }
                    isLoading = false
                }
            },
            onCancel = { showResetConfirmation = false; restoreResetFocus = true },
        )
    }
    LaunchedEffect(showResetConfirmation, restoreResetFocus, isLoading) {
        if (!showResetConfirmation && restoreResetFocus && !isLoading) {
            restoreResetFocus = false
            resetFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(restoreSaveFocus, isLoading) {
        if (restoreSaveFocus && !isLoading) {
            restoreSaveFocus = false
            saveFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(conflict, isLoading) {
        if (conflict?.fields?.isEmpty() == true && !isLoading) resolveConflict()
    }
    conflict?.let { currentConflict ->
        TableSaveConflictDialog(
            conflict = currentConflict,
            choices = conflictChoices,
            isSaving = isLoading,
            onChoice = { id, choice -> conflictChoices = conflictChoices + (id to choice) },
            onConfirm = { scope.launch { resolveConflict() } },
            onCancel = { conflict = null; conflictTableId = null; conflictChoices = emptyMap(); restoreSaveFocus = true },
        )
    }
    errorMessage?.let { message -> AppErrorDialog(message) { errorMessage = null } }

    AppScaffold(
        title = tournament?.name?.ifBlank { "Tournament" } ?: "Tournament",
        titleAction = {
            TournamentEditTitleAction(
                tournamentId = tournamentId,
                onDeleted = {
                    navController.navigate(TournamentsRoute) {
                        popUpTo(TournamentsRoute) { inclusive = true }
                    }
                },
            )
        },
        isLoading = isLoading,
        showProgress = isLoading || isLoadingRest,
        onBack = { requestAction(RoundEditorAction.Back) },
        leadingActions = {
            ProfileInfo(profile, roleLabel)
            AppTopBarLeadingActions(
                showThemeToggle = true,
                onTimer = { requestAction(RoundEditorAction.Timer) },
                onRanking = { requestAction(RoundEditorAction.Rankings) },
            )
        },
        actions = {
            val showReset = adminStatus?.isAdmin == true && selectedRoundId != null && selectedTableId != null
            if (tournament?.isTeams == true) {
                AppTopBarButton(
                    text = "Teams",
                    icon = Icons.Default.Groups,
                    onClick = { requestAction(RoundEditorAction.Teams) },
                    enabled = !isLoading,
                )
            }
            AppTopBarButton(
                text = "Players",
                icon = Icons.Default.People,
                onClick = { requestAction(RoundEditorAction.Players) },
                enabled = !isLoading,
                focusRequester = if (showReset) null else resetFocusRequester,
            )
            AppTopBarButton(
                text = "EMA report",
                icon = Icons.Outlined.Assessment,
                onClick = { requestAction(RoundEditorAction.Export) },
                enabled = !isLoading && !isLoadingRest,
            )
            if (showReset) {
                AppTopBarButton(
                    text = "Reset table",
                    icon = Icons.Default.RestartAlt,
                    onClick = { showResetConfirmation = true },
                    enabled = !isLoading && !isLoadingRest,
                    focusRequester = resetFocusRequester,
                    textColor = MaterialTheme.colorScheme.errorContainer,
                )
            }
        },
    ) {
        CompositionLocalProvider(
            LocalHandStatOptions provides HandStatOptions(
                countBestHands = tournament?.countBestHands ?: true,
                countChickenHands = tournament?.countChickenHands ?: true,
            ),
        ) {
        TournamentContent(
            rounds = rounds,
            allTables = allTables,
            tableEditors = tableEditors,
            selectedRoundId = selectedRoundId,
            selectedTableId = selectedTableId,
            enabled = !isLoading,
            playerNamesById = playerNamesById,
            roundFocusRequester = roundFocusRequester,
            scoreFocusRequester = scoreFocusRequester,
            saveFocusRequester = saveFocusRequester,
            onSelectRound = { requestAction(RoundEditorAction.SelectRound(it)) },
            onSelectTable = { requestAction(RoundEditorAction.SelectTable(it)) },
            onRanking = { requestAction(RoundEditorAction.Rankings) },
            onSave = { requestSave(tableIds = listOf(it)) },
            onSaveAndNext = { requestSave(RoundEditorAction.NextTable(it), listOf(it)) },
        )
        }
    }
}

/** The stateless body of the tournament screen: the round and table strip, and the editor of the selected table. */
@Composable
internal fun TournamentContent(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    tableEditors: Map<Int, TableManagerEditorState>,
    selectedRoundId: Int?,
    selectedTableId: Int?,
    enabled: Boolean,
    playerNamesById: Map<Int, String>,
    roundFocusRequester: FocusRequester,
    scoreFocusRequester: FocusRequester,
    saveFocusRequester: FocusRequester,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
    onRanking: () -> Unit,
    onSave: (Int) -> Unit,
    onSaveAndNext: (Int) -> Unit,
) {
    val selectedEditor = selectedTableId?.let { tableEditors[it] }
    val roundTableCount = allTables.count { it.roundId == selectedRoundId }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RoundTableStrip(
            rounds = rounds,
            allTables = allTables,
            tableEditors = tableEditors,
            selectedRoundId = selectedRoundId,
            selectedTableId = selectedTableId,
            enabled = enabled,
            roundFocusRequester = roundFocusRequester,
            onSelectRound = onSelectRound,
            onSelectTable = onSelectTable,
            onRanking = onRanking,
        )
        if (selectedEditor != null) {
            Text(
                text = "Round ${selectedEditor.roundId} • Table ${selectedEditor.tableId}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedEditor != null) {
                val tableId = selectedEditor.tableId
                val accent = MaterialTheme.colorScheme.tertiary
                val scrollState = rememberScrollState()
                val stickyHost = remember(scrollState) { StickyHeaderHost(scrollState) }
                Column(
                    modifier = Modifier
                        .widthIn(max = 1600.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onGloballyPositioned { stickyHost.viewportTop = it.positionInRoot().y }
                        .verticalScroll(scrollState)
                        .padding(bottom = 96.dp),
                ) {
                    CompositionLocalProvider(LocalStickyHeaderHost provides stickyHost) {
                    TableEditorPanel(
                        editor = selectedEditor,
                        enabled = enabled,
                        playerNamesById = playerNamesById,
                        scoreFocusRequester = scoreFocusRequester,
                        otherTablesBestHandScores = allTables
                            .filterNot { it.roundId == selectedEditor.roundId && it.tableId == tableId }
                            .flatMap { it.bestHandScores },
                    )
                    }
                }
                if (selectedEditor.hasUnsavedChanges) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { onSave(tableId) },
                            modifier = Modifier.focusRequester(saveFocusRequester),
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ) { Text("Save") }
                        if (roundTableCount > 1) {
                            ExtendedFloatingActionButton(
                                onClick = { onSaveAndNext(tableId) },
                                containerColor = accent,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ) { Text("Save and next table") }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun TournamentContentPreview() {
    MtsTheme(useDarkTheme = false) {
        val editor = remember { previewTableManagerEditorState() }
        val tables = listOf(1, 2, 3).map { id ->
            TournamentTable(
                roundId = editor.roundId,
                tableId = id,
                playerIds = listOf(101, 102, 103, 104),
                isCompleted = false,
                useTotalsOnly = false,
                usePointsCalculation = true,
                hasProgress = id == editor.tableId,
            )
        }
        AppScaffold(title = "Preview Tournament") {
            TournamentContent(
                rounds = listOf(TournamentRound(1), TournamentRound(editor.roundId)),
                allTables = tables,
                tableEditors = mapOf(editor.tableId to editor),
                selectedRoundId = editor.roundId,
                selectedTableId = editor.tableId,
                enabled = true,
                playerNamesById = previewTablePlayerNamesById(),
                roundFocusRequester = remember { FocusRequester() },
                scoreFocusRequester = remember { FocusRequester() },
                saveFocusRequester = remember { FocusRequester() },
                onSelectRound = {},
                onSelectTable = {},
                onRanking = {},
                onSave = {},
                onSaveAndNext = {},
            )
        }
    }
}

/**
 * Returns the table to open after [currentId]. It prefers the next table that needs data and wraps around.
 * When every table has data, it returns the table after [currentId], or null for the last table.
 */
internal fun nextTableToOpen(tableIds: List<Int>, currentId: Int, needsData: (Int) -> Boolean): Int? {
    val index = tableIds.indexOf(currentId)
    if (index < 0) return tableIds.firstOrNull()
    val following = tableIds.drop(index + 1) + tableIds.take(index)
    return following.firstOrNull(needsData) ?: tableIds.getOrNull(index + 1)
}

/** One slim row: round picker, one chip per table, and a ranking shortcut once the round has all data. */
@Composable
private fun RoundTableStrip(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    tableEditors: Map<Int, TableManagerEditorState>,
    selectedRoundId: Int?,
    selectedTableId: Int?,
    enabled: Boolean,
    roundFocusRequester: FocusRequester,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
    onRanking: () -> Unit,
) {
    val tables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val roundIndex = rounds.indexOfFirst { it.roundId == selectedRoundId }
    val chipsState = rememberLazyListState()
    var roundMenuOpen by remember { mutableStateOf(false) }
    val roundStatus = roundCompletionStatus(tables)
    val roundIsFinished = roundStatus == CompletionStatus.Completed || roundStatus == CompletionStatus.Manual
    Card(modifier = Modifier.fillMaxWidth().appFocusGroup()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            FocusedIconButton(
                onClick = { rounds.getOrNull(roundIndex - 1)?.let { onSelectRound(it.roundId) } },
                enabled = enabled && roundIndex > 0,
            ) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous round") }
            Box {
                FocusedOutlinedButton(
                    onClick = { roundMenuOpen = true },
                    enabled = enabled,
                    focusRequester = roundFocusRequester,
                ) {
                    Text("Round ${selectedRoundId ?: "-"}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    StatusIconWithTooltip(roundStatus)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = roundMenuOpen, onDismissRequest = { roundMenuOpen = false }) {
                    rounds.forEach { round ->
                        DropdownMenuItem(
                            text = { Text("Round ${round.roundId}") },
                            trailingIcon = {
                                StatusIconWithTooltip(roundCompletionStatus(allTables.filter { it.roundId == round.roundId }))
                            },
                            onClick = {
                                roundMenuOpen = false
                                onSelectRound(round.roundId)
                            },
                        )
                    }
                }
            }
            FocusedIconButton(
                onClick = { rounds.getOrNull(roundIndex + 1)?.let { onSelectRound(it.roundId) } },
                enabled = enabled && roundIndex in 0 until rounds.lastIndex,
            ) { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next round") }
            }
            Box(Modifier.height(32.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            LazyRow(
                state = chipsState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(tables, key = { _, table -> table.tableId }) { index, table ->
                    val editor = tableEditors[table.tableId]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (index > 0) {
                            Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        TableChip(
                            label = "${table.tableId}",
                            status = editor?.completionStatus ?: tableCompletionStatus(table),
                            hasUnsavedChanges = editor?.hasUnsavedChanges == true,
                            selected = table.tableId == selectedTableId,
                            // A table that is not loaded yet stays disabled until its data arrives.
                            enabled = enabled && editor != null,
                            onClick = { onSelectTable(table.tableId) },
                            loaded = editor != null,
                        )
                    }
                }
            }
            if (roundIsFinished) {
                FocusedButton(onClick = onRanking, enabled = enabled) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ranking")
                }
            }
        }
    }
}

@Composable
private fun TableChip(
    label: String,
    status: CompletionStatus,
    hasUnsavedChanges: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    loaded: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier, interactionSource = interactionSource) {
        Surface(
            modifier = Modifier
                .alpha(if (loaded) 1f else 0.38f)
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
                .activateOnEnter(enabled = enabled, onClick = onClick),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                StatusIconWithTooltip(status = status)
                if (hasUnsavedChanges) {
                    // Marks a table with changes that are not saved yet.
                    Box(
                        Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    )
                }
            }
        }
    }
}
