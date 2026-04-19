package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.TableRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.TablesPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
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
    val roundsByTournamentId by store.tournamentRounds.collectAsState()
    val tablesByTournamentId by store.tournamentTables.collectAsState()

    val playersById = playersByTournamentId[tournamentId].orEmpty().associateBy { it.id }
    val rounds = roundsByTournamentId[tournamentId].orEmpty()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedRoundId by remember { mutableStateOf<Int?>(null) }
    var isRoundMenuOpen by remember { mutableStateOf(false) }

    val tablesKey = selectedRoundId?.toString() ?: "all"
    val tables = tablesByTournamentId[tournamentId]?.get(tablesKey) ?: emptyList()

    fun refreshMeta() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val playersResult = presenter.loadPlayers(tournamentId)) {
                is AppResult.Success -> store.upsertPlayers(tournamentId, playersResult.value)
                is AppResult.Failure -> {
                    errorMessage = playersResult.error.toUiMessage()
                    isLoading = false
                    return@launch
                }
            }

            when (val roundsResult = presenter.loadRounds(tournamentId)) {
                is AppResult.Success -> store.upsertRounds(tournamentId, roundsResult.value)
                is AppResult.Failure -> {
                    errorMessage = roundsResult.error.toUiMessage()
                    isLoading = false
                    return@launch
                }
            }

            isLoading = false
        }
    }

    fun refreshTables(roundId: Int?) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val tablesResult = presenter.loadTables(tournamentId, roundId)) {
                is AppResult.Success -> store.upsertTables(tournamentId, roundId, tablesResult.value)
                is AppResult.Failure -> errorMessage = tablesResult.error.toUiMessage()
            }

            isLoading = false
        }
    }

    LaunchedEffect(tournamentId) {
        refreshMeta()
        refreshTables(roundId = null)
    }

    AppScaffold(
        title = "Tables",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onRefresh = {
                    refreshMeta()
                    refreshTables(roundId = selectedRoundId)
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
                title = "Tables",
                subtitle = if (selectedRoundId == null) "All rounds" else "Round $selectedRoundId",
                actions = {
                    AppTextButton(
                        enabled = !isLoading,
                        onClick = { isRoundMenuOpen = true },
                    ) {
                        Text("Filter")
                    }
                },
                content = {
                    DropdownMenu(
                        expanded = isRoundMenuOpen,
                        onDismissRequest = { isRoundMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("All rounds") },
                            onClick = {
                                selectedRoundId = null
                                isRoundMenuOpen = false
                                refreshTables(roundId = null)
                            },
                        )
                        rounds.forEach { round ->
                            DropdownMenuItem(
                                text = { Text("Round ${round.roundId}") },
                                onClick = {
                                    selectedRoundId = round.roundId
                                    isRoundMenuOpen = false
                                    refreshTables(roundId = round.roundId)
                                },
                            )
                        }
                    }

                    when {
                        errorMessage != null -> {
                            AppErrorMessage(message = errorMessage!!)
                        }

                        isLoading -> {
                            Text("Loading…")
                        }

                        tables.isEmpty() -> {
                            Text(
                                text = "No tables found yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item {
                                    DataTableHeaderRow {
                                        Text(
                                            text = "Round",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = "Table",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.7f),
                                        )
                                        Text(
                                            text = "Players",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(2.8f),
                                        )
                                        Text(
                                            text = "Done",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        Text(
                                            text = "Manual Results",
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

                                items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                                    val playersLabel = table.playerIds.joinToString(" • ") { playerId ->
                                        val player = playersById[playerId]
                                        if (player == null) {
                                            playerId.toString()
                                        } else {
                                            "${player.id}(T${player.team})"
                                        }
                                    }

                                    DataTableRow(
                                        onClick = {
                                            navController.navigate(
                                                TableRoute(
                                                    tournamentId = tournamentId,
                                                    roundId = table.roundId,
                                                    tableId = table.tableId,
                                                ),
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
                                            text = if (table.isCompleted) "Yes" else "No",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        Text(
                                            text = if (table.useTotalsOnly) "Yes" else "No",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                        RowActionsMenu(
                                            enabled = !isLoading,
                                            items = listOf(
                                                RowActionMenuItem(
                                                    label = "Open",
                                                    onClick = {
                                                        navController.navigate(
                                                            TableRoute(
                                                                tournamentId = tournamentId,
                                                                roundId = table.roundId,
                                                                tableId = table.tableId,
                                                            ),
                                                        )
                                                    },
                                                ),
                                                RowActionMenuItem(label = "Edit (coming soon)", enabled = false, onClick = {}),
                                                RowActionMenuItem(label = "Delete (coming soon)", enabled = false, onClick = {}),
                                            ),
                                            modifier = Modifier.width(44.dp),
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
