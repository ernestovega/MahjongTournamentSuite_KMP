package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerContactState
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.contactState
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.EmaCountryDropdown
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton
import com.etologic.mahjongtournamentsuite.presentation.components.IdCardsExportDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.platform.saveBinaryFile
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlin.time.Clock
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.account
import mahjongtournamentsuite.adminapp.generated.resources.assign
import mahjongtournamentsuite.adminapp.generated.resources.assign_all_before_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.assign_all_before_id_list
import mahjongtournamentsuite.adminapp.generated.resources.assign_ema_player_to_player
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_2
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_message
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.contact_contacted
import mahjongtournamentsuite.adminapp.generated.resources.contact_invite
import mahjongtournamentsuite.adminapp.generated.resources.contact_invited
import mahjongtournamentsuite.adminapp.generated.resources.contact_linked
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.ema_number
import mahjongtournamentsuite.adminapp.generated.resources.remove_account_link
import mahjongtournamentsuite.adminapp.generated.resources.resend_invitation
import mahjongtournamentsuite.adminapp.generated.resources.send_reminder_again
import mahjongtournamentsuite.adminapp.generated.resources.send_reminder
import mahjongtournamentsuite.adminapp.generated.resources.send_invitation
import mahjongtournamentsuite.adminapp.generated.resources.filter_by_code
import mahjongtournamentsuite.adminapp.generated.resources.first_name
import mahjongtournamentsuite.adminapp.generated.resources.last_name
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.no_country
import mahjongtournamentsuite.adminapp.generated.resources.no_ema_players_match
import mahjongtournamentsuite.adminapp.generated.resources.no_generated_players
import mahjongtournamentsuite.adminapp.generated.resources.no_matches
import mahjongtournamentsuite.adminapp.generated.resources.no_player_photo
import mahjongtournamentsuite.adminapp.generated.resources.no_players_yet
import mahjongtournamentsuite.adminapp.generated.resources.no_tournament_players_match
import mahjongtournamentsuite.adminapp.generated.resources.not_assigned
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.photo_of
import mahjongtournamentsuite.adminapp.generated.resources.player_actions
import mahjongtournamentsuite.adminapp.generated.resources.player_error_country
import mahjongtournamentsuite.adminapp.generated.resources.player_error_first_name
import mahjongtournamentsuite.adminapp.generated.resources.player_error_last_name
import mahjongtournamentsuite.adminapp.generated.resources.player_n
import mahjongtournamentsuite.adminapp.generated.resources.save_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.save_id_list
import mahjongtournamentsuite.adminapp.generated.resources.search_by_name_country_or_ema
import mahjongtournamentsuite.adminapp.generated.resources.search_by_name_ema_number_player
import mahjongtournamentsuite.adminapp.generated.resources.substitute
import mahjongtournamentsuite.adminapp.generated.resources.substitution
import mahjongtournamentsuite.adminapp.generated.resources.tables
import mahjongtournamentsuite.adminapp.generated.resources.team
import mahjongtournamentsuite.adminapp.generated.resources.team_n
import mahjongtournamentsuite.adminapp.generated.resources.this_assignment
import mahjongtournamentsuite.adminapp.generated.resources.tournament_players
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Links generated tournament slots to players in the shared base. */
@Composable
fun PlayersScreen(navController: NavHostController, tournamentId: String) {
    val playerLabel = rememberTemplate(Res.string.player_n)
    val saveIdCardsTitle = stringResource(Res.string.save_id_cards)
    val saveIdListTitle = stringResource(Res.string.save_id_list)
    val teamLabel = rememberTemplate(Res.string.team_n)
    val presenter = koinInject<PlayersPresenter>()
    val store = koinInject<AppMemoryStore>()
    val scope = rememberCoroutineScope()
    val slots by store.tournamentPlayers.collectAsState()
    val basePlayers by store.players.collectAsState()
    LoadMissingSession()
    val tournaments by store.tournaments.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var savingId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    var countries by remember { mutableStateOf<List<Country>>(emptyList()) }
    var teams by remember { mutableStateOf<List<TournamentTeam>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TournamentTable>>(emptyList()) }
    var assignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var clearAssignmentSlotId by remember { mutableStateOf<Int?>(null) }
    var substitutionSlotId by remember { mutableStateOf<Int?>(null) }
    var inviteEmailSlotId by remember { mutableStateOf<Int?>(null) }
    var accountOutcome by remember { mutableStateOf<AccountOutcome?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<UiText?>(null) }
    var accountResults by remember { mutableStateOf<List<PlayerAccountActionResult>>(emptyList()) }
    var bulkAccountsOpen by remember { mutableStateOf(false) }
    var idCardsDialogOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    var assignmentSearchQuery by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val searchFocusRequester = remember { FocusRequester() }
    val assignmentsListState = rememberPersistentLazyListState("assignments")
    var initialAssignmentScrollPending by rememberSaveable(tournamentId) { mutableStateOf(true) }
    var assignmentFocusRestoreSlotId by rememberSaveable(tournamentId) { mutableStateOf<Int?>(null) }

    fun refresh(force: Boolean = false) = scope.launch {
        loading = true
        error = null
        when (val result = presenter.loadPlayers(tournamentId, force)) {
            is AppResult.Success -> store.upsertPlayers(tournamentId, result.value)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        when (val result = presenter.loadBasePlayers(force)) {
            is AppResult.Success -> store.upsertBasePlayers(result.value)
            is AppResult.Failure -> if (error == null) error = result.error.toUiText()
        }
        when (val result = presenter.loadCountries(force)) {
            is AppResult.Success -> countries = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiText()
        }
        when (val result = presenter.loadTeams(tournamentId, force)) {
            is AppResult.Success -> teams = result.value
            is AppResult.Failure -> if (error == null) error = result.error.toUiText()
        }
        when (val result = presenter.loadTables(tournamentId, force)) {
            is AppResult.Success -> {
                tables = result.value
                store.upsertTables(tournamentId, roundId = null, tables = result.value)
            }
            is AppResult.Failure -> if (error == null) error = result.error.toUiText()
        }
        loading = false
    }

    fun runAccountAction(
        action: PlayerAccountAction,
        items: List<PlayerAccountActionItem>,
        showOutcome: Boolean = false,
    ) = scope.launch {
        accountBusy = true
        accountError = null
        accountResults = emptyList()
        when (val result = presenter.runAccountAction(tournamentId, action, items)) {
            is AppResult.Success -> {
                accountResults = result.value
                refresh(force = true).join()
                if (showOutcome) {
                    accountOutcome = result.value.firstOrNull()?.toOutcome()
                    accountResults = emptyList()
                }
            }
            is AppResult.Failure -> {
                if (showOutcome) {
                    accountOutcome = AccountOutcome(result.error.toUiText(), isError = true)
                } else {
                    accountError = result.error.toUiText()
                }
            }
        }
        accountBusy = false
    }

    fun assign(slotId: Int, player: Player?) = scope.launch {
        savingId = slotId
        error = null
        when (val result = presenter.assignPlayer(tournamentId, slotId, player?.emaId)) {
            is AppResult.Success -> refresh(force = true)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        assignmentSlotId = null
        clearAssignmentSlotId = null
        savingId = null
    }

    fun saveSubstitution(slotId: Int, substitution: PlayerSubstitution?) = scope.launch {
        savingId = slotId
        error = null
        when (val result = presenter.setSubstitution(tournamentId, slotId, substitution)) {
            is AppResult.Success -> refresh(force = true)
            is AppResult.Failure -> error = result.error.toUiText()
        }
        substitutionSlotId = null
        savingId = null
    }

    LaunchedEffect(tournamentId) { refresh() }
    val playersByEma = basePlayers.associateBy { it.emaId }
    val players = slots[tournamentId].orEmpty()

    fun exportIdCards(sheet: IdCardSheetLayout?) = scope.launch {
        val unassigned = players.filterNot { it.isAssigned }
        if (unassigned.isNotEmpty()) {
            error = UiText.of(Res.string.assign_all_before_id_cards)
            return@launch
        }

        loading = true
        error = null
        when (val result = presenter.generateIdCards(tournamentId, sheet)) {
            is AppResult.Success -> {
                val tournament = tournaments.firstOrNull { it.id == tournamentId }
                val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                val fileName = generatedFileName(baseName, "id-cards", "pdf", Clock.System.now(), year = year)
                saveBinaryFile(fileName, result.value, "application/pdf", saveIdCardsTitle)
            }
            is AppResult.Failure -> error = result.error.toUiText()
        }
        loading = false
    }

    val exportPlayers = rememberPlayersExport(tournamentId) { error = it }

    fun exportIdList() = scope.launch {
        val unassigned = players.filterNot { it.isAssigned }
        if (unassigned.isNotEmpty()) {
            error = UiText.of(Res.string.assign_all_before_id_list)
            return@launch
        }

        loading = true
        error = null
        when (val result = presenter.generateIdList(tournamentId)) {
            is AppResult.Success -> {
                val tournament = tournaments.firstOrNull { it.id == tournamentId }
                val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                val fileName = generatedFileName(baseName, "id-list", "xlsx", Clock.System.now(), year = year)
                saveBinaryFile(
                    fileName,
                    result.value,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    saveIdListTitle,
                )
            }
            is AppResult.Failure -> error = result.error.toUiText()
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
    val tournamentCountryCodes = remember(players, playersByEma) {
        players.mapNotNull { slot ->
            slot.assignedEmaId?.let(playersByEma::get)?.country
        }.map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != "EU" }
            .distinct()
            .sorted()
    }
    val filteredPlayers = remember(players, playersByEma, teamNamesById, tableNumbersByPlayerId, searchQuery, selectedCountryCodes) {
        val query = normalizeSearchText(searchQuery.trim())
        players.filter { slot ->
            val assigned = slot.assignedEmaId?.let(playersByEma::get)
            val name = assigned?.displayName.orEmpty()
            val country = assigned?.country.orEmpty()
            val team = teamNamesById[slot.team] ?: teamLabel(slot.team)
            val tablesForPlayer = tableNumbersByPlayerId[slot.id].orEmpty()
            val matchesSearch = query.isEmpty() || listOf(
                name, assigned?.emaId.orEmpty(), slot.email.orEmpty(), slot.id.toString(), team, tablesForPlayer,
            ).any { normalizeSearchText(it).contains(query) }
            val matchesCountry = selectedCountryCodes.isEmpty() || country.trim().uppercase() in selectedCountryCodes
            matchesSearch && matchesCountry
        }
    }
    val filteredPlayerIndexes = remember(players, filteredPlayers) {
        filteredPlayers.map { filtered -> players.indexOfFirst { it.id == filtered.id } }
    }
    LaunchedEffect(tournamentCountryCodes) {
        selectedCountryCodes = selectedCountryCodes.intersect(tournamentCountryCodes.toSet())
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
    inviteEmailSlotId?.let { id -> players.firstOrNull { it.id == id } }?.let { slot ->
        MissingEmailsDialog(
            players = listOf(slot),
            nameOf = { playerLabel(it.id) },
            busy = accountBusy,
            confirmLabel = stringResource(Res.string.send_invitation),
            onConfirm = { emails ->
                inviteEmailSlotId = null
                emails[slot.id]?.let { runAccountAction(PlayerAccountAction.SEND, listOf(PlayerAccountActionItem(slot.id, it)), showOutcome = true) }
            },
            onDismiss = { inviteEmailSlotId = null },
        )
    }
    accountOutcome?.let { AccountOutcomeDialog(it, onDismiss = { accountOutcome = null }) }
    if (bulkAccountsOpen) {
        val registry = basePlayers.associateBy { it.emaId }
        PlayerAccountsBulkDialog(
            players = players.filter { it.isAssigned },
            nameOf = { slot ->
                slot.assignedEmaId?.let(registry::get)?.displayName
                    ?: playerLabel(slot.id)
            },
            countryOf = { slot ->
                slot.assignedEmaId?.let(registry::get)?.country.orEmpty()
            },
            emaIdOf = { slot -> slot.assignedEmaId?.let(registry::get)?.emaId },
            countryNameOf = { code -> countries.firstOrNull { it.code == code }?.name.orEmpty() },
            results = accountResults,
            busy = accountBusy,
            errorMessage = accountError,
            onAction = { action, items -> runAccountAction(action, items) },
            onDismiss = { bulkAccountsOpen = false; accountResults = emptyList(); accountError = null },
        )
    }
    val assignFocusRequesters = remember(players.map { it.id }) { players.map { FocusRequester() } }
    val clearFocusRequesters = remember(players.map { it.id }) { players.map { FocusRequester() } }
    var focusedAssignRowIndex by remember { mutableStateOf<Int?>(null) }
    var focusedClearRowIndex by remember { mutableStateOf<Int?>(null) }

    fun openAssignment(slotId: Int) {
        assignmentSearchQuery = ""
        assignmentFocusRestoreSlotId = slotId
        assignmentSlotId = slotId
    }

    // A substitution matters only after the first table has data.
    val tournamentStarted = tables.any { it.hasProgress }
    val tournamentRounds = tables.map { it.roundId }.distinct().sorted()

    fun openClearAssignment(slotId: Int) {
        assignmentFocusRestoreSlotId = slotId
        clearAssignmentSlotId = slotId
    }

    suspend fun revealPlayerAction(index: Int) {
        // Wait for the web layout pass before reading or changing the list state.
        withFrameNanos { }
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
        val playerListState = rememberPersistentLazyListState("players")
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
            title = { Text(stringResource(Res.string.assign_ema_player_to_player, assignmentSlot.id)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = assignmentSearchQuery,
                        onValueChange = { assignmentSearchQuery = it },
                        label = { Text(stringResource(Res.string.search_by_name_country_or_ema)) },
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
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                    )
                    Column(Modifier.fillMaxWidth()) {
                        DataTableHeaderRow {
                            TournamentPlayerHeader(stringResource(Res.string.photo), Modifier.width(AssignmentPhotoColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader(stringResource(Res.string.country), Modifier.width(AssignmentCountryColumnWidth), TextAlign.Center)
                            TournamentPlayerHeader(stringResource(Res.string.ema_number), Modifier.width(AssignmentEmaColumnWidth))
                            TournamentPlayerHeader(stringResource(Res.string.name), Modifier.weight(1f))
                        }
                        DataTableDivider()
                        if (filteredBasePlayers.isEmpty()) {
                            AppEmptyState(Icons.Default.Search, stringResource(Res.string.no_matches), stringResource(Res.string.no_ema_players_match), compact = true)
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
                                                contentDescription = stringResource(Res.string.country_n, player.country),
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
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    val substitutionSlot = substitutionSlotId?.let { id -> players.firstOrNull { it.id == id } }
    if (substitutionSlot != null) {
        val assigned = substitutionSlot.assignedEmaId?.let(playersByEma::get)
        val firstOpenRound = tournamentRounds.firstOrNull { round ->
            tables.none { it.roundId == round && substitutionSlot.id in it.playerIds && it.isCompleted }
        } ?: tournamentRounds.lastOrNull() ?: 1
        PlayerSubstitutionDialog(
            playerName = assigned?.displayName ?: playerLabel(substitutionSlot.id).lowercase(),
            current = substitutionSlot.substitution,
            rounds = tournamentRounds,
            defaultRound = firstOpenRound,
            busy = savingId != null,
            onSave = { saveSubstitution(substitutionSlot.id, it) },
            onRemove = { saveSubstitution(substitutionSlot.id, null) },
            onDismiss = { substitutionSlotId = null },
        )
    }

    if (clearAssignmentSlot != null) {
        val assigned = clearAssignmentSlot.assignedEmaId?.let(playersByEma::get)
        val assignedName = assigned?.displayName
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
            title = { Text(stringResource(Res.string.clear_assignment)) },
            text = {
                Text(
                    stringResource(
                        Res.string.clear_assignment_message,
                        assignedName ?: stringResource(Res.string.this_assignment),
                        clearAssignmentSlot.id,
                    ),
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
                    ) { Text(stringResource(Res.string.clear_assignment_2)) }
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
                    ) { Text(stringResource(Res.string.cancel)) }
                }
            },
        )
    }

    AppScaffold(
        title = stringResource(Res.string.tournament_players),
        subtitle = tournaments.firstOrNull { it.id == tournamentId }?.name?.ifBlank { null },
        isLoading = loading || savingId != null,
        showBackgroundLogo = loading && players.isEmpty(),
        onBack = { navController.popBackStack() },
        actions = {
            AppTopBarActions(
                onIdCards = { idCardsDialogOpen = true },
                onIdList = ::exportIdList,
                onPlayerAccounts = { accountError = null; accountResults = emptyList(); bulkAccountsOpen = true },
                onImportPlayers = { importDialogOpen = true },
                onExportPlayers = exportPlayers,
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
            if (importDialogOpen) {
                PlayerImportDialog(
                    tournamentId = tournamentId,
                    playerNameOf = { emaId -> playersByEma[emaId]?.displayName },
                    onChanged = { refresh(force = true) },
                    onDismiss = { importDialogOpen = false },
                )
            }
            if (idCardsDialogOpen) {
                IdCardsExportDialog(
                    onExport = { sheet ->
                        idCardsDialogOpen = false
                        exportIdCards(sheet)
                    },
                    onDismiss = { idCardsDialogOpen = false },
                )
            }
            SectionCard {
                if (players.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(Res.string.search_by_name_ema_number_player)) },
                        singleLine = true,
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchFocusRequester.requestFocus()
                                }) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search)) }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    )
                    if (tournamentCountryCodes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                tournamentCountryCodes.forEach { code ->
                                    val isSelected = code in selectedCountryCodes
                                    val count = players.count { slot ->
                                        val assigned = slot.assignedEmaId?.let(playersByEma::get)
                                        assigned?.country.orEmpty().uppercase() == code
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedCountryCodes = if (isSelected) selectedCountryCodes - code else selectedCountryCodes + code
                                        },
                                        shape = CircleShape,
                                        colors = if (isSelected) ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ) else ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        CountryFlag(code = code, contentDescription = stringResource(Res.string.filter_by_code, code))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$code ($count)")
                                    }
                                }
                            }
                        }
                    }
                }
                if (players.isEmpty() && !loading) {
                    AppEmptyState(Icons.Default.People, stringResource(Res.string.no_players_yet), stringResource(Res.string.no_generated_players), compact = true)
                }
                if (players.isNotEmpty() && filteredPlayers.isEmpty() && !loading) {
                    AppEmptyState(Icons.Default.Search, stringResource(Res.string.no_matches), stringResource(Res.string.no_tournament_players_match), compact = true)
                }
                Column(Modifier.fillMaxWidth()) {
                    DataTableHeaderRow {
                        TournamentPlayerHeader("ID", Modifier.width(TournamentPlayerIdColumnWidth), TextAlign.Center)
                        TournamentPlayerHeader(stringResource(Res.string.ema_number), Modifier.width(TournamentPlayerEmaColumnWidth))
                        TournamentPlayerHeader(stringResource(Res.string.country), Modifier.width(TournamentPlayerCountryColumnWidth), TextAlign.Center)
                        TournamentPlayerHeader(stringResource(Res.string.photo), Modifier.width(TournamentPlayerPhotoColumnWidth))
                        TournamentPlayerHeader(stringResource(Res.string.name), Modifier.weight(1.2f))
                        if (isTeamsTournament) {
                            TournamentPlayerHeader(stringResource(Res.string.team), Modifier.width(TournamentPlayerTeamColumnWidth))
                        }
                        TournamentPlayerHeader(stringResource(Res.string.tables), Modifier.width(TournamentPlayerTablesColumnWidth))
                        TournamentPlayerHeader(stringResource(Res.string.account), Modifier.width(TournamentPlayerAccountColumnWidth), TextAlign.Center)
                        Spacer(Modifier.width(TournamentPlayerActionColumnWidth))
                    }
                    DataTableDivider()
                    LazyColumnWithScrollbar(
                        state = assignmentsListState,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(filteredPlayers.size, key = { filteredPlayers[it].id }) { index ->
                            val slot = filteredPlayers[index]
                            val assigned = slot.assignedEmaId?.let(playersByEma::get)
                            val playerCountry = assigned?.country.orEmpty()
                            val playerName = assigned?.displayName
                            DataTableRow(
                                // A slot with a player is not clickable. The editor must use the menu and Clear to change it.
                                onClick = if (slot.isAssigned) null else ({ openAssignment(slot.id) }),
                                clickFocusable = false,
                                highlighted = focusedAssignRowIndex == index || focusedClearRowIndex == index,
                            ) {
                                Text(
                                    text = slot.id.toString(),
                                    modifier = Modifier.width(TournamentPlayerIdColumnWidth),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    assigned?.emaId ?: "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(TournamentPlayerEmaColumnWidth),
                                    textAlign = TextAlign.Center,
                                )
                                Box(
                                    modifier = Modifier.width(TournamentPlayerCountryColumnWidth),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CountryFlag(
                                        code = playerCountry,
                                        contentDescription = playerCountry.ifBlank { stringResource(Res.string.no_country) },
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(TournamentPlayerPhotoColumnWidth)
                                        .height(TournamentPlayerPhotoSize),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TournamentPlayerPhoto(
                                        photoUrl = assigned?.photoUrl,
                                        playerName = playerName.orEmpty(),
                                        columnWidth = TournamentPlayerPhotoColumnWidth,
                                    )
                                }
                                // The first name is on the first line and the last name on the second line.
                                Text(
                                    if (assigned == null) {
                                        stringResource(Res.string.not_assigned)
                                    } else {
                                        assigned.firstName.trim() + "\n" + assigned.lastName.trim()
                                    } + (slot.substitution?.let { " (${it.label()})" } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = if (slot.substitution?.struckThrough == true) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isTeamsTournament) {
                                    Text(
                                        text = teamNamesById[slot.team] ?: teamLabel(slot.team),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(TournamentPlayerTeamColumnWidth),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = tableNumbersByPlayerId[slot.id] ?: "—",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(TournamentPlayerTablesColumnWidth),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    modifier = Modifier.width(TournamentPlayerAccountColumnWidth),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (slot.assignedEmaId.isNullOrBlank()) {
                                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = slot.contactState.label(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                            )
                                            Text(
                                                text = slot.email ?: "—",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier.width(TournamentPlayerActionColumnWidth),
                                    contentAlignment = Alignment.CenterEnd,
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
                                                .focusRequester(assignFocusRequesters[filteredPlayerIndexes[index]])
                                                .onFocusChanged {
                                                    focusedAssignRowIndex = if (it.isFocused) index else null
                                                }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type != KeyEventType.KeyDown) {
                                                        return@onPreviewKeyEvent false
                                                    }
                                                    when (event.key) {
                                                        Key.DirectionLeft, Key.DirectionRight -> true
                                                        Key.DirectionUp -> {
                                                            requestPlayerActionFocus(filteredPlayerIndexes.getOrNull(index - 1) ?: -1)
                                                            true
                                                        }
                                                        Key.DirectionDown -> {
                                                            requestPlayerActionFocus(filteredPlayerIndexes.getOrNull(index + 1) ?: -1)
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                },
                                        ) { Text(stringResource(Res.string.assign), maxLines = 1, overflow = TextOverflow.Clip) }
                                    }
                                    if (slot.isAssigned) {
                                        var actionsMenuOpen by remember { mutableStateOf(false) }
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
                                            IconButton(
                                                enabled = !loading && savingId == null,
                                                onClick = { actionsMenuOpen = true },
                                                modifier = Modifier
                                                    .focusRequester(clearFocusRequesters[filteredPlayerIndexes[index]])
                                                    .onFocusChanged {
                                                        focusedClearRowIndex = if (it.isFocused) index else null
                                                    }
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type != KeyEventType.KeyDown) {
                                                        return@onPreviewKeyEvent false
                                                    }
                                                    when (event.key) {
                                                        Key.DirectionLeft, Key.DirectionRight -> true
                                                        Key.DirectionUp -> {
                                                            requestPlayerActionFocus(filteredPlayerIndexes.getOrNull(index - 1) ?: -1)
                                                            true
                                                        }
                                                        Key.DirectionDown -> {
                                                            requestPlayerActionFocus(filteredPlayerIndexes.getOrNull(index + 1) ?: -1)
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                },
                                            ) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.player_actions)) }
                                            DropdownMenu(
                                                expanded = actionsMenuOpen,
                                                onDismissRequest = { actionsMenuOpen = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(Res.string.clear)) },
                                                    onClick = {
                                                        actionsMenuOpen = false
                                                        openClearAssignment(slot.id)
                                                    },
                                                )
                                                val accountActionsEnabled = !accountBusy && !loading && savingId == null
                                                fun accountAction(action: PlayerAccountAction) {
                                                    actionsMenuOpen = false
                                                    runAccountAction(action, listOf(PlayerAccountActionItem(slot.id)), showOutcome = true)
                                                }
                                                when (slot.contactState) {
                                                    PlayerContactState.NOT_INVITED -> DropdownMenuItem(
                                                        enabled = accountActionsEnabled,
                                                        text = { Text(stringResource(Res.string.send_invitation)) },
                                                        onClick = {
                                                            actionsMenuOpen = false
                                                            inviteEmailSlotId = slot.id
                                                        },
                                                    )
                                                    PlayerContactState.LINKED -> DropdownMenuItem(
                                                        enabled = accountActionsEnabled,
                                                        text = { Text(stringResource(Res.string.send_reminder)) },
                                                        onClick = { accountAction(PlayerAccountAction.SEND) },
                                                    )
                                                    PlayerContactState.CONTACTED -> DropdownMenuItem(
                                                        enabled = accountActionsEnabled,
                                                        text = { Text(stringResource(Res.string.send_reminder_again)) },
                                                        onClick = { accountAction(PlayerAccountAction.RESEND) },
                                                    )
                                                    PlayerContactState.INVITED -> DropdownMenuItem(
                                                        enabled = accountActionsEnabled,
                                                        text = { Text(stringResource(Res.string.resend_invitation)) },
                                                        onClick = { accountAction(PlayerAccountAction.RESEND) },
                                                    )
                                                    PlayerContactState.NO_EMA_ID -> Unit
                                                }
                                                if (slot.contactState != PlayerContactState.NOT_INVITED && slot.contactState != PlayerContactState.NO_EMA_ID) {
                                                    DropdownMenuItem(
                                                        enabled = accountActionsEnabled,
                                                        text = { Text(stringResource(Res.string.remove_account_link)) },
                                                        onClick = { accountAction(PlayerAccountAction.REVOKE) },
                                                    )
                                                }
                                                if (tournamentStarted) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(if (slot.substitution == null) Res.string.substitute else Res.string.substitution)) },
                                                        onClick = {
                                                            actionsMenuOpen = false
                                                            substitutionSlotId = slot.id
                                                        },
                                                    )
                                                }
                                            }
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
private val TournamentPlayerIdColumnWidth = 48.dp
private val TournamentPlayerEmaColumnWidth = 104.dp
private val TournamentPlayerTeamColumnWidth = 144.dp
private val TournamentPlayerTablesColumnWidth = 220.dp
private val TournamentPlayerAccountColumnWidth = 220.dp
private val TournamentPlayerActionColumnWidth = 112.dp
private val AssignmentPhotoColumnWidth = 48.dp
private val AssignmentCountryColumnWidth = 64.dp
private val AssignmentEmaColumnWidth = 96.dp

@Composable
private fun TournamentPlayerHeader(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
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
                    contentDescription = stringResource(Res.string.no_player_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TournamentPlayerPhotoSize * 0.62f),
                )
            }
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = stringResource(Res.string.photo_of, playerName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(TournamentPlayerPhotoSize),
            )
        }
    }
}
