package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRound
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.presentation.MembersRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerTablesRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.UnsavedChangesDialog
import com.etologic.mahjongtournamentsuite.presentation.platform.openRankings
import com.etologic.mahjongtournamentsuite.presentation.platform.openTimer
import com.etologic.mahjongtournamentsuite.presentation.presenter.TableManagerPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarLeadingActions

private sealed class PendingUnsavedAction {
    data object Back : PendingUnsavedAction()
    data object Refresh : PendingUnsavedAction()
    data class SelectRound(val roundId: Int) : PendingUnsavedAction()
    data class SelectTable(val tableId: Int) : PendingUnsavedAction()
    data object NavigatePlayers : PendingUnsavedAction()
    data object NavigatePlayerTables : PendingUnsavedAction()
    data object NavigateMembers : PendingUnsavedAction()
    data object OpenRankings : PendingUnsavedAction()
    data object OpenTimer : PendingUnsavedAction()
}

@Composable
fun TournamentScreen(
    navController: NavHostController,
    tournamentId: String,
    tournamentName: String,
) {
    val presenter = koinInject<TablesPresenter>()
    val tablePresenter = koinInject<TableManagerPresenter>()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rounds by remember { mutableStateOf<List<TournamentRound>>(emptyList()) }
    var selectedRoundId by remember { mutableStateOf<Int?>(null) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var selectedTableId by remember { mutableStateOf<Int?>(null) }
    var playerNamesById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var tableState by remember { mutableStateOf<TableState?>(null) }
    var hands by remember { mutableStateOf<List<TableHand>>(emptyList()) }
    var pendingUnsavedAction by remember { mutableStateOf<PendingUnsavedAction?>(null) }

    suspend fun loadSelectedTable() {
        val roundId = selectedRoundId ?: return
        val tableId = selectedTableId ?: return

        when (val tableResult = tablePresenter.loadTableWithHands(tournamentId, roundId, tableId)) {
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

    suspend fun refresh() {
        isLoading = true
        errorMessage = null

        when (val playersResult = presenter.loadPlayers(tournamentId)) {
            is AppResult.Success -> {
                playerNamesById = playersResult.value.associate { it.id to it.name }
            }

            is AppResult.Failure -> {
                playerNamesById = emptyMap()
            }
        }

        val roundsResult = presenter.loadRounds(tournamentId)
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

        when (val tablesResult = presenter.loadTables(tournamentId, roundId)) {
            is AppResult.Success -> {
                tables = tablesResult.value.sortedBy { it.tableId }
            }

            is AppResult.Failure -> errorMessage = tablesResult.error.toUiMessage()
        }

        val nextTableId = selectedTableId
            ?.takeIf { id -> tables.any { it.tableId == id } }
            ?: tables.firstOrNull()?.tableId
        selectedTableId = nextTableId

        tableState = null
        hands = emptyList()
        loadSelectedTable()

        isLoading = false
    }

    LaunchedEffect(tournamentId) {
        refresh()
    }

    val table = tableState
    val editorState = remember(table, hands) { table?.let { TableManagerEditorState.from(it, hands) } }
    val hasUnsavedChanges = editorState?.hasUnsavedChanges == true

    suspend fun saveChanges(): Boolean {
        val editor = editorState ?: return true
        val roundId = selectedRoundId ?: return true
        val tableId = selectedTableId ?: return true

        val tablePatch = editor.buildTablePatch()
        val handPatches = editor.buildHandPatches()
        if (tablePatch.isEmpty() && handPatches.isEmpty()) return true

        isLoading = true
        errorMessage = null
        try {
            if (tablePatch.isNotEmpty()) {
                when (val result = tablePresenter.patchTable(tournamentId, roundId, tableId, tablePatch)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                        return false
                    }
                }
            }

            for ((handId, patch) in handPatches) {
                when (val result = tablePresenter.patchHand(tournamentId, roundId, tableId, handId, patch)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> {
                        errorMessage = result.error.toUiMessage()
                        return false
                    }
                }
            }

            loadSelectedTable()
            return true
        } finally {
            isLoading = false
        }
    }

    suspend fun performUnsavedAction(action: PendingUnsavedAction) {
        when (action) {
            PendingUnsavedAction.Back -> navController.popBackStack()
            PendingUnsavedAction.Refresh -> refresh()
            is PendingUnsavedAction.SelectRound -> selectRound(action.roundId)
            is PendingUnsavedAction.SelectTable -> selectTable(action.tableId)
            PendingUnsavedAction.NavigatePlayers -> navController.navigate(PlayersRoute(tournamentId = tournamentId))
            PendingUnsavedAction.NavigatePlayerTables -> navController.navigate(PlayerTablesRoute(tournamentId = tournamentId))
            PendingUnsavedAction.NavigateMembers -> navController.navigate(MembersRoute(tournamentId = tournamentId))
            PendingUnsavedAction.OpenRankings -> openRankings(navController, tournamentId, tournamentName)
            PendingUnsavedAction.OpenTimer -> openTimer(navController)
        }
    }

    fun requestUnsavedAction(action: PendingUnsavedAction) {
        if (isLoading) return
        if (hasUnsavedChanges) {
            pendingUnsavedAction = action
            return
        }
        coroutineScope.launch { performUnsavedAction(action) }
    }

    pendingUnsavedAction?.let { action ->
        UnsavedChangesDialog(
            isSaving = isLoading,
            onSave = {
                coroutineScope.launch {
                    if (saveChanges()) {
                        pendingUnsavedAction = null
                        performUnsavedAction(action)
                    }
                }
            },
            onDiscard = {
                pendingUnsavedAction = null
                coroutineScope.launch { performUnsavedAction(action) }
            },
            onCancel = { pendingUnsavedAction = null },
        )
    }

    AppScaffold(
        title = tournamentName.ifBlank { "Tournament" },
        isLoading = isLoading,
        onBack = { requestUnsavedAction(PendingUnsavedAction.Back) },
        leadingActions = {
            AppTopBarLeadingActions(
                showThemeToggle = true,
                onTimer = { openTimer(navController) },
                onRanking = { openRankings(navController, tournamentId, tournamentName) },
            )
        },
        actions = {
            AppTopBarActions(
                onPlayers = { requestUnsavedAction(PendingUnsavedAction.NavigatePlayers) },
                onPlayerTables = { requestUnsavedAction(PendingUnsavedAction.NavigatePlayerTables) },
                onMembers = { requestUnsavedAction(PendingUnsavedAction.NavigateMembers) },
                onRefresh = { requestUnsavedAction(PendingUnsavedAction.Refresh) },
            )
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
                    selectedRoundId = selectedRoundId,
                    tables = tables,
                    selectedTableId = selectedTableId,
                    enabled = !isLoading,
                    isLoading = isLoading,
                    onSelectRound = { roundId -> requestUnsavedAction(PendingUnsavedAction.SelectRound(roundId)) },
                    onSelectTable = { tableId -> requestUnsavedAction(PendingUnsavedAction.SelectTable(tableId)) },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val saveButtonHeight = 64.dp
                    val listState = rememberLazyListState()
                    val showOverlaySave = hasUnsavedChanges

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = if (showOverlaySave) saveButtonHeight + 32.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (errorMessage != null) {
                            item(key = "error") {
                                AppErrorMessage(message = errorMessage.orEmpty())
                            }
                        }

                        item {
                            if (selectedRoundId != null && selectedTableId != null && editorState != null) {
                                TableManagerContent(
                                    editor = editorState,
                                    enabled = !isLoading,
                                    playerNamesById = playerNamesById,
                                )
                            } else if (!isLoading) {
                                Text(
                                    text = "Select a round and table to manage hands.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (hasUnsavedChanges) {
                            item(key = "save-inline-spacer") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(saveButtonHeight),
                                )
                            }
                        }
                    }

                    if (showOverlaySave) {
                        val colors = MaterialTheme.colorScheme
                        Button(
                            onClick = { coroutineScope.launch { saveChanges() } },
                            enabled = !isLoading,
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
    selectedRoundId: Int?,
    tables: List<TournamentTable>,
    selectedTableId: Int?,
    enabled: Boolean,
    isLoading: Boolean,
    onSelectRound: (Int) -> Unit,
    onSelectTable: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 260.dp, max = 360.dp),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Rounds",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(rounds, key = { it.roundId }) { round ->
                            RoundTableSidebarItem(
                                label = "Round ${round.roundId}",
                                selected = round.roundId == selectedRoundId,
                                enabled = enabled,
                                onClick = { onSelectRound(round.roundId) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colorScheme.outlineVariant),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Tables",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

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
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(tables, key = { it.tableId }) { table ->
                                    val status = when {
                                        table.isCompleted -> " ✓"
                                        table.useTotalsOnly -> " Σ"
                                        else -> ""
                                    }
                                    RoundTableSidebarItem(
                                        label = "Table ${table.tableId}$status",
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
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.small,
        color = if (selected) colorScheme.secondaryContainer else colorScheme.surface,
        contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}
