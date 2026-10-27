package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import com.etologic.mahjongtournamentsuite.presentation.components.SelectMenu
import com.etologic.mahjongtournamentsuite.presentation.components.SelectOption
import com.etologic.mahjongtournamentsuite.presentation.components.rememberSelectMenuState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.StatisticsRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsScope
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.all_rounds
import mahjongtournamentsuite.adminapp.generated.resources.delete_coming_soon
import mahjongtournamentsuite.adminapp.generated.resources.done
import mahjongtournamentsuite.adminapp.generated.resources.edit_coming_soon
import mahjongtournamentsuite.adminapp.generated.resources.filter
import mahjongtournamentsuite.adminapp.generated.resources.loading
import mahjongtournamentsuite.adminapp.generated.resources.manual_results
import mahjongtournamentsuite.adminapp.generated.resources.no
import mahjongtournamentsuite.adminapp.generated.resources.no_tables_yet
import mahjongtournamentsuite.adminapp.generated.resources.open
import mahjongtournamentsuite.adminapp.generated.resources.players
import mahjongtournamentsuite.adminapp.generated.resources.preview_tournament_2
import mahjongtournamentsuite.adminapp.generated.resources.round
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.table
import mahjongtournamentsuite.adminapp.generated.resources.tables
import mahjongtournamentsuite.adminapp.generated.resources.the_tables_show_here_when_the
import mahjongtournamentsuite.adminapp.generated.resources.yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun TablesScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<TablesPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val playersByTournamentId by store.tournamentPlayers.collectAsState()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    val isTeamsTournament = tournaments.firstOrNull { it.id == tournamentId }?.isTeams == true
    val roundsByTournamentId by store.tournamentRounds.collectAsState()
    val tablesByTournamentId by store.tournamentTables.collectAsState()

    val playersById = playersByTournamentId[tournamentId].orEmpty().associateBy { it.id }
    val rounds = roundsByTournamentId[tournamentId].orEmpty()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<UiText?>(null) }

    var selectedRoundId by remember { mutableStateOf<Int?>(null) }
    val roundMenu = rememberSelectMenuState()

    val tablesKey = selectedRoundId?.toString() ?: "all"
    val tables = tablesByTournamentId[tournamentId]?.get(tablesKey) ?: emptyList()
    val tablesListState = rememberPersistentLazyListState("tables")
    var tableFocusApplied by remember(tablesKey) { mutableStateOf(false) }
    var lastFocusedTableKey by rememberSaveable(tournamentId) { mutableStateOf<String?>(null) }
    var lastFocusedTableControl by rememberSaveable(tournamentId) { mutableStateOf("row") }
    val tableFocusRequesters = remember(tables.map { it.roundId to it.tableId }) {
        tables.map { FocusRequester() }
    }
    val tableActionFocusRequesters = remember(tables.map { it.roundId to it.tableId }) {
        tables.map { FocusRequester() }
    }

    LaunchedEffect(isLoading, tablesKey, tables.map { it.roundId to it.tableId }) {
        if (!tableFocusApplied && !isLoading && tableFocusRequesters.isNotEmpty()) {
            val index = tables.indexOfFirst {
                "${it.roundId}_${it.tableId}" == lastFocusedTableKey
            }.coerceAtLeast(0)
            if (lastFocusedTableControl == "menu") {
                tableActionFocusRequesters[index].requestFocus()
            } else {
                tableFocusRequesters[index].requestFocus()
            }
            tableFocusApplied = true
        }
    }

    fun refreshMeta(force: Boolean = false) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val playersResult = presenter.loadPlayers(tournamentId, force)) {
                is AppResult.Success -> store.upsertPlayers(tournamentId, playersResult.value)
                is AppResult.Failure -> {
                    errorMessage = playersResult.error.toUiText()
                    isLoading = false
                    return@launch
                }
            }

            when (val roundsResult = presenter.loadRounds(tournamentId, force)) {
                is AppResult.Success -> store.upsertRounds(tournamentId, roundsResult.value)
                is AppResult.Failure -> {
                    errorMessage = roundsResult.error.toUiText()
                    isLoading = false
                    return@launch
                }
            }

            isLoading = false
        }
    }

    fun refreshTables(roundId: Int?, force: Boolean = false) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val tablesResult = presenter.loadTables(tournamentId, roundId, force)) {
                is AppResult.Success -> store.upsertTables(tournamentId, roundId, tablesResult.value)
                is AppResult.Failure -> errorMessage = tablesResult.error.toUiText()
            }

            isLoading = false
        }
    }

    LaunchedEffect(tournamentId) {
        refreshMeta()
        refreshTables(roundId = null)
    }

    errorMessage?.let { message ->
        AppErrorDialog(
            message = message,
            onDismiss = { errorMessage = null },
        )
    }

    AppScaffold(
        title = stringResource(Res.string.tables),
        subtitle = tournaments.firstOrNull { it.id == tournamentId }?.name?.ifBlank { null },
        isLoading = isLoading,
        showBackgroundLogo = isLoading && tables.isEmpty(),
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onStatistics = {
                    navController.navigate(StatisticsRoute(StatisticsScope.TOURNAMENT.name, tournamentId = tournamentId))
                },
            )
        },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                title = stringResource(Res.string.tables),
                subtitle = if (selectedRoundId == null) stringResource(Res.string.all_rounds) else stringResource(Res.string.round_n, selectedRoundId ?: 0),
                actions = {
                    AppTextButton(
                        enabled = !isLoading,
                        onClick = roundMenu::open,
                    ) {
                        Text(stringResource(Res.string.filter))
                    }
                },
                content = {
                    val allRounds = stringResource(Res.string.all_rounds)
                    val roundOptions = listOf(SelectOption<Int?>(null, allRounds)) +
                        rounds.map { round -> SelectOption(round.roundId, stringResource(Res.string.round_n, round.roundId)) }
                    SelectMenu(
                        state = roundMenu,
                        options = roundOptions,
                        selected = selectedRoundId,
                        onSelect = { roundId ->
                            selectedRoundId = roundId
                            refreshTables(roundId = roundId)
                        },
                    )

                    when {
                        isLoading -> {
                            Text(stringResource(Res.string.loading))
                        }

                        tables.isEmpty() -> {
                            AppEmptyState(
                                icon = Icons.Default.People,
                                title = stringResource(Res.string.no_tables_yet),
                                message = stringResource(Res.string.the_tables_show_here_when_the),
                                compact = true,
                            )
                        }

                        else -> {
                            LazyColumnWithScrollbar(
                                state = tablesListState,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item {
                                    DataTableHeaderRow {
                                        Text(
                                            text = stringResource(Res.string.round),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = stringResource(Res.string.table),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = stringResource(Res.string.players),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(2.8f),
                                        )
                                        Text(
                                            text = stringResource(Res.string.done),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        Text(
                                            text = stringResource(Res.string.manual_results),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        Text(
                                            text = "",
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.width(44.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        )
                                    }
                                    DataTableDivider()
                                }

                                itemsIndexed(
                                    tables,
                                    key = { _, table -> "${table.roundId}_${table.tableId}" },
                                ) { index, table ->
                                    val playersLabel = table.playerIds.joinToString(" • ") { playerId ->
                                        val player = playersById[playerId]
                                        if (player == null) {
                                            playerId.toString()
                                        } else if (isTeamsTournament) {
                                            "${player.id}(T${player.team})"
                                        } else {
                                            player.id.toString()
                                        }
                                    }

                                    DataTableRow(
                                        modifier = Modifier
                                            .focusRequester(tableFocusRequesters[index])
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    lastFocusedTableKey = "${table.roundId}_${table.tableId}"
                                                }
                                            },
                                        onClick = {
                                            lastFocusedTableKey = "${table.roundId}_${table.tableId}"
                                            lastFocusedTableControl = "row"
                                            navController.navigate(
                                                TournamentRoute(tournamentId),
                                            )
                                        },
                                    ) {
                                        Text(
                                            text = table.roundId.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = table.tableId.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = playersLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(2.8f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(if (table.isCompleted) Res.string.yes else Res.string.no),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        Text(
                                            text = stringResource(if (table.useTotalsOnly) Res.string.yes else Res.string.no),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        RowActionsMenu(
                                            enabled = !isLoading,
                                            items = listOf(
                                                RowActionMenuItem(
                                                    label = stringResource(Res.string.open),
                                                    onClick = {
                                                        lastFocusedTableKey = "${table.roundId}_${table.tableId}"
                                                        lastFocusedTableControl = "menu"
                                                        navController.navigate(
                                                            TournamentRoute(tournamentId),
                                                        )
                                                    },
                                                ),
                                                RowActionMenuItem(label = stringResource(Res.string.edit_coming_soon), enabled = false, onClick = {}),
                                                RowActionMenuItem(label = stringResource(Res.string.delete_coming_soon), enabled = false, onClick = {}),
                                            ),
                                            modifier = Modifier.width(44.dp),
                                            focusRequester = tableActionFocusRequesters[index],
                                        )
                                    }
                                    DataTableDivider()
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun TablesScreenPreview() {
    MtsTheme(useDarkTheme = false) {
        AppScaffold(
            title = stringResource(Res.string.tables),
            subtitle = stringResource(Res.string.preview_tournament_2),
            isLoading = false,
        ) {
            ScreenColumn(
                maxWidth = 1100.dp,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(
                    title = stringResource(Res.string.tables),
                    subtitle = stringResource(Res.string.all_rounds),
                    actions = {
                        AppTextButton(enabled = true, onClick = {}) {
                            Text(stringResource(Res.string.filter))
                        }
                    },
                    content = {
                        LazyColumnWithScrollbar(
                            state = rememberLazyListState(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            item {
                                DataTableHeaderRow {
                                    Text(
                                        text = stringResource(Res.string.round),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.7f),
                                    )
                                    Text(
                                        text = stringResource(Res.string.table),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.7f),
                                    )
                                    Text(
                                        text = stringResource(Res.string.players),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(2.8f),
                                    )
                                    Text(
                                        text = stringResource(Res.string.done),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.9f),
                                    )
                                    Text(
                                        text = stringResource(Res.string.manual_results),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.9f),
                                    )
                                    Text(
                                        text = "",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.width(44.dp),
                                    )
                                }
                                DataTableDivider()
                            }

                            item {
                                PreviewTableRow(
                                    round = 1,
                                    table = 1,
                                    players = "101(T1) • 102(T1) • 103(T2) • 104(T2)",
                                    done = true,
                                    manualResults = false,
                                )
                            }
                            item {
                                DataTableDivider()
                            }
                            item {
                                PreviewTableRow(
                                    round = 1,
                                    table = 2,
                                    players = "105(T2) • 106(T3) • 107(T4) • 108(T4)",
                                    done = false,
                                    manualResults = true,
                                )
                            }
                            item {
                                DataTableDivider()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewTableRow(
    round: Int,
    table: Int,
    players: String,
    done: Boolean,
    manualResults: Boolean,
) {
    DataTableRow(onClick = {}) {
        Text(
            text = round.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = table.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = players,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(if (done) Res.string.yes else Res.string.no),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = stringResource(if (manualResults) Res.string.yes else Res.string.no),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
        )
        RowActionsMenu(
            enabled = true,
            items = listOf(
                RowActionMenuItem(label = stringResource(Res.string.open), onClick = {}),
                RowActionMenuItem(label = stringResource(Res.string.edit_coming_soon), enabled = false, onClick = {}),
                RowActionMenuItem(label = stringResource(Res.string.delete_coming_soon), enabled = false, onClick = {}),
            ),
            modifier = Modifier.width(44.dp),
        )
    }
}
