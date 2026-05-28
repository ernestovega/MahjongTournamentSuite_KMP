package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
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
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PlayerTablesScreen(
    navController: NavHostController,
    tournamentId: String,
    initialPlayerId: Int?,
) {
    val presenter = koinInject<TablesPresenter>()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var players by remember { mutableStateOf<List<TournamentPlayer>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }

    var selectedPlayerId by remember { mutableStateOf<Int?>(initialPlayerId) }
    var isPlayerMenuOpen by remember { mutableStateOf(false) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val playersResult = presenter.loadPlayers(tournamentId)) {
                is AppResult.Success -> {
                    players = playersResult.value
                    if (selectedPlayerId == null) {
                        selectedPlayerId = players.firstOrNull()?.id
                    }
                }
                is AppResult.Failure -> {
                    errorMessage = playersResult.error.toUiMessage()
                    isLoading = false
                    return@launch
                }
            }

            when (val tablesResult = presenter.loadTables(tournamentId, roundId = null)) {
                is AppResult.Success -> tables = tablesResult.value
                is AppResult.Failure -> errorMessage = tablesResult.error.toUiMessage()
            }

            isLoading = false
        }
    }

    LaunchedEffect(tournamentId) {
        refresh()
    }

    val playerId = selectedPlayerId
    val playersById = players.associateBy { it.id }
    val tablesForPlayer = if (playerId == null) emptyList() else tables.filter { it.playerIds.contains(playerId) }
    val grouped = tablesForPlayer
        .groupBy { it.roundId }
        .entries
        .sortedBy { it.key }
        .map { it.key to it.value.sortedBy { table -> table.tableId } }

    AppScaffold(
        title = "Player tables",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = { refresh() }) },
    ) {
        ScreenColumn(
            maxWidth = 1200.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            errorMessage?.let {
                AppErrorMessage(message = it)
            }

            SectionCard(
                title = "Player",
                subtitle = if (playerId == null) "Select a player to see all assigned tables" else "Player $playerId • ${tablesForPlayer.size} tables",
                actions = {
                    AppTextButton(
                        enabled = !isLoading && players.isNotEmpty(),
                        onClick = { isPlayerMenuOpen = true },
                    ) {
                        Text(if (playerId == null) "Select" else "Change")
                    }
                },
                content = {
                    DropdownMenu(
                        expanded = isPlayerMenuOpen,
                        onDismissRequest = { isPlayerMenuOpen = false },
                    ) {
                        players.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("Player ${p.id} (Team ${p.team})") },
                                onClick = {
                                    selectedPlayerId = p.id
                                    isPlayerMenuOpen = false
                                },
                            )
                        }
                    }

                    if (isLoading) {
                        Text("Loading…")
                    } else if (players.isEmpty()) {
                        Text(
                            text = "No players available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (playerId == null) {
                        Text(
                            text = "Select a player to view assignments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Shows every table where this player participates, grouped by round.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            SectionCard(
                title = "Assignments",
                subtitle = if (playerId == null) "—" else "Round-by-round list",
                content = {
                    if (isLoading) {
                        Text("Loading…")
                        return@SectionCard
                    }
                    if (playerId == null || players.isEmpty()) {
                        Text(
                            text = "Select a player to see tables.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@SectionCard
                    }
                    if (tablesForPlayer.isEmpty()) {
                        Text(
                            text = "This player is not assigned to any table yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@SectionCard
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        grouped.forEach { entry ->
                            val roundId = entry.first
                            val roundTables = entry.second
                            item(key = "round_$roundId") {
                                Text(
                                    text = "Round $roundId",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                DataTableDivider()
                                DataTableHeaderRow {
                                    Text(
                                        text = "Table",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                    Text(
                                        text = "Players",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(2.6f),
                                    )
                                    Text(
                                        text = "Done",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                    Text(
                                        text = "Manual Results",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.8f),
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
                            items(roundTables, key = { "${it.roundId}_${it.tableId}" }) { table ->
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
                                        text = table.tableId.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                    Text(
                                        text = playersLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(2.6f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (table.isCompleted) "Yes" else "No",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                    Text(
                                        text = if (table.useTotalsOnly) "Yes" else "No",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.8f),
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
                                        ),
                                        modifier = Modifier.width(44.dp),
                                    )
                                }
                                DataTableDivider()
                            }
                        }
                    }
                },
            )
        }
    }
}
