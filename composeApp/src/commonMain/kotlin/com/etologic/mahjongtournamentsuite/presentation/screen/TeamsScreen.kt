package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedAssistChip
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
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
@OptIn(ExperimentalLayoutApi::class)
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialFocusApplied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val slotsById = remember(slots) { slots.associateBy { it.id } }
    val basePlayersByEma = remember(basePlayers) { basePlayers.associateBy { it.emaId } }
    val playersByTeamId = remember(teams, slotsById, basePlayersByEma) {
        teams.associate { team ->
            team.id to team.playerIds.map { playerId ->
                val emaId = slotsById[playerId]?.assignedEmaId
                val player = emaId?.let(basePlayersByEma::get)
                val nonMember = slotsById[playerId]?.nonMember
                TeamPlayerSummary(
                    id = playerId,
                    name = player?.displayName ?: nonMember?.displayName,
                    country = player?.country ?: nonMember?.country.orEmpty(),
                )
            }
        }
    }
    val tournamentCountryCodes = remember(playersByTeamId) {
        playersByTeamId.values
            .flatten()
            .map { it.country.trim().uppercase() }
            .filter { it.isNotEmpty() && it != "EU" }
            .distinct()
            .sorted()
    }
    val normalizedQuery = normalizeSearchText(searchQuery.trim())
    val filteredTeams = remember(teams, playersByTeamId, normalizedQuery, selectedCountryCodes) {
        teams.filter { team ->
            val teamPlayers = playersByTeamId[team.id].orEmpty()
            val matchesSearch = normalizedQuery.isEmpty() ||
                normalizeSearchText(team.name).contains(normalizedQuery) ||
                team.id.toString().contains(normalizedQuery) ||
                teamPlayers.any { player ->
                    player.id.toString().contains(normalizedQuery) ||
                        normalizeSearchText(player.name.orEmpty()).contains(normalizedQuery)
                }
            val matchesCountry = selectedCountryCodes.isEmpty() || teamPlayers.any { player ->
                player.country.trim().uppercase() in selectedCountryCodes
            }
            matchesSearch && matchesCountry
        }
    }
    val editFocusRequesters = remember(filteredTeams.map { it.id }) {
        filteredTeams.map { FocusRequester() }
    }

    fun refresh(force: Boolean = false) = scope.launch {
        loading = true
        error = null
        when (val result = presenter.loadTeams(tournamentId, force)) {
            is AppResult.Success -> teams = result.value
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        when (val result = presenter.loadTournamentPlayers(tournamentId, force)) {
            is AppResult.Success -> slots = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        when (val result = presenter.loadBasePlayers(force)) {
            is AppResult.Success -> basePlayers = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        when (val result = presenter.loadTables(tournamentId, force)) {
            is AppResult.Success -> tables = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        loading = false
    }

    LaunchedEffect(tournamentId) { refresh() }

    LaunchedEffect(tournamentCountryCodes) {
        selectedCountryCodes = selectedCountryCodes.intersect(tournamentCountryCodes.toSet())
    }

    LaunchedEffect(loading, teams.isNotEmpty()) {
        if (!loading && teams.isNotEmpty() && !initialFocusApplied) {
            initialFocusApplied = true
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(loading, restoreTeamId, filteredTeams.map { it.id }, editingTeamId) {
        val teamIdToRestore = restoreTeamId
        if (!loading && teamIdToRestore != null && editingTeamId == null) {
            val restoreIndex = filteredTeams.indexOfFirst { it.id == teamIdToRestore }
            if (restoreIndex >= 0) {
                listState.scrollToItem(restoreIndex)
                editFocusRequesters.getOrNull(restoreIndex)?.requestFocus()
            } else {
                searchFocusRequester.requestFocus()
            }
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
                            refresh(force = true)
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
        actions = { AppTopBarActions(onRefresh = { refresh(force = true) }) },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let {
                AppErrorDialog(
                    message = it,
                    onDismiss = { error = null },
                )
            }
            if (teams.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by team or player name or ID") },
                    singleLine = true,
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            FocusedIconButton(
                                onClick = {
                                    searchQuery = ""
                                    searchFocusRequester.requestFocus()
                                },
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                )
                if (tournamentCountryCodes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Filter by country",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tournamentCountryCodes.forEach { code ->
                                val isSelected = code in selectedCountryCodes
                                FocusedTextButton(
                                    onClick = {
                                        selectedCountryCodes = if (isSelected) {
                                            selectedCountryCodes - code
                                        } else {
                                            selectedCountryCodes + code
                                        }
                                    },
                                    shape = CircleShape,
                                    colors = if (isSelected) {
                                        ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    CountryFlag(
                                        code = code,
                                        contentDescription = if (isSelected) {
                                            "Remove $code country filter"
                                        } else {
                                            "Filter by $code"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SectionCard {
                if (teams.isEmpty() && !loading) {
                    Text("This tournament does not use teams.")
                } else if (filteredTeams.isEmpty() && !loading) {
                    Text("No teams match the current filters.")
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
                        itemsIndexed(filteredTeams, key = { _, team -> team.id }) { index, team ->
                            val assignedPlayers = playersByTeamId[team.id].orEmpty()
                            DataTableRow(
                                onClick = { editingTeamId = team.id },
                                clickFocusable = false,
                            ) {
                                Text(
                                    text = team.id.toString(),
                                    modifier = Modifier.width(90.dp),
                                )
                                Text(
                                    text = team.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Column(
                                    modifier = Modifier.weight(1.6f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    assignedPlayers.forEach { player ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(player.id.toString())
                                            CountryFlag(
                                                code = player.country,
                                                width = 16.dp,
                                                contentDescription = player.country.ifBlank { "No country" },
                                            )
                                            Text(
                                                text = player.name ?: "Not assigned",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
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
    var playerPickerSlotIndex by remember(team.id) { mutableStateOf<Int?>(null) }
    var restoreSlotIndex by remember(team.id) { mutableStateOf<Int?>(null) }
    var moveWarning by remember(team.id) { mutableStateOf<String?>(null) }
    val nameFocusRequester = remember(team.id) { FocusRequester() }
    val slotFocusRequesters = remember(team.id, teamSlots.map { it.id }) {
        teamSlots.map { FocusRequester() }
    }
    val playersByEma = remember(basePlayers) { basePlayers.associateBy { it.emaId } }
    val slotsByEma = remember(slots) { slots.mapNotNull { slot -> slot.assignedEmaId?.let { it to slot } }.toMap() }
    val teamNamesById = remember(teams) { teams.associate { it.id to it.name } }
    val membershipChanged = selectedEmaIds.toList() != originalEmaIds

    LaunchedEffect(team.id) { nameFocusRequester.requestFocus() }
    LaunchedEffect(playerPickerSlotIndex, restoreSlotIndex) {
        if (playerPickerSlotIndex == null) {
            restoreSlotIndex?.let { index -> slotFocusRequesters.getOrNull(index)?.requestFocus() }
            restoreSlotIndex = null
        }
    }

    fun selectPlayer(slotIndex: Int, player: Player) {
        if (assignmentsLocked) return
        val previousSelectedIndex = selectedEmaIds.indexOf(player.emaId)
        if (previousSelectedIndex >= 0 && previousSelectedIndex != slotIndex) {
            val displaced = selectedEmaIds[slotIndex]
            selectedEmaIds[previousSelectedIndex] = displaced
        }
        selectedEmaIds[slotIndex] = player.emaId

        val sourceSlot = slotsByEma[player.emaId]
        moveWarning = if (sourceSlot != null && sourceSlot.team != team.id) {
            val sourceTeam = teamNamesById[sourceSlot.team] ?: "Team ${sourceSlot.team}"
            "${player.displayName} is assigned to $sourceTeam as player ${sourceSlot.id}. " +
                "Saving will change the player's ID and tables. An occupied source slot will receive the displaced player."
        } else {
            null
        }
    }

    playerPickerSlotIndex?.let { slotIndex ->
        val slot = teamSlots.getOrNull(slotIndex)
        if (slot != null) {
            PlayerPickerDialog(
                slot = slot,
                selectedEmaId = selectedEmaIds.getOrNull(slotIndex),
                players = basePlayers,
                slotsByEma = slotsByEma,
                teamNamesById = teamNamesById,
                saving = saving,
                onSelect = { player ->
                    selectPlayer(slotIndex, player)
                    restoreSlotIndex = slotIndex
                    playerPickerSlotIndex = null
                },
                onClear = {
                    selectedEmaIds[slotIndex] = null
                    moveWarning = null
                    restoreSlotIndex = slotIndex
                    playerPickerSlotIndex = null
                },
                onDismiss = {
                    restoreSlotIndex = slotIndex
                    playerPickerSlotIndex = null
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        title = { Text("Edit team ${team.id}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Team name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Team members",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (assignmentsLocked) {
                        Text(
                            text = "Player assignments are locked because table results have started.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        teamSlots.forEachIndexed { index, slot ->
                            val selected = selectedEmaIds.getOrNull(index)?.let(playersByEma::get)
                            val nonMemberName = if (selected == null) slot.nonMember?.displayName else null
                            FocusedAssistChip(
                                onClick = { playerPickerSlotIndex = index },
                                enabled = !saving && !assignmentsLocked,
                                focusRequester = slotFocusRequesters[index],
                                label = {
                                    Text(
                                        text = "${slot.id} - ${selected?.displayName ?: nonMemberName ?: "Not assigned"}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                moveWarning?.let { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.error)
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
private fun PlayerPickerDialog(
    slot: TournamentPlayer,
    selectedEmaId: String?,
    players: List<Player>,
    slotsByEma: Map<String, TournamentPlayer>,
    teamNamesById: Map<Int, String>,
    saving: Boolean,
    onSelect: (Player) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(slot.id) { mutableStateOf("") }
    val searchFocusRequester = remember(slot.id) { FocusRequester() }
    val listState = rememberLazyListState()
    val normalizedQuery = normalizeSearchText(query.trim())
    val filteredPlayers = remember(players, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            players
        } else {
            players.filter { player ->
                normalizeSearchText(player.displayName).contains(normalizedQuery) ||
                    normalizeSearchText(player.emaId).contains(normalizedQuery) ||
                    normalizeSearchText(player.country).contains(normalizedQuery)
            }
        }
    }

    LaunchedEffect(slot.id) { searchFocusRequester.requestFocus() }
    LaunchedEffect(normalizedQuery) {
        if (filteredPlayers.isNotEmpty()) listState.scrollToItem(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.appFocusGroup(),
        title = { Text("Select player ${slot.id}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search players") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                )
                if (filteredPlayers.isEmpty()) {
                    Text(
                        text = "No players match the search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        LazyColumnWithScrollbar(state = listState) {
                            itemsIndexed(filteredPlayers, key = { _, player -> player.emaId }) { _, player ->
                                val assignedSlot = slotsByEma[player.emaId]
                                val assignedTeam = assignedSlot?.let {
                                    teamNamesById[it.team] ?: "Team ${it.team}"
                                }
                                DataTableRow(
                                    onClick = { onSelect(player) },
                                    highlighted = player.emaId == selectedEmaId,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(player.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            buildString {
                                                append("EMA ${player.emaId}")
                                                if (assignedTeam != null) {
                                                    append(" • $assignedTeam • Player ${assignedSlot.id}")
                                                }
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
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = onClear,
                enabled = !saving && selectedEmaId != null,
            ) { Text("Clear slot") }
        },
        dismissButton = {
            FocusedTextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

private data class TeamPlayerSummary(
    val id: Int,
    val name: String?,
    val country: String,
)

@Composable
private fun TeamHeader(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
