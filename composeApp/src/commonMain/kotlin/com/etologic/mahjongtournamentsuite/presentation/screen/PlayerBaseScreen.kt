package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared player base. All signed-in users can read it. Only global admins can change it. */
@Composable
fun PlayerBaseScreen(navController: NavHostController) {
    val presenter = koinInject<PlayerBasePresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val players by store.players.collectAsState()
    val admin by store.adminStatus.collectAsState()
    val canEdit = admin?.canEditPlayers == true
    var selectedEmaId by remember { mutableStateOf<String?>(null) }
    var emaId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = players.firstOrNull { it.emaId == selectedEmaId }

    fun select(player: Player?) {
        selectedEmaId = player?.emaId
        emaId = player?.emaId.orEmpty()
        name = player?.name.orEmpty()
        country = player?.country.orEmpty()
    }
    fun refresh() = scope.launch {
        loading = true
        when (val result = presenter.loadPlayers()) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        loading = false
    }
    fun save() = scope.launch {
        if (emaId.isBlank() || name.isBlank()) {
            error = "EMA number and name are required."
            return@launch
        }
        saving = true
        error = null
        val player = Player(emaId = emaId.trim(), name = name.trim(), country = country.trim())
        if (selected == null) {
            when (val result = presenter.createPlayer(player)) {
                is AppResult.Success -> {
                    refresh()
                    select(result.value)
                }
                is AppResult.Failure -> error = result.error.toUiMessage()
            }
        } else {
            when (val result = presenter.updatePlayer(player)) {
                is AppResult.Success -> {
                    refresh()
                    select(player)
                }
                is AppResult.Failure -> error = result.error.toUiMessage()
            }
        }
        saving = false
    }
    LaunchedEffect(Unit) { refresh() }

    AppScaffold(
        title = "Players",
        subtitle = if (canEdit) "Shared player base" else "Shared player base · read only",
        isLoading = loading || saving,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = ::refresh) },
    ) {
        ScreenColumn(maxWidth = 1200.dp, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { AppErrorMessage(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard(modifier = Modifier.weight(1f), title = "Player base", subtitle = "${players.size} EMA players") {
                    LazyColumn {
                        items(players, key = { it.emaId }) { player ->
                            DataTableRow(onClick = { select(player) }) {
                                Text(player.name, modifier = Modifier.weight(1f))
                                Text("EMA ${player.emaId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DataTableDivider()
                        }
                    }
                }
                SectionCard(modifier = Modifier.weight(1f), title = if (selected == null) "New player" else "Player details") {
                    OutlinedTextField(emaId, { emaId = it }, label = { Text("EMA number") }, enabled = canEdit && selected == null, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(country, { country = it }, label = { Text("Country code") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                    if (canEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !saving, onClick = ::save) { Text(if (selected == null) "Add player" else "Save") }
                            if (selected != null) Button(enabled = !saving, onClick = { select(null) }) { Text("New player") }
                        }
                    } else {
                        Text("Only admins and superadmins can edit the player base.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
