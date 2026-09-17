package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorMessage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import coil3.compose.AsyncImage

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
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var assignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var clearAssignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var assignmentSearchQuery by remember { mutableStateOf("") }

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
        when (val result = presenter.loadCountries()) {
            is AppResult.Success -> countries = result.value
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
        assignmentSlotId = null
        clearAssignmentSlotId = null
        savingId = null
    }

    LaunchedEffect(tournamentId) { refresh() }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val players = slots[tournamentId].orEmpty()
    val filteredBasePlayers = remember(basePlayers, countries, assignmentSearchQuery) {
        val query = normalizeSearchText(assignmentSearchQuery.trim())
        if (query.isEmpty()) {
            basePlayers
        } else {
            basePlayers.filter { player ->
                val countryName = countries.firstOrNull { it.code == player.country }?.name.orEmpty()
                normalizeSearchText(player.name).contains(query) ||
                    normalizeSearchText(player.emaId).contains(query) ||
                    normalizeSearchText(player.country).contains(query) ||
                    normalizeSearchText(countryName).contains(query)
            }
        }
    }
    val assignmentSlot = assignmentSlotId?.let { id -> players.firstOrNull { it.id == id } }
    val clearAssignmentSlot = clearAssignmentSlotId?.let { id -> players.firstOrNull { it.id == id } }

    if (assignmentSlot != null) {
        AlertDialog(
            onDismissRequest = { assignmentSlotId = null },
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 900.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Assign EMA player") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Select an EMA player for tournament player ${assignmentSlot.id}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = assignmentSearchQuery,
                        onValueChange = { assignmentSearchQuery = it },
                        label = { Text("Search by name, country, or EMA number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(Modifier.fillMaxWidth()) {
                        DataTableHeaderRow {
                            TournamentPlayerHeader("Photo", Modifier.width(AssignmentPhotoColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader("Country", Modifier.width(AssignmentCountryColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader("Name", Modifier.weight(1f))
                            TournamentPlayerHeader("EMA number", Modifier.width(AssignmentEmaColumnWidth))
                        }
                        DataTableDivider()
                        if (filteredBasePlayers.isEmpty()) {
                            Text(
                                text = "No EMA players match this search.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 410.dp)) {
                                items(filteredBasePlayers, key = { it.emaId }) { player ->
                                    DataTableRow(onClick = { assign(assignmentSlot.id, player) }) {
                                        TournamentPlayerPhoto(
                                            photoUrl = player.photoUrl,
                                            playerName = player.name,
                                            columnWidth = AssignmentPhotoColumnWidth,
                                        )
                                        Box(
                                            modifier = Modifier.width(AssignmentCountryColumnWidth),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(countryFlag(player.country))
                                        }
                                        Text(player.name, modifier = Modifier.weight(1f))
                                        Text(
                                            player.emaId,
                                            modifier = Modifier.width(AssignmentEmaColumnWidth),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    DataTableDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignmentSlotId = null }) { Text("Cancel") }
            },
        )
    }

    if (clearAssignmentSlot != null) {
        val assigned = clearAssignmentSlot.assignedEmaId?.let(playersByEma::get)
        AlertDialog(
            onDismissRequest = { clearAssignmentSlotId = null },
            title = { Text("Clear assignment?") },
            text = {
                Text(
                    "Remove ${assigned?.name ?: "EMA ${clearAssignmentSlot.assignedEmaId}"} " +
                        "from tournament player ${clearAssignmentSlot.id}?",
                )
            },
            confirmButton = {
                Button(
                    enabled = savingId == null,
                    onClick = { assign(clearAssignmentSlot.id, null) },
                ) { Text("Clear assignment") }
            },
            dismissButton = {
                TextButton(onClick = { clearAssignmentSlotId = null }) { Text("Cancel") }
            },
        )
    }

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
                Column(Modifier.fillMaxWidth()) {
                    DataTableHeaderRow {
                        TournamentPlayerHeader("Photo", Modifier.width(TournamentPlayerPhotoColumnWidth))
                        TournamentPlayerHeader("Country", Modifier.width(TournamentPlayerCountryColumnWidth), TextAlign.Center)
                        TournamentPlayerHeader("Player ID", Modifier.width(TournamentPlayerIdColumnWidth))
                        TournamentPlayerHeader("EMA player", Modifier.weight(1.4f).padding(horizontal = 12.dp))
                        TournamentPlayerHeader("Action", Modifier.width(TournamentPlayerActionColumnWidth))
                    }
                    DataTableDivider()
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(players, key = { it.id }) { slot ->
                            val assigned = slot.assignedEmaId?.let(playersByEma::get)
                            val playerCountry = assigned?.country?.takeIf { it.isNotBlank() } ?: slot.country
                            DataTableRow {
                                Box(
                                    modifier = Modifier
                                        .width(TournamentPlayerPhotoColumnWidth)
                                        .height(TournamentPlayerPhotoSize),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    TournamentPlayerPhoto(
                                        photoUrl = assigned?.photoUrl,
                                        playerName = assigned?.name.orEmpty(),
                                        columnWidth = TournamentPlayerPhotoColumnWidth,
                                    )
                                }
                                Box(
                                    modifier = Modifier.width(TournamentPlayerCountryColumnWidth),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(countryFlag(playerCountry))
                                }
                                Text(slot.id.toString(), modifier = Modifier.width(TournamentPlayerIdColumnWidth))
                                Text(
                                    assigned?.let { "${it.name} · ${it.emaId}" } ?: "Not assigned",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1.4f).padding(horizontal = 12.dp),
                                )
                                Row(
                                    modifier = Modifier.width(TournamentPlayerActionColumnWidth),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        enabled = !loading && savingId == null,
                                        onClick = {
                                            assignmentSearchQuery = ""
                                            assignmentSlotId = slot.id
                                        },
                                    ) { Text("Assign", maxLines = 1, overflow = TextOverflow.Clip) }
                                    if (slot.assignedEmaId != null) {
                                        OutlinedButton(
                                            enabled = !loading && savingId == null,
                                            onClick = { clearAssignmentSlotId = slot.id },
                                        ) { Text("Clear", maxLines = 1, overflow = TextOverflow.Clip) }
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
}

private val TournamentPlayerPhotoSize = 40.dp
private val TournamentPlayerPhotoColumnWidth = 48.dp
private val TournamentPlayerCountryColumnWidth = 64.dp
private val TournamentPlayerIdColumnWidth = 72.dp
private val TournamentPlayerActionColumnWidth = 208.dp
private val AssignmentPhotoColumnWidth = 48.dp
private val AssignmentCountryColumnWidth = 64.dp
private val AssignmentEmaColumnWidth = 96.dp

@Composable
private fun TournamentPlayerHeader(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
}

@Composable
private fun TournamentPlayerPhoto(
    photoUrl: String?,
    playerName: String,
    columnWidth: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.width(columnWidth).height(TournamentPlayerPhotoSize),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(TournamentPlayerPhotoSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "No player photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TournamentPlayerPhotoSize * 0.62f),
                )
            }
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Photo of $playerName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(TournamentPlayerPhotoSize),
            )
        }
    }
}

private fun countryFlag(code: String): String {
    val normalized = code.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return "🌐"
    return normalized.map { regionalIndicator(it) }.joinToString("")
}

private fun regionalIndicator(letter: Char): String {
    val codePoint = 0x1F1E6 + (letter.code - 'A'.code)
    val offset = codePoint - 0x10000
    val high = ((offset / 0x400) + 0xD800).toChar()
    val low = ((offset % 0x400) + 0xDC00).toChar()
    return charArrayOf(high, low).concatToString()
}
