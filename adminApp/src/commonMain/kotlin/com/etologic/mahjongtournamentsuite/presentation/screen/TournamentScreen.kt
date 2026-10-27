package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions
import com.etologic.mahjongtournamentsuite.presentation.components.HandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHandStatOptions
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.TournamentEditTitleAction
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentOptionalInt
import com.etologic.mahjongtournamentsuite.presentation.components.requestFocusIfAttached
import com.etologic.mahjongtournamentsuite.presentation.platform.openRankings
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.RankingPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.sync.ActivityTracker
import com.etologic.mahjongtournamentsuite.presentation.sync.PollSchedule
import com.etologic.mahjongtournamentsuite.presentation.sync.TableSyncPoller
import com.etologic.mahjongtournamentsuite.presentation.sync.trackActivity
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.dataStatus
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import com.etologic.mahjongtournamentsuite.presentation.util.tournamentDataRevision
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.assign_all_before_export
import mahjongtournamentsuite.adminapp.generated.resources.ema_report
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.role_admin
import mahjongtournamentsuite.adminapp.generated.resources.role_editor
import mahjongtournamentsuite.adminapp.generated.resources.save_ema_report
import mahjongtournamentsuite.adminapp.generated.resources.scope_tournament
import mahjongtournamentsuite.adminapp.generated.resources.statistics
import mahjongtournamentsuite.adminapp.generated.resources.teams
import org.jetbrains.compose.resources.stringResource
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
    data object Statistics : RoundEditorAction()
    data object Timer : RoundEditorAction()
    data object Export : RoundEditorAction()
}

@Composable
fun TournamentScreen(
    navController: NavHostController,
    tournamentId: String,
    // Tests replace these to control the clock and the polling pace.
    activity: ActivityTracker = remember { ActivityTracker() },
    pollSchedule: PollSchedule = remember { PollSchedule() },
) {
    val tablesPresenter = koinInject<TablesPresenter>()
    val tablePresenter = koinInject<TableManagerPresenter>()
    val tournamentsPresenter = koinInject<TournamentsPresenter>()
    val rankingPresenter = koinInject<RankingPresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val profile by store.profile.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    val tournament = tournaments.firstOrNull { it.id == tournamentId }

    var selectedRoundId by rememberPersistentOptionalInt("round", tournamentId)
    var selectedTableId by rememberPersistentOptionalInt("table", tournamentId)
    // The tables and the rules that keep them in step with the server live in this object.
    val sync = remember(tournamentId) {
        RoundEditorSync(
            tournamentId, tablePresenter, tablesPresenter, { selectedRoundId }, { selectedTableId },
            reloadTournaments = {
                // Another user changed the tournament settings. The screen reads them from the store.
                val result = tournamentsPresenter.loadTournaments(force = true)
                if (result is AppResult.Success) store.upsertTournaments(result.value)
                result is AppResult.Success
            },
        )
    }
    var isLoading by sync::isLoading
    // True while the tables of the selected round load in the background. It only keeps the progress bar on.
    var isLoadingRest by sync::isLoadingRest
    var restJob by remember { mutableStateOf<Job?>(null) }
    var errorMessage by sync::errorMessage
    var rounds by remember { mutableStateOf<List<TournamentRound>>(emptyList()) }
    var allTables by sync::allTables
    var tableEditors by sync::tableEditors
    var playerNamesById by sync::playerNamesById
    var pendingAction by remember { mutableStateOf<RoundEditorAction?>(null) }
    var restoreSaveFocus by remember { mutableStateOf(false) }
    val poller = remember(activity, pollSchedule) { TableSyncPoller(pollSchedule, activity) }

    val saveFocusRequester = remember { FocusRequester() }
    val roundFocusRequester = remember { FocusRequester() }
    val scoreFocusRequester = remember { FocusRequester() }
    val selectedRoundTables = allTables.filter { it.roundId == selectedRoundId }.sortedBy { it.tableId }
    val hasUnsavedChanges = tableEditors.values.any { it.hasUnsavedChanges }
    val saveEmaReportTitle = stringResource(Res.string.save_ema_report)
    val roleLabel = adminStatus?.let { stringResource(if (it.isAdmin) Res.string.role_admin else Res.string.role_editor) }

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
                    errorMessage = result.error.toUiText()
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
                                is AppResult.Failure -> errorMessage = detail.error.toUiText()
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
        sync.loadPlayerNames(force)?.let { errorMessage = it }
        val newRounds = when (val result = tablesPresenter.loadRounds(tournamentId, force)) {
            is AppResult.Success -> result.value.sortedBy { it.roundId }
            is AppResult.Failure -> {
                errorMessage = result.error.toUiText()
                isLoading = false
                return
            }
        }
        rounds = newRounds
        val allTablesResult = tablesPresenter.loadTables(tournamentId, null, force)
        allTables = when (allTablesResult) {
            is AppResult.Success -> allTablesResult.value.sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
            is AppResult.Failure -> {
                errorMessage = allTablesResult.error.toUiText()
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
    LaunchedEffect(selectedRoundId, selectedTableId, isLoading, selectedTableLoaded, sync.focusToken) {
        if (isLoading || selectedTableId == null || !selectedTableLoaded) return@LaunchedEffect
        // Wait for the first layout pass. Then the first score field takes the focus, so the keyboard flow can start.
        withFrameNanos { }
        runCatching { scoreFocusRequester.requestFocus() }
    }

    LaunchedEffect(tournamentId) {
        poller.run(
            hasUnsavedEdits = { sync.hasUnsavedChanges },
            check = { sync.checkForChanges() },
        )
    }

    suspend fun exportResults() {
        if (isLoading || isLoadingRest) return
        // The export uses what the app holds, like the ranking. One check first catches a change that is
        // newer than the last poll.
        sync.checkForChanges()
        isLoading = true
        errorMessage = null
        when (val result = rankingPresenter.load(tournamentId)) {
            is AppResult.Failure -> errorMessage = result.error.toUiText()
            is AppResult.Success -> {
                val snapshot = result.value
                officialResultsExportError(snapshot.rankingTables)?.let {
                    errorMessage = it
                    isLoading = false
                    return
                }
                if (snapshot.tournamentPlayers.any { !it.isAssigned }) {
                    errorMessage = UiText.of(Res.string.assign_all_before_export)
                    isLoading = false
                    return
                }
                when (val report = rankingPresenter.generateEmaReport(tournamentId, snapshot.rankings.players)) {
                    is AppResult.Failure -> errorMessage = report.error.toUiText()
                    is AppResult.Success -> saveBinaryFile(
                        dialogTitle = saveEmaReportTitle,
                        // The name tells which results the file holds: the revision, and when it was made.
                        fileName = generatedFileName(
                            tournamentName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament",
                            kind = "ema-report",
                            year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) },
                            extension = "xlsx",
                            now = Clock.System.now(),
                            revision = snapshot.dataRevision,
                        ),
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
            RoundEditorAction.Statistics -> {
                val round = selectedRoundId
                val table = selectedTableId
                navController.navigate(
                    if (round != null && table != null) {
                        StatisticsRoute(StatisticsScope.TABLE.name, tournamentId = tournamentId, roundId = round, tableId = table)
                    } else {
                        StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = tournamentId)
                    },
                )
            }
            RoundEditorAction.Timer -> openTimer(navController, allTables.maxOfOrNull { it.roundId }?.plus(1) ?: 1)
            RoundEditorAction.Export -> scope.launch { exportResults() }
        }
    }

    fun requestAction(action: RoundEditorAction) {
        if (isLoading) return
        // Choosing the table that is already open changes nothing.
        if (action is RoundEditorAction.SelectTable && action.tableId == selectedTableId) return
        // The user must save or discard the changes before leaving a table.
        if (hasUnsavedChanges) pendingAction = action else perform(action)
    }

    /** Saves the given tables, or all tables with changes. Table saves are independent, so one failure keeps the other data. */
    fun requestSave(action: RoundEditorAction? = null, tableIds: List<Int>? = null) {
        val targets = tableIds ?: tableEditors.filterValues { it.hasUnsavedChanges }.keys.toList()
        scope.launch { if (sync.saveTables(targets)) action?.let(::perform) }
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
    LaunchedEffect(restoreSaveFocus, isLoading) {
        if (restoreSaveFocus && !isLoading) {
            restoreSaveFocus = false
            saveFocusRequester.requestFocus()
        }
    }
    if (sync.updateNotices.isNotEmpty()) {
        TableUpdatesDialog(notices = sync.updateNotices, onDismiss = sync::dismissNotices)
    }
    errorMessage?.let { message -> AppErrorDialog(message) { errorMessage = null } }

    Box(modifier = Modifier.trackActivity(activity)) {
    AppScaffold(
        title = tournament?.name?.ifBlank { null } ?: stringResource(Res.string.scope_tournament),
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
        showBackgroundLogo = isLoading && tournament == null,
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
            if (tournament?.isTeams == true) {
                AppTopBarButton(
                    text = stringResource(Res.string.teams),
                    icon = Icons.Default.Groups,
                    onClick = { requestAction(RoundEditorAction.Teams) },
                    enabled = !isLoading,
                )
            }
            AppTopBarButton(
                text = stringResource(Res.string.players),
                icon = Icons.Default.People,
                onClick = { requestAction(RoundEditorAction.Players) },
                enabled = !isLoading,
            )
            AppTopBarButton(
                text = stringResource(Res.string.statistics),
                icon = StatisticsIcon,
                onClick = { requestAction(RoundEditorAction.Statistics) },
                enabled = !isLoading,
            )
            AppTopBarButton(
                text = stringResource(Res.string.ema_report),
                icon = Icons.Outlined.Assessment,
                onClick = { requestAction(RoundEditorAction.Export) },
                enabled = !isLoading && !isLoadingRest,
            )
        },
    ) {
        CompositionLocalProvider(
            LocalHandStatOptions provides HandStatOptions(
                countBestHands = tournament?.countBestHands ?: true,
                countChickenHands = tournament?.countChickenHands ?: true,
            ),
        ) {
        // The results the screen holds, and when the server was last asked. Support can ask for this.
        val dataStatus = dataStatus(
            revision = allTables.takeIf { it.isNotEmpty() }?.let { rows -> tournamentDataRevision(rows.map { it.version }) },
            at = sync.lastCheckedAt,
        ).asString()
        Column(Modifier.fillMaxSize()) {
        if (dataStatus.isNotBlank()) {
            Text(
                dataStatus,
                Modifier.fillMaxWidth().padding(top = 4.dp, end = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
            onSave = { requestSave(tableIds = listOf(it)) },
            onSaveAndNext = { requestSave(RoundEditorAction.NextTable(it), listOf(it)) },
        )
        }
        }
        }
    }
    }
}
