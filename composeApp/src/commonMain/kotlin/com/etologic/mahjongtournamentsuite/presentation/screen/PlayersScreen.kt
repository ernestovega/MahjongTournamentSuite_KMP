package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
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
    val tournaments by store.tournaments.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var savingId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var teams by remember { mutableStateOf<List<TournamentTeam>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var assignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var nonMemberSlotId by remember { mutableStateOf<Int?>(null) }
    var nonMemberFirstName by remember { mutableStateOf("") }
    var nonMemberLastName by remember { mutableStateOf("") }
    var nonMemberCountry by remember { mutableStateOf("") }
    var nonMemberError by remember { mutableStateOf<String?>(null) }
    var clearAssignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var assignmentSearchQuery by remember { mutableStateOf("") }
    val assignmentsListState = rememberLazyListState()
    var initialAssignmentScrollPending by rememberSaveable(tournamentId) { mutableStateOf(true) }
    var assignmentFocusRestoreSlotId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }

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
        when (val result = presenter.loadTeams(tournamentId)) {
            is AppResult.Success -> teams = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiMessage()
        }
        when (val result = presenter.loadTables(tournamentId)) {
            is AppResult.Success -> {
                tables = result.value
                store.upsertTables(tournamentId, roundId = null, tables = result.value)
            }
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

    fun assignNonMember(slotId: Int) = scope.launch {
        val firstName = nonMemberFirstName.trim()
        val lastName = nonMemberLastName.trim()
        val country = nonMemberCountry.trim().uppercase()
        nonMemberError = when {
            firstName.isEmpty() -> "First name is required."
            lastName.isEmpty() -> "Last name is required."
            !country.matches(Regex("^[A-Z]{3}$")) -> "Country must use a three-letter EMA code."
            else -> null
        }
        if (nonMemberError != null) return@launch
        savingId = slotId
        when (val result = presenter.assignPlayer(
            tournamentId,
            slotId,
            emaId = null,
            nonMember = NonMemberPlayer(firstName, lastName, country),
        )) {
            is AppResult.Success -> {
                nonMemberSlotId = null
                refresh()
            }
            is AppResult.Failure -> {
                nonMemberSlotId = null
                error = result.error.toUiMessage()
            }
        }
        savingId = null
    }

    LaunchedEffect(tournamentId) { refresh() }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val players = slots[tournamentId].orEmpty()

    fun exportIdCards() = scope.launch {
        val unassigned = players.filterNot { it.isAssigned }
        if (unassigned.isNotEmpty()) {
            error = "Assign an EMA player or non-member to every tournament slot before creating ID cards."
            return@launch
        }

        loading = true
        error = null
        when (val result = presenter.generateIdCards(tournamentId)) {
            is AppResult.Success -> {
                val tournament = tournaments.firstOrNull { it.id == tournamentId }
                val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                val safeName = baseName.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "tournament" }
                val fileName = listOfNotNull(safeName, year, "id-cards").joinToString("-") + ".pdf"
                saveBinaryFile(fileName, result.value, "application/pdf")
            }
            is AppResult.Failure -> error = result.error.toUiMessage()
        }
        loading = false
    }

    val isTeamsTournament = tournaments.firstOrNull { it.id == tournamentId }?.isTeams == true
    val teamNamesById = remember(teams) { teams.associate { it.id to it.name } }
    val tableNumbersByPlayerId = remember(tables) {
        buildMap<Int, String> {
            tables
                .sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
                .flatMap { table -> table.playerIds.map { playerId -> playerId to table.tableId } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .forEach { (playerId, tableIds) ->
                    put(playerId, tableIds.joinToString(" · "))
                }
        }
    }
    val assignedEmaIds = remember(players) { players.mapNotNullTo(mutableSetOf()) { it.assignedEmaId } }
    val filteredBasePlayers = remember(basePlayers, countries, assignmentSearchQuery, assignedEmaIds) {
        val query = normalizeSearchText(assignmentSearchQuery.trim())
        val availablePlayers = basePlayers.filterNot { it.emaId in assignedEmaIds }
        if (query.isEmpty()) {
            availablePlayers
        } else {
            availablePlayers.filter { player ->
                val countryName = countries.firstOrNull { it.code == player.country }?.name.orEmpty()
                normalizeSearchText(player.displayName).contains(query) ||
                    normalizeSearchText(player.emaId).contains(query) ||
                    normalizeSearchText(player.country).contains(query) ||
                    normalizeSearchText(countryName).contains(query)
            }
        }
    }
    val assignmentSlot = assignmentSlotId?.let { id -> players.firstOrNull { it.id == id } }
    val clearAssignmentSlot = clearAssignmentSlotId?.let { id -> players.firstOrNull { it.id == id } }
    val assignFocusRequesters = remember(players.map { it.id }) { players.map { FocusRequester() } }
    val clearFocusRequesters = remember(players.map { it.id }) { players.map { FocusRequester() } }
    var focusedAssignRowIndex by remember { mutableStateOf<Int?>(null) }
    var focusedClearRowIndex by remember { mutableStateOf<Int?>(null) }

    fun openAssignment(slotId: Int) {
        assignmentSearchQuery = ""
        assignmentFocusRestoreSlotId = slotId
        assignmentSlotId = slotId
    }

    fun openClearAssignment(slotId: Int) {
        assignmentFocusRestoreSlotId = slotId
        clearAssignmentSlotId = slotId
    }

    suspend fun revealPlayerAction(index: Int) {
        val initialLayout = assignmentsListState.layoutInfo
        val initialVisibleItems = initialLayout.visibleItemsInfo
        val initialTarget = initialVisibleItems.firstOrNull { it.index == index }

        when {
            initialTarget != null -> {
                val scrollDistance = when {
                    initialTarget.offset < initialLayout.viewportStartOffset -> {
                        initialTarget.offset - initialLayout.viewportStartOffset
                    }
                    initialTarget.offset + initialTarget.size > initialLayout.viewportEndOffset -> {
                        initialTarget.offset + initialTarget.size - initialLayout.viewportEndOffset
                    }
                    else -> 0
                }
                if (scrollDistance != 0) assignmentsListState.scrollBy(scrollDistance.toFloat())
            }
            index < assignmentsListState.firstVisibleItemIndex -> {
                assignmentsListState.scrollToItem(index)
            }
            initialVisibleItems.isNotEmpty() -> {
                val visibleItemCount = initialVisibleItems.last().index - initialVisibleItems.first().index + 1
                val firstItemToShow = (index - visibleItemCount + 1).coerceAtLeast(0)
                assignmentsListState.scrollToItem(firstItemToShow)
                withFrameNanos { }

                val updatedLayout = assignmentsListState.layoutInfo
                val updatedTarget = updatedLayout.visibleItemsInfo.firstOrNull { it.index == index }
                if (updatedTarget != null) {
                    val overflow = updatedTarget.offset + updatedTarget.size - updatedLayout.viewportEndOffset
                    if (overflow > 0) assignmentsListState.scrollBy(overflow.toFloat())
                }
            }
            else -> assignmentsListState.scrollToItem(index)
        }
        withFrameNanos { }
    }

    fun requestPlayerActionFocus(index: Int) {
        if (index !in players.indices) return
        scope.launch {
            revealPlayerAction(index)
            if (players[index].isAssigned) {
                clearFocusRequesters[index].requestFocus()
            } else {
                assignFocusRequesters[index].requestFocus()
            }
        }
    }

    LaunchedEffect(loading, players) {
        if (initialAssignmentScrollPending && !loading && players.isNotEmpty()) {
            val firstUnassignedIndex = players.indexOfFirst { !it.isAssigned }
            val initialFocusIndex = firstUnassignedIndex.takeIf { it >= 0 } ?: 0
            revealPlayerAction(initialFocusIndex)
            if (!players[initialFocusIndex].isAssigned) {
                assignFocusRequesters[initialFocusIndex].requestFocus()
            } else {
                clearFocusRequesters[initialFocusIndex].requestFocus()
            }
            initialAssignmentScrollPending = false
        }
    }

    LaunchedEffect(assignmentSlotId, clearAssignmentSlotId, loading, savingId, players.map { it.id }) {
        val slotId = assignmentFocusRestoreSlotId
        if (
            assignmentSlotId == null &&
            clearAssignmentSlotId == null &&
            !loading &&
            savingId == null &&
            slotId != null
        ) {
            assignmentFocusRestoreSlotId = null
            val index = players.indexOfFirst { it.id == slotId }
            requestPlayerActionFocus(index)
        }
    }

    if (assignmentSlot != null) {
        val searchFocusRequester = remember(assignmentSlot.id) { FocusRequester() }
        val clearSearchFocusRequester = remember(assignmentSlot.id) { FocusRequester() }
        val cancelFocusRequester = remember(assignmentSlot.id) { FocusRequester() }
        val playerFocusRequesters = remember(assignmentSlot.id, filteredBasePlayers.map(Player::emaId)) {
            filteredBasePlayers.map { FocusRequester() }
        }
        val playerListState = rememberLazyListState()
        var focusedPlayerIndex by remember(assignmentSlot.id) { mutableStateOf(0) }
        var searchFieldFocused by remember(assignmentSlot.id) { mutableStateOf(false) }

        fun selectedPlayerIndex(): Int = focusedPlayerIndex
            .coerceAtMost(playerFocusRequesters.lastIndex)
            .coerceAtLeast(0)

        fun requestPlayerFocus(index: Int) {
            if (index !in playerFocusRequesters.indices) return
            focusedPlayerIndex = index
            scope.launch {
                playerListState.scrollToItem(index)
                playerFocusRequesters[index].requestFocus()
            }
        }

        LaunchedEffect(assignmentSlot.id) {
            searchFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { assignmentSlotId = null },
            modifier = Modifier.appFocusGroup(),
            title = { Text("ASSIGN EMA PLAYER TO PLAYER ${assignmentSlot.id}") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = assignmentSearchQuery,
                        onValueChange = { assignmentSearchQuery = it },
                        label = { Text("Search by name, country, or EMA number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFieldFocused = it.isFocused }
                            .focusProperties {
                                next = if (assignmentSearchQuery.isNotEmpty()) {
                                    clearSearchFocusRequester
                                } else {
                                    playerFocusRequesters.firstOrNull() ?: cancelFocusRequester
                                }
                                previous = cancelFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (!searchFieldFocused) return@onPreviewKeyEvent false
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Tab -> {
                                        if (event.isShiftPressed) {
                                            cancelFocusRequester.requestFocus()
                                        } else if (assignmentSearchQuery.isNotEmpty()) {
                                            clearSearchFocusRequester.requestFocus()
                                        } else if (playerFocusRequesters.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(selectedPlayerIndex())
                                        }
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter, Key.DirectionDown -> {
                                        if (playerFocusRequesters.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                        } else {
                                            requestPlayerFocus(0)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        cancelFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (assignmentSearchQuery.isEmpty()) {
                                            cancelFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (assignmentSearchQuery.isEmpty()) {
                                            if (playerFocusRequesters.isEmpty()) {
                                                cancelFocusRequester.requestFocus()
                                            } else {
                                                requestPlayerFocus(selectedPlayerIndex())
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            },
                        trailingIcon = if (assignmentSearchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        assignmentSearchQuery = ""
                                        searchFocusRequester.requestFocus()
                                    },
                                    buttonModifier = Modifier
                                        .focusRequester(clearSearchFocusRequester)
                                        .focusProperties {
                                            previous = searchFocusRequester
                                            next = playerFocusRequesters.firstOrNull() ?: cancelFocusRequester
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) {
                                                return@onPreviewKeyEvent false
                                            }
                                            when (event.key) {
                                                Key.Tab -> {
                                                    if (event.isShiftPressed) {
                                                        searchFocusRequester.requestFocus()
                                                    } else if (playerFocusRequesters.isEmpty()) {
                                                        cancelFocusRequester.requestFocus()
                                                    } else {
                                                        requestPlayerFocus(selectedPlayerIndex())
                                                    }
                                                    true
                                                }
                                                Key.DirectionLeft -> {
                                                    searchFocusRequester.requestFocus()
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    if (playerFocusRequesters.isEmpty()) {
                                                        cancelFocusRequester.requestFocus()
                                                    } else {
                                                        requestPlayerFocus(selectedPlayerIndex())
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                    )
                    Button(
                        enabled = savingId == null,
                        onClick = {
                            nonMemberFirstName = ""
                            nonMemberLastName = ""
                            nonMemberCountry = ""
                            nonMemberError = null
                            nonMemberSlotId = assignmentSlot.id
                            assignmentSlotId = null
                        },
                    ) { Text("Enter non-member") }
                    Column(Modifier.fillMaxWidth()) {
                        DataTableHeaderRow {
                            TournamentPlayerHeader("Photo", Modifier.width(AssignmentPhotoColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader("Country", Modifier.width(AssignmentCountryColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader("EMA number", Modifier.width(AssignmentEmaColumnWidth))
                            TournamentPlayerHeader("Name", Modifier.weight(1f))
                        }
                        DataTableDivider()
                        if (filteredBasePlayers.isEmpty()) {
                            Text(
                                text = "No EMA players match this search.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumnWithScrollbar(
                                state = playerListState,
                                modifier = Modifier.heightIn(max = 410.dp),
                            ) {
                                items(filteredBasePlayers.size, key = { filteredBasePlayers[it].emaId }) { index ->
                                    val player = filteredBasePlayers[index]
                                    DataTableRow(
                                        modifier = Modifier
                                            .focusRequester(playerFocusRequesters[index])
                                            .onFocusChanged {
                                                if (it.isFocused) focusedPlayerIndex = index
                                            }
                                            .focusProperties {
                                                previous = searchFocusRequester
                                                next = cancelFocusRequester
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) {
                                                    return@onPreviewKeyEvent false
                                                }
                                                when (event.key) {
                                                    Key.Tab -> {
                                                        if (event.isShiftPressed) {
                                                            if (assignmentSearchQuery.isNotEmpty()) {
                                                                clearSearchFocusRequester.requestFocus()
                                                            } else {
                                                                searchFocusRequester.requestFocus()
                                                            }
                                                        } else {
                                                            cancelFocusRequester.requestFocus()
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionUp -> {
                                                        if (index == 0) searchFocusRequester.requestFocus()
                                                        else requestPlayerFocus(index - 1)
                                                        true
                                                    }
                                                    Key.DirectionDown -> {
                                                        if (index == filteredBasePlayers.lastIndex) {
                                                            cancelFocusRequester.requestFocus()
                                                        } else {
                                                            requestPlayerFocus(index + 1)
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionLeft -> {
                                                        searchFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        cancelFocusRequester.requestFocus()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            },
                                        onClick = { assign(assignmentSlot.id, player) },
                                    ) {
                                        TournamentPlayerPhoto(
                                            photoUrl = player.photoUrl,
                                            playerName = player.displayName,
                                            columnWidth = AssignmentPhotoColumnWidth,
                                        )
                                        Box(
                                            modifier = Modifier.width(AssignmentCountryColumnWidth),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CountryFlag(
                                                code = player.country,
                                                contentDescription = "Country ${player.country}",
                                            )
                                        }
                                        Text(
                                            player.emaId,
                                            modifier = Modifier.width(AssignmentEmaColumnWidth),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(player.displayName, modifier = Modifier.weight(1f))
                                    }
                                    DataTableDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val cancelInteractionSource = remember { MutableInteractionSource() }
                val cancelHovered by cancelInteractionSource.collectIsHoveredAsState()
                val cancelFocused by cancelInteractionSource.collectIsFocusedAsState()
                TextButton(
                    onClick = { assignmentSlotId = null },
                    interactionSource = cancelInteractionSource,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (cancelHovered || cancelFocused) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .focusRequester(cancelFocusRequester)
                        .focusProperties {
                            previous = playerFocusRequesters.getOrNull(
                                selectedPlayerIndex(),
                            ) ?: searchFocusRequester
                            next = searchFocusRequester
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Tab -> {
                                    if (event.isShiftPressed && playerFocusRequesters.isNotEmpty()) {
                                        requestPlayerFocus(selectedPlayerIndex())
                                    } else {
                                        searchFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (playerFocusRequesters.isEmpty()) {
                                        searchFocusRequester.requestFocus()
                                    } else {
                                        requestPlayerFocus(selectedPlayerIndex())
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    searchFocusRequester.requestFocus()
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (playerFocusRequesters.isEmpty()) {
                                        searchFocusRequester.requestFocus()
                                    } else {
                                        requestPlayerFocus(selectedPlayerIndex())
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    searchFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        },
                ) { Text("Cancel") }
            },
        )
    }

    nonMemberSlotId?.let { slotId ->
        val firstNameFocusRequester = remember(slotId) { FocusRequester() }
        LaunchedEffect(slotId) { firstNameFocusRequester.requestFocus() }
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (savingId == null) nonMemberSlotId = null },
            title = { Text("Assign non-member to player $slotId") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    nonMemberError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = nonMemberFirstName,
                        onValueChange = { nonMemberFirstName = it; nonMemberError = null },
                        label = { Text("First name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(firstNameFocusRequester),
                    )
                    OutlinedTextField(
                        value = nonMemberLastName,
                        onValueChange = { nonMemberLastName = it; nonMemberError = null },
                        label = { Text("Last name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = nonMemberCountry,
                        onValueChange = { nonMemberCountry = it.uppercase().take(3); nonMemberError = null },
                        label = { Text("Country") },
                        placeholder = { Text("Three-letter EMA code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(enabled = savingId == null, onClick = { assignNonMember(slotId) }) { Text("Assign") }
            },
            dismissButton = {
                TextButton(enabled = savingId == null, onClick = { nonMemberSlotId = null }) { Text("Cancel") }
            },
        )
    }

    if (clearAssignmentSlot != null) {
        val assigned = clearAssignmentSlot.assignedEmaId?.let(playersByEma::get)
        val assignedName = assigned?.displayName ?: clearAssignmentSlot.nonMember?.displayName
        val clearConfirmFocusRequester = remember(clearAssignmentSlot.id) { FocusRequester() }
        val clearCancelFocusRequester = remember(clearAssignmentSlot.id) { FocusRequester() }
        val clearConfirmInteractionSource = remember(clearAssignmentSlot.id) { MutableInteractionSource() }
        val clearCancelInteractionSource = remember(clearAssignmentSlot.id) { MutableInteractionSource() }
        val clearConfirmFocused by clearConfirmInteractionSource.collectIsFocusedAsState()
        val clearConfirmHovered by clearConfirmInteractionSource.collectIsHoveredAsState()
        val clearCancelFocused by clearCancelInteractionSource.collectIsFocusedAsState()
        val clearCancelHovered by clearCancelInteractionSource.collectIsHoveredAsState()

        LaunchedEffect(clearAssignmentSlot.id) {
            clearConfirmFocusRequester.requestFocus()
        }

        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { clearAssignmentSlotId = null },
            title = { Text("Clear assignment?") },
            text = {
                Text(
                    "Remove ${assignedName ?: "this assignment"} " +
                        "from tournament player ${clearAssignmentSlot.id}?",
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (clearConfirmFocused || clearConfirmHovered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .padding(3.dp),
                ) {
                    Button(
                        enabled = savingId == null,
                        onClick = { assign(clearAssignmentSlot.id, null) },
                        interactionSource = clearConfirmInteractionSource,
                        modifier = Modifier
                            .focusRequester(clearConfirmFocusRequester)
                            .focusProperties {
                                previous = clearCancelFocusRequester
                                next = clearCancelFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.Tab,
                                    Key.DirectionLeft,
                                    Key.DirectionRight,
                                    Key.DirectionUp,
                                    Key.DirectionDown,
                                    -> {
                                        clearCancelFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    ) { Text("Clear assignment") }
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (clearCancelFocused || clearCancelHovered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .padding(3.dp),
                ) {
                    TextButton(
                        onClick = { clearAssignmentSlotId = null },
                        interactionSource = clearCancelInteractionSource,
                        modifier = Modifier
                            .focusRequester(clearCancelFocusRequester)
                            .focusProperties {
                                previous = clearConfirmFocusRequester
                                next = clearConfirmFocusRequester
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.Tab,
                                    Key.DirectionLeft,
                                    Key.DirectionRight,
                                    Key.DirectionUp,
                                    Key.DirectionDown,
                                    -> {
                                        clearConfirmFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    ) { Text("Cancel") }
                }
            },
        )
    }

    AppScaffold(
        title = "Tournament players",
        isLoading = loading || savingId != null,
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onIdCards = ::exportIdCards,
                onRefresh = ::refresh,
            )
        },
    ) {
        ScreenColumn(maxWidth = 1400.dp, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let {
                AppErrorDialog(
                    message = it,
                    onDismiss = { error = null },
                )
            }
            SectionCard {
                if (players.isEmpty() && !loading) Text("No generated tournament players found.")
                Column(Modifier.fillMaxWidth()) {
                    DataTableHeaderRow {
                        TournamentPlayerHeader("Photo", Modifier.width(TournamentPlayerPhotoColumnWidth))
                        TournamentPlayerHeader("Country", Modifier.width(TournamentPlayerCountryColumnWidth), TextAlign.Center)
                        TournamentPlayerHeader(
                            "Player ID",
                            Modifier.width(TournamentPlayerIdColumnWidth),
                            TextAlign.Center,
                        )
                        TournamentPlayerHeader("EMA number", Modifier.width(TournamentPlayerEmaColumnWidth))
                        TournamentPlayerHeader("Name", Modifier.weight(1.2f))
                        if (isTeamsTournament) {
                            TournamentPlayerHeader("Team", Modifier.width(TournamentPlayerTeamColumnWidth))
                        }
                        TournamentPlayerHeader("Tables", Modifier.width(TournamentPlayerTablesColumnWidth))
                        Spacer(Modifier.width(TournamentPlayerActionColumnWidth))
                    }
                    DataTableDivider()
                    LazyColumnWithScrollbar(
                        state = assignmentsListState,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(players.size, key = { players[it].id }) { index ->
                            val slot = players[index]
                            val assigned = slot.assignedEmaId?.let(playersByEma::get)
                            val playerCountry = assigned?.country ?: slot.nonMember?.country.orEmpty()
                            val playerName = assigned?.displayName ?: slot.nonMember?.displayName
                            DataTableRow(
                                onClick = { openAssignment(slot.id) },
                                clickFocusable = false,
                                highlighted = focusedAssignRowIndex == index || focusedClearRowIndex == index,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(TournamentPlayerPhotoColumnWidth)
                                        .height(TournamentPlayerPhotoSize),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    TournamentPlayerPhoto(
                                        photoUrl = assigned?.photoUrl,
                                        playerName = playerName.orEmpty(),
                                        columnWidth = TournamentPlayerPhotoColumnWidth,
                                    )
                                }
                                Box(
                                    modifier = Modifier.width(TournamentPlayerCountryColumnWidth),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CountryFlag(
                                        code = playerCountry,
                                        contentDescription = playerCountry.ifBlank { "No country" },
                                    )
                                }
                                Text(
                                    text = slot.id.toString(),
                                    modifier = Modifier.width(TournamentPlayerIdColumnWidth),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    assigned?.emaId ?: if (slot.nonMember != null) "Non-member" else "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(TournamentPlayerEmaColumnWidth),
                                )
                                Text(
                                    playerName ?: "Not assigned",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1.2f),
                                )
                                if (isTeamsTournament) {
                                    Text(
                                        text = teamNamesById[slot.team] ?: "Team ${slot.team}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(TournamentPlayerTeamColumnWidth),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = tableNumbersByPlayerId[slot.id] ?: "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(TournamentPlayerTablesColumnWidth),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    modifier = Modifier.width(TournamentPlayerActionColumnWidth),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!slot.isAssigned) Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(
                                                if (focusedAssignRowIndex == index) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                } else {
                                                    androidx.compose.ui.graphics.Color.Transparent
                                                },
                                            )
                                            .padding(3.dp),
                                    ) {
                                        Button(
                                            enabled = !loading && savingId == null,
                                            onClick = { openAssignment(slot.id) },
                                            modifier = Modifier
                                                .focusRequester(assignFocusRequesters[index])
                                                .onFocusChanged {
                                                    focusedAssignRowIndex = if (it.isFocused) index else null
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type != KeyEventType.KeyDown) {
                                                        return@onPreviewKeyEvent false
                                                    }
                                                    when (event.key) {
                                                        Key.DirectionLeft -> true
                                                        Key.DirectionRight -> {
                                                            true
                                                        }
                                                        Key.DirectionUp -> {
                                                            requestPlayerActionFocus(index - 1)
                                                            true
                                                        }
                                                        Key.DirectionDown -> {
                                                            requestPlayerActionFocus(index + 1)
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                },
                                        ) { Text("Assign", maxLines = 1, overflow = TextOverflow.Clip) }
                                    }
                                    if (slot.isAssigned) {
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(
                                                    if (focusedClearRowIndex == index) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                    } else {
                                                        androidx.compose.ui.graphics.Color.Transparent
                                                    },
                                                )
                                                .padding(3.dp),
                                        ) {
                                            OutlinedButton(
                                                enabled = !loading && savingId == null,
                                                onClick = { openClearAssignment(slot.id) },
                                                modifier = Modifier
                                                    .focusRequester(clearFocusRequesters[index])
                                                    .onFocusChanged {
                                                        focusedClearRowIndex = if (it.isFocused) index else null
                                                    }
                                                    .onPreviewKeyEvent { event ->
                                                        if (event.type != KeyEventType.KeyDown) {
                                                            return@onPreviewKeyEvent false
                                                        }
                                                        when (event.key) {
                                                            Key.DirectionLeft -> {
                                                                true
                                                            }
                                                            Key.DirectionRight -> true
                                                            Key.DirectionUp -> {
                                                                requestPlayerActionFocus(index - 1)
                                                                true
                                                            }
                                                            Key.DirectionDown -> {
                                                                requestPlayerActionFocus(index + 1)
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    },
                                            ) { Text("Clear", maxLines = 1, overflow = TextOverflow.Clip) }
                                        }
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
private val TournamentPlayerEmaColumnWidth = 104.dp
private val TournamentPlayerTeamColumnWidth = 144.dp
private val TournamentPlayerTablesColumnWidth = 220.dp
private val TournamentPlayerActionColumnWidth = 112.dp
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
