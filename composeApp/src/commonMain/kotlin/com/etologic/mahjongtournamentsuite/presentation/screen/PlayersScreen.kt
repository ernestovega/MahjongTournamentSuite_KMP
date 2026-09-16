package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Links generated tournament slots to players in the shared base. */
@Composable
fun PlayersScreen(navController: NavHostController, tournamentId: String) {
    val presenter = koinInject<PlayersPresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val slots by store.tournamentPlayers.collectAsState()
    val basePlayers by store.players.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var savingId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuSlotId by remember { mutableStateOf<Int?>(null) }

    fun refresh() = scope.launch {
        loading = true
        error = null
        when (val result = presenter.loadPlayers(tournamentId)) {
            is AppResult.Success -> store.upsertPlayers(tournamentId, result.value)
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        when (val result = presenter.loadBasePlayers()) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        loading = false
    }

    fun assign(slotId: Int, player: Player?) = scope.launch {
        savingId = slotId
        error = null
        when (val result = presenter.assignPlayer(tournamentId, slotId, player?.emaId)) {
            is AppResult.Success -> refresh()
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        menuSlotId = null
        savingId = null
    }

    LaunchedEffect(tournamentId) { refresh() }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val players = slots[tournamentId].orEmpty()

    AppScaffold(
        title = "Tournament players",
        subtitle = "Assign each generated player to a shared EMA player.",
        isLoading = loading || savingId != null,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = ::refresh) },
    ) {
        ScreenColumn(maxWidth = 1000.dp, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { AppErrorMessage(it) }
            SectionCard(title = "Assignments", subtitle = "${players.size} tournament player slots") {
                if (players.isEmpty() && !loading) Text("No generated tournament players found.")
                LazyColumn {
                    items(players, key = { it.id }) { slot ->
                        val assigned = slot.assignedEmaId?.let(playersByEma::get)
                        DataTableRow {
                            Text("${slot.id} · ${slot.name}", modifier = Modifier.weight(1f))
                            Text(
                                assigned?.let { "${it.name} · EMA ${it.emaId}" } ?: "Not assigned",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.4f).padding(horizontal = 12.dp),
                            )
                            Button(enabled = !loading && savingId == null, onClick = { menuSlotId = slot.id }) { Text("Assign") }
                            DropdownMenu(expanded = menuSlotId == slot.id, onDismissRequest = { menuSlotId = null }) {
                                DropdownMenuItem(text = { Text("Clear assignment") }, onClick = { assign(slot.id, null) })
                                basePlayers.forEach { player ->
                                    DropdownMenuItem(text = { Text("${player.name} · EMA ${player.emaId}") }, onClick = { assign(slot.id, player) })
                                }
                            }
                        }
                        DataTableDivider()
                    }
                }
            }
        }
    }
}
