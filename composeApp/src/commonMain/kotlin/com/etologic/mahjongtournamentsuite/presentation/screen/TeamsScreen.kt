package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.presenter.TeamsPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TeamsScreen(
    navController: NavHostController,
    tournamentId: String,
) {
    val presenter = koinInject<TeamsPresenter>()
    val scope = rememberCoroutineScope()
    var teams by remember { mutableStateOf<List<TournamentTeam>>(emptyList()) }
    var slots by remember { mutableStateOf<List<TournamentPlayer>>(emptyList()) }
    var basePlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var editingTeamId by remember { mutableStateOf<Int?>(null) }
    var restoreTeamId by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val editFocusRequesters = remember(teams.map { it.id }) { teams.map { FocusRequester() } }

    fun refresh() = scope.launch {
        loading = true
        error = null
        when (val result = presenter.loadTeams(tournamentId)) {
            is AppResult.Success -> teams = result.value
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        when (val result = presenter.loadTournamentPlayers(tournamentId)) {
            is AppResult.Success -> slots = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        when (val result = presenter.loadBasePlayers()) {
            is AppResult.Success -> basePlayers = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        when (val result = presenter.loadTables(tournamentId)) {
            is AppResult.Success -> tables = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        loading = false
    }

    LaunchedEffect(tournamentId) { refresh() }

    LaunchedEffect(loading, teams.map { it.id }) {
        if (!loading && teams.isNotEmpty() && editingTeamId == null) {
            val restoreIndex = restoreTeamId?.let { id -> teams.indexOfFirst { it.id == id } } ?: -1
            val index = restoreIndex.takeIf { it >= 0 } ?: 0
            listState.scrollToItem(index)
            editFocusRequesters.getOrNull(index)?.requestFocus()
            restoreTeamId = null
        }
    }

    val editingTeam = editingTeamId?.let { id -> teams.firstOrNull { it.id == id } }
    if (editingTeam != null) {
        TeamEditorDialog(
            team = editingTeam,
            teams = teams,
            slots = slots,
            basePlayers = basePlayers,
            assignmentsLocked = tables.any { it.hasProgress },
            saving = saving,
            onSave = { name, emaIds ->
                scope.launch {
                    saving = true
                    error = null
                    when (val result = presenter.updateTeam(
                        tournamentId = tournamentId,
                        teamId = editingTeam.id,
                        name = name,
                        emaIds = emaIds,
                    )) {
                        is AppResult.Success -> {
                            restoreTeamId = editingTeam.id
                            editingTeamId = null
                            refresh()
                        }
                        is AppResult.Failure -> error = result.error.toUiMessage()
                    }
                    saving = false
                }
            },
            onDismiss = {
                restoreTeamId = editingTeam.id
                editingTeamId = null
            },
        )
    }

    AppScaffold(
        title = "Tournament teams",
        isLoading = loading || saving,
        onBack = { navController.popBackStack() },
        actions = { AppTopBarActions(onRefresh = ::refresh) },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { AppErrorMessage(it) }
            SectionCard {
                if (teams.isEmpty() && !loading) {
                    Text("This tournament does not use teams.")
                }
                Column(Modifier.fillMaxWidth()) {
                    DataTableHeaderRow {
                        TeamHeader("Team ID", Modifier.width(90.dp))
                        TeamHeader("Name", Modifier.weight(1f))
                        TeamHeader("Players", Modifier.weight(1.6f))
                        Spacer(Modifier.width(96.dp))
                    }
                    DataTableDivider()
                    LazyColumnWithScrollbar(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        itemsIndexed(teams, key = { _, team -> team.id }) { index, team ->
                            val assignedNames = team.playerIds.mapNotNull { playerId ->
                                val emaId = slots.firstOrNull { it.id == playerId }?.assignedEmaId
                                emaId?.let { id -> basePlayers.firstOrNull { it.emaId == id }?.name }
                            }
                            DataTableRow(
                                onClick = { editingTeamId = team.id },
                                clickFocusable = false,
                            ) {
                                Text(team.id.toString(), Modifier.width(90.dp))
                                Text(
                                    text = team.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (assignedNames.isEmpty()) {
                                        "No players assigned"
                                    } else {
                                        assignedNames.joinToString(", ")
                                    },
                                    modifier = Modifier.weight(1.6f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                FocusedButton(
                                    onClick = { editingTeamId = team.id },
                                    focusRequester = editFocusRequesters[index],
                                    buttonModifier = Modifier.onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.DirectionUp -> {
                                                editFocusRequesters.getOrNull(index - 1)?.requestFocus()
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                editFocusRequesters.getOrNull(index + 1)?.requestFocus()
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                ) { Text("Edit") }
                            }
                            DataTableDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamEditorDialog(
    team: TournamentTeam,
    teams: List<TournamentTeam>,
    slots: List<TournamentPlayer>,
    basePlayers: List<Player>,
    assignmentsLocked: Boolean,
    saving: Boolean,
    onSave: (String, List<String?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val teamSlots = remember(team, slots) {
        team.playerIds.mapNotNull { playerId -> slots.firstOrNull { it.id == playerId } }
    }
    val selectedEmaIds = remember(team.id, teamSlots.map { it.assignedEmaId }) {
        mutableStateListOf<String?>().apply { addAll(teamSlots.map { it.assignedEmaId }) }
    }
    val originalEmaIds = remember(team.id, teamSlots.map { it.assignedEmaId }) {
        teamSlots.map { it.assignedEmaId }
    }
    var name by remember(team.id) { mutableStateOf(team.name) }
    var activeSlotIndex by remember(team.id) { mutableStateOf(0) }
    var query by remember(team.id) { mutableStateOf("") }
    var moveWarning by remember(team.id) { mutableStateOf<String?>(null) }
    val nameFocusRequester = remember(team.id) { FocusRequester() }
    val playersByEma = remember(basePlayers) { basePlayers.associateBy { it.emaId } }
    val slotsByEma = remember(slots) { slots.mapNotNull { slot -> slot.assignedEmaId?.let { it to slot } }.toMap() }
    val teamNamesById = remember(teams) { teams.associate { it.id to it.name } }
    val normalizedQuery = normalizeSearchText(query.trim())
    val filteredPlayers = remember(basePlayers, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            basePlayers
        } else {
            basePlayers.filter { player ->
                normalizeSearchText(player.name).contains(normalizedQuery) ||
                    normalizeSearchText(player.emaId).contains(normalizedQuery) ||
                    normalizeSearchText(player.country).contains(normalizedQuery)
            }
        }
    }
    val membershipChanged = selectedEmaIds.toList() != originalEmaIds

    LaunchedEffect(team.id) { nameFocusRequester.requestFocus() }

    fun selectPlayer(player: Player) {
        if (assignmentsLocked) return
        val previousSelectedIndex = selectedEmaIds.indexOf(player.emaId)
        if (previousSelectedIndex >= 0 && previousSelectedIndex != activeSlotIndex) {
            val displaced = selectedEmaIds[activeSlotIndex]
            selectedEmaIds[previousSelectedIndex] = displaced
        }
        selectedEmaIds[activeSlotIndex] = player.emaId

        val sourceSlot = slotsByEma[player.emaId]
        moveWarning = if (sourceSlot != null && sourceSlot.team != team.id) {
            val sourceTeam = teamNamesById[sourceSlot.team] ?: "Team ${sourceSlot.team}"
            "${player.name} is assigned to $sourceTeam as player ${sourceSlot.id}. " +
                "Saving will change the player's ID and tables. An occupied source slot will receive the displaced player."
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 920.dp).appFocusGroup(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Edit team ${team.id}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Team name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                )
                if (assignmentsLocked) {
                    Text(
                        "Player assignments are locked because table results have started. The name can still change.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("Select a team slot, then select a player.", style = MaterialTheme.typography.labelLarge)
                teamSlots.forEachIndexed { index, slot ->
                    val selected = selectedEmaIds.getOrNull(index)?.let(playersByEma::get)
                    FocusedOutlinedButton(
                        onClick = { activeSlotIndex = index },
                        enabled = !saving,
                        buttonModifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Player ${slot.id}: ${selected?.name ?: "Not assigned"}" +
                                if (activeSlotIndex == index) "  • selected" else "",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search players") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FocusedTextButton(
                        onClick = {
                            if (!assignmentsLocked && activeSlotIndex in selectedEmaIds.indices) {
                                selectedEmaIds[activeSlotIndex] = null
                                moveWarning = null
                            }
                        },
                        enabled = !assignmentsLocked && !saving,
                    ) { Text("Clear slot") }
                }
                moveWarning?.let { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.error)
                }
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    LazyColumnWithScrollbar(state = rememberLazyListState()) {
                        itemsIndexed(filteredPlayers, key = { _, player -> player.emaId }) { _, player ->
                            val assignedSlot = slotsByEma[player.emaId]
                            val assignedTeam = assignedSlot?.let { teamNamesById[it.team] ?: "Team ${it.team}" }
                            DataTableRow(
                                onClick = { selectPlayer(player) },
                                clickFocusable = !assignmentsLocked,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(player.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        buildString {
                                            append("EMA ${player.emaId}")
                                            if (assignedTeam != null) append(" • $assignedTeam • Player ${assignedSlot.id}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DataTableDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = { onSave(name.trim(), selectedEmaIds.toList()) },
                enabled = !saving && name.trim().isNotEmpty() && (!assignmentsLocked || !membershipChanged),
            ) { Text("Save team") }
        },
        dismissButton = {
            FocusedTextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun TeamHeader(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
