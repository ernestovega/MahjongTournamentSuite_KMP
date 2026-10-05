package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.key.type
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
import com.etologic.mahjongtournamentsuite.presentation.components.ManualScoreTotalConfirmationDialog
import com.etologic.mahjongtournamentsuite.presentation.components.ResetTableDialog
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed class RoundEditorAction {
    data object Back : RoundEditorAction()
    data class SelectRound(val roundId: Int) : RoundEditorAction()
    data class SelectTable(val tableId: Int) : RoundEditorAction()
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rounds by remember { mutableStateOf<List<TournamentRound>>(emptyList()) }
    var allTables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var selectedRoundId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }
    var selectedTableId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }
    var tableEditors by remember { mutableStateOf<Map<Int, TableManagerEditorState>>(emptyMap()) }
    var playerNamesById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var handsDialogTableId by remember { mutableStateOf<Int?>(null) }
    var restoreHandsFocusTableId by remember { mutableStateOf<Int?>(null) }
    var pendingAction by remember { mutableStateOf<RoundEditorAction?>(null) }
    var scoreTotalToConfirm by remember { mutableStateOf<Long?>(null) }
    var actionAfterScoreConfirmation by remember { mutableStateOf<RoundEditorAction?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    var restoreResetFocus by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<TableSaveConflict?>(null) }
    var conflictTableId by remember { mutableStateOf<Int?>(null) }
    var conflictChoices by remember { mutableStateOf<Map<String, ConflictChoice>>(emptyMap()) }

    val saveFocusRequester = remember { FocusRequester() }
    val resetFocusRequester = remember { FocusRequester() }
    val roundFocusRequester = remember { FocusRequester() }
    val exportFocusRequester = remember { FocusRequester() }
    val cardFocusRequesters = remember(selectedRoundId, tableEditors.keys.toList()) {
        tableEditors.keys.associateWith { FocusRequester() }
    }
    val editHandsFocusRequesters = remember(selectedRoundId, tableEditors.keys.toList()) {
        tableEditors.keys.associateWith { FocusRequester() }
    }
    val selectedRoundTables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val tableListState = rememberLazyListState()
    val hasUnsavedChanges = tableEditors.values.any { it.hasUnsavedChanges }
    val roleLabel = adminStatus?.let { if (it.isAdmin) "Admin" else "Editor" }

    suspend fun loadRound(roundId: Int, force: Boolean = false) {
        isLoading = true
        errorMessage = null
        when (val result = tablesPresenter.loadTables(tournamentId, roundId, force)) {
            is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            is AppResult.Success -> {
                val loaded = linkedMapOf<Int, TableManagerEditorState>()
                result.value.sortedBy { it.tableId }.forEach { summary ->
                    when (val detail = tablePresenter.loadTableWithHands(
                        tournamentId = tournamentId,
                        roundId = roundId,
                        tableId = summary.tableId,
                        forceRefresh = force,
                    )) {
                        is AppResult.Success -> loaded[summary.tableId] =
                            TableManagerEditorState.from(detail.value.first, detail.value.second)
                        is AppResult.Failure -> errorMessage = detail.error.toUiMessage()
                    }
                }
                tableEditors = loaded
                selectedTableId = selectedTableId?.takeIf { it in loaded } ?: loaded.keys.firstOrNull()
            }
        }
        isLoading = false
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
        val nextRound = selectedRoundId?.takeIf { id -> newRounds.any { it.roundId == id } }
            ?: firstTournamentTableToOpen(allTables)?.roundId
            ?: newRounds.firstOrNull()?.roundId
        selectedRoundId = nextRound
        if (nextRound == null) {
            tableEditors = emptyMap()
            isLoading = false
            return
        }
        loadRound(nextRound, force)
    }

    LaunchedEffect(tournamentId) { refresh() }
    LaunchedEffect(selectedRoundId, selectedTableId, isLoading, tableEditors.keys.toList()) {
        val tableId = selectedTableId ?: return@LaunchedEffect
        if (isLoading) return@LaunchedEffect
        val index = selectedRoundTables.indexOfFirst { it.tableId == tableId }
        if (index >= 0) {
            tableListState.animateScrollToItem(index)
            cardFocusRequesters[tableId]?.requestFocus()
        }
    }

    suspend fun saveAll(): Boolean {
        val current = tableEditors.toList()
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
                    tableEditors = tableEditors + (tableId to TableManagerEditorState.from(result.value.first, result.value.second))
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
                tableEditors = tableEditors + (tableId to TableManagerEditorState.from(result.value.first, result.value.second))
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
        if (isLoading) return
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
        when (action) {
            RoundEditorAction.Back -> navController.popBackStack()
            is RoundEditorAction.SelectRound -> scope.launch { selectedRoundId = action.roundId; loadRound(action.roundId) }
            is RoundEditorAction.SelectTable -> selectedTableId = action.tableId
            RoundEditorAction.Players -> navController.navigate(PlayersRoute(tournamentId))
            RoundEditorAction.Teams -> navController.navigate(TeamsRoute(tournamentId))
            RoundEditorAction.Rankings -> openRankings(navController, tournamentId)
            RoundEditorAction.Timer -> openTimer(navController, allTables.maxOfOrNull { it.roundId }?.plus(1) ?: 1)
            RoundEditorAction.Export -> scope.launch { exportResults() }
        }
    }

    fun requestAction(action: RoundEditorAction) {
        if (isLoading) return
        if (hasUnsavedChanges) pendingAction = action else perform(action)
    }

    fun requestSave(action: RoundEditorAction? = null) {
        val total = tableEditors.values.mapNotNull { it.nonZeroManualScoreTotal }.sum().takeIf { it != 0L }
        if (total != null) {
            actionAfterScoreConfirmation = action
            scoreTotalToConfirm = total
        } else {
            scope.launch { if (saveAll()) action?.let(::perform) }
        }
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
    scoreTotalToConfirm?.let { total ->
        ManualScoreTotalConfirmationDialog(
            total = total,
            onConfirm = {
                val action = actionAfterScoreConfirmation
                actionAfterScoreConfirmation = null
                scoreTotalToConfirm = null
                scope.launch { if (saveAll()) action?.let(::perform) }
            },
            onCancel = {
                restoreSaveFocus = actionAfterScoreConfirmation == null
                actionAfterScoreConfirmation = null
                scoreTotalToConfirm = null
            },
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
    LaunchedEffect(scoreTotalToConfirm, restoreSaveFocus, isLoading) {
        if (scoreTotalToConfirm == null && restoreSaveFocus && !isLoading) {
            restoreSaveFocus = false
            saveFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(handsDialogTableId, restoreHandsFocusTableId) {
        val tableId = restoreHandsFocusTableId ?: return@LaunchedEffect
        if (handsDialogTableId == null) {
            restoreHandsFocusTableId = null
            editHandsFocusRequesters[tableId]?.requestFocus()
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
    handsDialogTableId?.let { tableId ->
        tableEditors[tableId]?.let { editor ->
            TableHandsDialog(
                editor = editor,
                enabled = !isLoading,
                playerNamesById = playerNamesById,
                onDismiss = {
                    handsDialogTableId = null
                    restoreHandsFocusTableId = tableId
                },
            )
        }
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
        onBack = { requestAction(RoundEditorAction.Back) },
        leadingActions = {
            ProfileInfo(profile, roleLabel)
            AppTopBarLeadingActions(
                onTimer = { requestAction(RoundEditorAction.Timer) },
                onRanking = { requestAction(RoundEditorAction.Rankings) },
            )
        },
        actions = {
            AppTopBarActions(
                onTeams = if (tournament?.isTeams == true) ({ requestAction(RoundEditorAction.Teams) }) else null,
                onPlayers = { requestAction(RoundEditorAction.Players) },
                onEmaReport = { requestAction(RoundEditorAction.Export) },
                exportFocusRequester = exportFocusRequester,
            )
            if (adminStatus?.isAdmin == true && selectedRoundId != null && selectedTableId != null) {
                AppTopBarButton(
                    text = "Reset table",
                    icon = Icons.Default.RestartAlt,
                    onClick = { showResetConfirmation = true },
                    enabled = !isLoading,
                    focusRequester = resetFocusRequester,
                    textColor = MaterialTheme.colorScheme.error,
                )
            }
        },
        floatingActionButton = {
            if (hasUnsavedChanges) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton(
                        onClick = ::discardAll,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) { Text("Discard") }
                    com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton(
                        onClick = { requestSave() },
                        enabled = !isLoading,
                        focusRequester = saveFocusRequester,
                    ) { Text("Save") }
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RoundEditorSidebar(
                rounds = rounds,
                allTables = allTables,
                selectedRoundId = selectedRoundId,
                selectedTableId = selectedTableId,
                enabled = !isLoading,
                roundFocusRequester = roundFocusRequester,
                onSelectRound = { requestAction(RoundEditorAction.SelectRound(it)) },
                onSelectTable = { requestAction(RoundEditorAction.SelectTable(it)) },
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyColumnWithScrollbar(
                    state = tableListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (hasUnsavedChanges) 88.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(selectedRoundTables, key = { it.tableId }) { summary ->
                        tableEditors[summary.tableId]?.let { editor ->
                            TableSummaryCard(
                                editor = editor,
                                enabled = !isLoading,
                                playerNamesById = playerNamesById,
                                cardFocusRequester = cardFocusRequesters[summary.tableId],
                                editHandsFocusRequester = editHandsFocusRequesters[summary.tableId],
                                onEditHands = { handsDialogTableId = summary.tableId },
                                onFocused = { selectedTableId = summary.tableId },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundEditorSidebar(
    rounds: List<TournamentRound>,
    allTables: List<TournamentTable>,
    selectedRoundId: Int?,
    selectedTableId: Int?,
    enabled: Boolean,
    roundFocusRequester: FocusRequester,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
) {
    val tables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val roundsListState = rememberLazyListState()
    val tablesListState = rememberLazyListState()
    Card(modifier = Modifier.fillMaxHeight().widthIn(min = 280.dp, max = 420.dp).appFocusGroup()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rounds", style = MaterialTheme.typography.titleMedium)
                LazyColumnWithScrollbar(
                    state = roundsListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rounds, key = { it.roundId }) { round ->
                        SidebarEntry(
                            label = "Round ${round.roundId}",
                            status = roundCompletionStatus(allTables.filter { it.roundId == round.roundId }),
                            selected = round.roundId == selectedRoundId,
                            enabled = enabled,
                            focusRequester = if (round.roundId == selectedRoundId) roundFocusRequester else null,
                            onClick = { onSelectRound(round.roundId) },
                        )
                    }
                }
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tables", style = MaterialTheme.typography.titleMedium)
                LazyColumnWithScrollbar(
                    state = tablesListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tables, key = { it.tableId }) { table ->
                        SidebarEntry(
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

@Composable
private fun SidebarEntry(
    label: String,
    status: CompletionStatus,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FocusHighlightContainer(modifier = Modifier.fillMaxWidth(), interactionSource = interactionSource) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
                .activateOnEnter(enabled = enabled, onClick = onClick),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current)
                Icon(
                    imageVector = status.icon,
                    contentDescription = status.label,
                    tint = if (selected) LocalContentColor.current else status.color,
                )
            }
        }
    }
}
