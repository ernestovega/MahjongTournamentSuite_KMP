package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.presentation.PlayerTablesRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionMenuItem
import com.etologic.mahjongtournamentsuite.presentation.components.RowActionsMenu
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PlayersScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<PlayersPresenter>()
    val store = koinInject<AppMemoryStore>()
    val coroutineScope = rememberCoroutineScope()

    val playersByTournamentId by store.tournamentPlayers.collectAsState()
    val players = playersByTournamentId[tournamentId].orEmpty()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = presenter.loadPlayers(tournamentId)) {
                is AppResult.Success -> store.upsertPlayers(tournamentId, result.value)
                is AppResult.Failure -> errorMessage = result.error.toUiMessage()
            }
            isLoading = false
        }
    }

    LaunchedEffect(tournamentId) {
        refresh()
    }

    AppScaffold(
        title = "Players",
        subtitle = tournamentId,
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = { refresh() }) },
    ) {
        ScreenColumn(
            maxWidth = 900.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tournament players (generated).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                isLoading && players.isEmpty() -> {
                    Text("Loading…")
                }
                errorMessage != null -> {
                    AppErrorMessage(message = errorMessage!!)
                }
                players.isEmpty() -> {
                    Text(
                        text = "No players found yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    SectionCard(
                        title = "Players",
                        subtitle = "${players.size} players",
                        content = {
                            PlayersTable(
                                navController = navController,
                                tournamentId = tournamentId,
                                players = players,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayersTable(
    navController: NavHostController,
    tournamentId: String,
    players: List<TournamentPlayer>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            DataTableHeaderRow {
                Text(
                    text = "ID",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.2f),
                )
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.6f),
                )
                Text(
                    text = "Team",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.2f),
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

        items(players, key = { it.id }) { player ->
            DataTableRow {
                Text(
                    text = player.id.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.2f),
                )
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.6f),
                )
                Text(
                    text = player.team.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.2f),
                )
                RowActionsMenu(
                    enabled = true,
                    items = listOf(
                        RowActionMenuItem(
                            label = "View tables",
                            onClick = {
                                navController.navigate(
                                    PlayerTablesRoute(
                                        tournamentId = tournamentId,
                                        initialPlayerId = player.id,
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
