package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.ButtonText
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import com.etologic.mahjongtournamentsuite.domain.model.map
import com.etologic.mahjongtournamentsuite.presentation.PlayerCountRoute
import com.etologic.mahjongtournamentsuite.presentation.TeamsRoute
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.AppTopBarActions
import com.etologic.mahjongtournamentsuite.presentation.components.CountryFlag
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableDivider
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableHeaderRow
import com.etologic.mahjongtournamentsuite.presentation.components.DataTableRow
import com.etologic.mahjongtournamentsuite.presentation.components.ExportFile
import com.etologic.mahjongtournamentsuite.presentation.components.IdCardsExportDialog
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollArea
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.ShowTabsAtTop
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.screenFullBleed
import com.etologic.mahjongtournamentsuite.presentation.components.screenListPadding
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.generatedFileName
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import com.etologic.mahjongtournamentsuite.presentation.util.rememberTemplate
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.assign
import mahjongtournamentsuite.adminapp.generated.resources.assign_all_before_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.assign_all_before_id_list
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.edit_account
import mahjongtournamentsuite.adminapp.generated.resources.edit_email
import mahjongtournamentsuite.adminapp.generated.resources.ema_number
import mahjongtournamentsuite.adminapp.generated.resources.filter_by_code
import mahjongtournamentsuite.adminapp.generated.resources.name
import mahjongtournamentsuite.adminapp.generated.resources.no_generated_players
import mahjongtournamentsuite.adminapp.generated.resources.no_matches
import mahjongtournamentsuite.adminapp.generated.resources.no_players_yet
import mahjongtournamentsuite.adminapp.generated.resources.no_tournament_players_match
import mahjongtournamentsuite.adminapp.generated.resources.not_assigned
import mahjongtournamentsuite.adminapp.generated.resources.photo
import mahjongtournamentsuite.adminapp.generated.resources.player_actions
import mahjongtournamentsuite.adminapp.generated.resources.player_n
import mahjongtournamentsuite.adminapp.generated.resources.round
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.save_id_list
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.substitute
import mahjongtournamentsuite.adminapp.generated.resources.substitution
import mahjongtournamentsuite.adminapp.generated.resources.tables
import mahjongtournamentsuite.adminapp.generated.resources.team
import mahjongtournamentsuite.adminapp.generated.resources.team_n
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.top_bar_id_list
import mahjongtournamentsuite.adminapp.generated.resources.tournament_players
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton as IconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton as TextButton

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
    val backgroundExports = rememberBackgroundExports()
    val slots by store.tournamentPlayers.collectAsState()
    val adminStatus by store.adminStatus.collectAsState()
    val canEditAccounts = adminStatus?.canManageStaff == true
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
    var accountOutcome by remember { mutableStateOf<AccountOutcome?>(null) }
    var emailSlotId by remember { mutableStateOf<Int?>(null) }
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

    /**
     * Saves the typed emails first, then sends to the players whose email was saved or that had one already.
     * A refused email shows as the failed result of its player, and that player gets no send.
     */
    fun sendToPlayers(
        playerIds: List<Int>,
        emails: Map<Int, String> = emptyMap(),
    ) = scope.launch {
        accountBusy = true
        accountError = null
        accountResults = emptyList()
        var refused = emptyList<PlayerAccountActionResult>()
        if (emails.isNotEmpty()) {
            when (val saved = presenter.saveEmails(tournamentId, emails.map { PlayerEmailItem(it.key, it.value) })) {
                is AppResult.Success -> refused = saved.value.filter { it.outcome == PlayerEmailOutcome.FAILED }.map { it.toFailedAction() }
                is AppResult.Failure -> {
                    accountError = saved.error.toUiText()
                    accountBusy = false
                    return@launch
                }
            }
        }
        val toSend = playerIds.filter { id -> refused.none { it.playerId == id } }
        if (toSend.isEmpty()) {
            accountResults = refused
            accountBusy = false
            return@launch
        }
        when (val result = presenter.sendToPlayers(tournamentId, toSend)) {
            is AppResult.Success -> {
                accountResults = refused + result.value
                refresh(force = true).join()
            }
            is AppResult.Failure -> accountError = result.error.toUiText()
        }
        accountBusy = false
    }

    /** Sets or clears the pending email of one slot. */
    fun saveSlotEmail(slotId: Int, email: String) {
        emailSlotId = null
        scope.launch {
            accountBusy = true
            when (val saved = presenter.saveEmails(tournamentId, listOf(PlayerEmailItem(slotId, email)))) {
                is AppResult.Success -> {
                    refresh(force = true).join()
                    saved.value.firstOrNull { it.outcome == PlayerEmailOutcome.FAILED }?.let { accountOutcome = it.toFailedAction().toOutcome() }
                }
                is AppResult.Failure -> accountOutcome = AccountOutcome(saved.error.toUiText(), isError = true)
            }
            accountBusy = false
        }
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

    fun exportIdCards(sheet: IdCardSheetLayout?) {
        val unassigned = players.filterNot { it.isAssigned }
        if (unassigned.isNotEmpty()) {
            error = UiText.of(Res.string.assign_all_before_id_cards)
            return
        }

        error = null
        val tournament = tournaments.firstOrNull { it.id == tournamentId }
        backgroundExports.start("id-cards:$tournamentId", UiText.of(Res.string.top_bar_id_cards)) {
            presenter.generateIdCards(tournamentId, sheet).map { content ->
                val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                val fileName = generatedFileName(baseName, "id-cards", "pdf", Clock.System.now(), year = year)
                ExportFile(fileName, content, "application/pdf", saveIdCardsTitle)
            }
        }
    }

    val exportPlayers = rememberPlayersExport(tournamentId) { error = it }

    fun exportIdList() {
        val unassigned = players.filterNot { it.isAssigned }
        if (unassigned.isNotEmpty()) {
            error = UiText.of(Res.string.assign_all_before_id_list)
            return
        }

        error = null
        val tournament = tournaments.firstOrNull { it.id == tournamentId }
        backgroundExports.start("id-list:$tournamentId", UiText.of(Res.string.top_bar_id_list)) {
            presenter.generateIdList(tournamentId).map { content ->
                val baseName = tournament?.shortName?.ifBlank { tournament.name.take(10) } ?: "tournament"
                val year = tournament?.eventStartDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                val fileName = generatedFileName(baseName, "id-list", "xlsx", Clock.System.now(), year = year)
                ExportFile(fileName, content, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", saveIdListTitle)
            }
        }
    }

    val isTeamsTournament = tournaments.firstOrNull { it.id == tournamentId }?.isTeams == true
    val teamNamesById = remember(teams) { teams.associate { it.id to it.name } }
    // Each player's table in every round, in round order.
    val roundTablesByPlayerId = remember(tables) { roundTablesByPlayer(tables) }
    // The players whose tables per round are open. All of them start closed.
    var expandedTablesSlotIds by remember(tournamentId) { mutableStateOf(emptySet<Int>()) }
    // The search still finds a player by a table number.
    val tableNumbersByPlayerId = remember(roundTablesByPlayerId) {
        roundTablesByPlayerId.mapValues { (_, roundTables) -> roundTables.joinToString(" · ") { it.tableId.toString() } }
    }
    val tournamentCountryCodes = remember(players, playersByEma) {
        players.mapNotNull { slot ->
            slot.assignedEmaId?.let(playersByEma::get)?.country
        }.map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != "EU" }
            .distinct()
            .sorted()
    }
    val filteredPlayers = remember(players, playersByEma, teamNamesById, tableNumbersByPlayerId, countries, searchQuery, selectedCountryCodes) {
        val query = normalizeSearchText(searchQuery.trim())
        players.filter { slot ->
            val assigned = slot.assignedEmaId?.let(playersByEma::get)
            val name = assigned?.displayName.orEmpty()
            val country = assigned?.country.orEmpty()
            val team = teamNamesById[slot.team] ?: teamLabel(slot.team)
            val tablesForPlayer = tableNumbersByPlayerId[slot.id].orEmpty()
            val matchesSearch = query.isEmpty() ||
                listOf(
                    name,
                    assigned?.emaId.orEmpty(),
                    slot.email.orEmpty(),
                    slot.id.toString(),
                    team,
                    tablesForPlayer,
                    // The country matches by its code (ESP) or by its name (Spain).
                    country,
                    countries.firstOrNull { it.code.equals(country.trim(), ignoreCase = true) }?.name.orEmpty(),
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
        // A deactivated EMA player is not offered for new seats. Its existing seats keep it.
        val availablePlayers = basePlayers.filterNot { it.emaId in assignedEmaIds || it.disabled }
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
    emailSlotId?.let { id -> players.firstOrNull { it.id == id } }?.let { slot ->
        SlotEmailDialog(
            playerName = slot.assignedEmaId?.let { emaId -> basePlayers.firstOrNull { it.emaId == emaId }?.displayName } ?: playerLabel(slot.id),
            currentEmail = slot.email,
            hasEmail = slot.hasEmail,
            confirmLabel = stringResource(Res.string.save),
            busy = accountBusy,
            onSave = { email -> saveSlotEmail(slot.id, email) },
            onRemove = { saveSlotEmail(slot.id, "") },
            onDismiss = { emailSlotId = null },
        )
    }
    // The Invitations dialog shows its own loading dialog.
    if (accountBusy && !bulkAccountsOpen) SendingDialog()
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
            onSend = { playerIds, emails -> sendToPlayers(playerIds, emails = emails) },
            onDismiss = {
                bulkAccountsOpen = false
                accountResults = emptyList()
                accountError = null
            },
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
    val currentTournament = tournaments.firstOrNull { it.id == tournamentId }
    val canChangePlayerCount = currentTournament != null &&
        adminStatus?.can(Permission.TOURNAMENT_CONFIGURE, currentTournament.permissions) == true
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
        AssignEmaPlayerDialog(
            slotId = assignmentSlot.id,
            players = filteredBasePlayers,
            searchQuery = assignmentSearchQuery,
            onSearchQueryChange = { assignmentSearchQuery = it },
            onAssign = { player -> assign(assignmentSlot.id, player) },
            onDismiss = { assignmentSlotId = null },
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
        ClearAssignmentDialog(
            slotId = clearAssignmentSlot.id,
            assignedName = clearAssignmentSlot.assignedEmaId?.let(playersByEma::get)?.displayName,
            saving = savingId != null,
            onConfirm = { assign(clearAssignmentSlot.id, null) },
            onDismiss = { clearAssignmentSlotId = null },
        )
    }

    AppScaffold(
        title = stringResource(Res.string.tournament_players),
        subtitle = tournaments.firstOrNull { it.id == tournamentId }?.name?.ifBlank { null },
        shortSubtitle = tournaments.firstOrNull { it.id == tournamentId }?.shortName,
        isLoading = loading || savingId != null,
        showBackgroundLogo = loading && players.isEmpty(),
        onBack = { navController.popBackStack() },
        // The cards and the list to print, the teams and the invitations, left of the title. On a phone they go first in the menu.
        leadingActions = {
            AppTopBarActions(
                onIdCards = { idCardsDialogOpen = true },
                onIdList = ::exportIdList,
                onTeams = if (isTeamsTournament) {
                    { navController.navigate(TeamsRoute(tournamentId)) }
                } else {
                    null
                },
                onPlayerAccounts = {
                    accountError = null
                    accountResults = emptyList()
                    bulkAccountsOpen = true
                },
            )
        },
        actions = {
            AppTopBarActions(
                onImportPlayers = { importDialogOpen = true },
                importLocked = tournamentStarted,
                onExportPlayers = exportPlayers,
                onPlayerCount = currentTournament?.takeIf { canChangePlayerCount }?.let {
                    { navController.navigate(PlayerCountRoute(tournamentId = tournamentId, started = tournamentStarted)) }
                },
                playerCountLocked = currentTournament?.let { PlayerCountRules.blockerOf(it.copy(hasTableData = it.hasTableData || tournamentStarted)) } != null,
            )
        },
    ) {
        // The search and the country filter hide when the list scrolls down and show when it scrolls up.
        HideOnScrollArea {
                ScreenColumn(maxWidth = 1400.dp, contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShowTabsAtTop(assignmentsListState)
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
                    // The names of the staff saved the last time fill the dialog when they arrive.
                    var savedStaffNames by remember { mutableStateOf(emptyList<String>()) }
                    var staffCandidates by remember { mutableStateOf(emptyList<IdCardStaffCandidate>()) }
                    LaunchedEffect(tournamentId) {
                        (presenter.loadIdCardStaffNames(tournamentId) as? AppResult.Success)?.let { savedStaffNames = it.value }
                    }
                    LaunchedEffect(tournamentId) {
                        (presenter.loadIdCardStaffCandidates(tournamentId) as? AppResult.Success)?.let { staffCandidates = it.value }
                    }
                    IdCardsExportDialog(
                        savedStaffNames = savedStaffNames,
                        staffCandidates = staffCandidates,
                        onExport = { sheet ->
                            idCardsDialogOpen = false
                            exportIdCards(sheet)
                        },
                        onDismiss = { idCardsDialogOpen = false },
                    )
                }
                SectionCard(container = false) {
                    if (players.isNotEmpty()) {
                            HideOnScrollTabs {
                                            Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                                                OutlinedTextField(
                                                    value = searchQuery,
                                                    onValueChange = { searchQuery = it },
                                                    label = { Text(stringResource(Res.string.search)) },
                                                    singleLine = true,
                                                    trailingIcon = if (searchQuery.isNotEmpty()) {
                                                        {
                                                            IconButton(onClick = {
                                                                searchQuery = ""
                                                                searchFocusRequester.requestFocus()
                                                            }) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search)) }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                                                )
                                                if (tournamentCountryCodes.isNotEmpty()) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().horizontalScrollWithIndicators(rememberScrollState()),
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
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                                                ) {
                                                                    CountryFlag(code = code, contentDescription = stringResource(Res.string.filter_by_code, code))
                                                                    Spacer(Modifier.width(4.dp))
                                                                    ButtonText("$code ($count)")
                                                                }
                                                            }
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
                    val compact = LocalCompactLayout.current
                    // The column of the button that opens the tables, the same in the header and in every row.
                    val anyRoundTables = roundTablesByPlayerId.isNotEmpty()
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tablesToggleLabel = !compact && maxWidth >= TablesToggleLabelMinWidth
                    val tablesToggleWidth = if (tablesToggleLabel) TablesToggleLabeledWidth else TablesToggleIconWidth
                    Column(Modifier.fillMaxWidth()) {
                        if (!compact) {
                            DataTableHeaderRow {
                                TournamentPlayerHeader("ID", Modifier.width(TournamentPlayerIdColumnWidth), TextAlign.Center)
                                TournamentPlayerHeader(stringResource(Res.string.ema_number), Modifier.width(TournamentPlayerEmaColumnWidth))
                                TournamentPlayerHeader(stringResource(Res.string.country), Modifier.width(TournamentPlayerCountryColumnWidth), TextAlign.Center)
                                TournamentPlayerHeader(stringResource(Res.string.photo), Modifier.width(TournamentPlayerPhotoColumnWidth))
                                // The name and the team at the left. The space that is left over stays before the menu.
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TournamentPlayerHeader(stringResource(Res.string.name), TournamentPlayerNameModifier(), TextAlign.Start)
                                    if (isTeamsTournament) {
                                        TournamentPlayerHeader(stringResource(Res.string.team), TournamentPlayerTeamModifier(), TextAlign.Start)
                                    }
                                }
                                if (anyRoundTables) {
                                    TournamentPlayerHeader(stringResource(Res.string.tables), Modifier.width(tablesToggleWidth))
                                }
                                Spacer(Modifier.width(TournamentPlayerActionColumnWidth))
                            }
                        }
                        DataTableDivider()
                        // No bottom margin on the screen: the list reaches the bottom of the window.
                        LazyColumnWithScrollbar(
                            state = assignmentsListState,
                            // The list reaches the edges of the window, so its shadows take the whole width; the margins go inside.
                            scrollbar = MtsScrollbar.Overlay,
                            modifier = Modifier.weight(1f, fill = false).screenFullBleed(),
                            contentPadding = screenListPadding(bottom = 24.dp),
                        ) {
                            items(filteredPlayers.size, key = { filteredPlayers[it].id }) { index ->
                                val slot = filteredPlayers[index]
                                val assigned = slot.assignedEmaId?.let(playersByEma::get)
                                val playerCountry = assigned?.country.orEmpty()
                                val playerName = assigned?.displayName
                                val roundTables = roundTablesByPlayerId[slot.id].orEmpty()
                                DataTableRow(
                                    // A slot with a player is not clickable. The editor must use the menu and Clear to change it.
                                    onClick = if (slot.isAssigned) null else ({ openAssignment(slot.id) }),
                                    clickFocusable = false,
                                    highlighted = focusedAssignRowIndex == index || focusedClearRowIndex == index,
                                ) {
                                    if (compact) {
                                        // A phone shows the player in one cell: photo, name, then the other columns as small text.
                                        TournamentPlayerPhoto(
                                            photoUrl = assigned?.photoUrl,
                                            playerName = playerName.orEmpty(),
                                            columnWidth = TournamentPlayerCompactLeadingWidth,
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = (assigned?.let { "${it.firstName.trim()} ${it.lastName.trim()}" } ?: stringResource(Res.string.not_assigned)) +
                                                    (slot.substitution?.let { " (${it.label()})" } ?: ""),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (assigned == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                textDecoration = if (slot.substitution?.struckThrough == true) TextDecoration.LineThrough else null,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                if (assigned != null) CountryFlag(code = playerCountry)
                                                Text(
                                                    text = listOfNotNull(
                                                        "#${slot.id}",
                                                        assigned?.emaId?.let { "EMA $it" },
                                                        if (isTeamsTournament) teamNamesById[slot.team] ?: teamLabel(slot.team) else null,
                                                    ).joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    } else {
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
                                            // An empty slot has no flag. A player without country shows the globe of the guests.
                                            if (assigned != null) CountryFlag(code = playerCountry)
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
                                        // The name and the team at the left. The space that is left over stays before the menu.
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                        // The whole name on one line. A very long name wraps to a second line.
                                        Text(
                                            if (assigned == null) {
                                                stringResource(Res.string.not_assigned)
                                            } else {
                                                assigned.firstName.trim() + " " + assigned.lastName.trim()
                                            } + (slot.substitution?.let { " (${it.label()})" } ?: ""),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textDecoration = if (slot.substitution?.struckThrough == true) TextDecoration.LineThrough else null,
                                            modifier = TournamentPlayerNameModifier(),
                                            textAlign = TextAlign.Start,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isTeamsTournament) {
                                            Text(
                                                text = teamNamesById[slot.team] ?: teamLabel(slot.team),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = TournamentPlayerTeamModifier(),
                                                textAlign = TextAlign.Start,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        }
                                    }
                                    // The tables of every round open under the player. They start closed, so the list stays short.
                                    if (anyRoundTables) {
                                        Box(Modifier.width(tablesToggleWidth), contentAlignment = Alignment.Center) {
                                        if (roundTables.isNotEmpty()) {
                                        PlayerRoundTablesToggle(
                                            expanded = slot.id in expandedTablesSlotIds,
                                            showLabel = tablesToggleLabel,
                                            onToggle = {
                                                expandedTablesSlotIds = if (slot.id in expandedTablesSlotIds) {
                                                    expandedTablesSlotIds - slot.id
                                                } else {
                                                    expandedTablesSlotIds + slot.id
                                                }
                                            },
                                        )
                                        }
                                        }
                                    }
                                    Box(
                                        modifier = if (compact) Modifier else Modifier.width(TournamentPlayerActionColumnWidth),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        if (!slot.isAssigned) {
                                            Box(
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
                                                ) { ButtonText(stringResource(Res.string.assign), maxLines = 1, overflow = TextOverflow.Clip) }
                                            }
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
                                                    // Invitations and reminders go out from the Invitations dialog only. The menu keeps the edit
                                                    // of the email, because that dialog asks only for the emails that are missing.
                                                    if (slot.isAssigned && !slot.hasAccount) {
                                                        DropdownMenuItem(
                                                            enabled = accountActionsEnabled,
                                                            text = { Text(stringResource(Res.string.edit_email)) },
                                                            onClick = {
                                                                actionsMenuOpen = false
                                                                emailSlotId = slot.id
                                                            },
                                                        )
                                                    }
                                                    // Super admins and club admins edit an account. The edit dialog has the button to reset the password.
                                                    val accountUid = slot.accountUid
                                                    if (canEditAccounts && slot.hasAccount && accountUid != null) {
                                                        DropdownMenuItem(
                                                            enabled = accountActionsEnabled,
                                                            text = { Text(stringResource(Res.string.edit_account)) },
                                                            onClick = {
                                                                actionsMenuOpen = false
                                                                navController.navigate(UsersRoute(editUid = accountUid))
                                                            },
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
                                // A second line under the player: the table of every round, side by side.
                                if (roundTables.isNotEmpty() && slot.id in expandedTablesSlotIds) {
                                    PlayerRoundTablesStrip(
                                        roundTables = roundTables,
                                        titleWidth = if (compact) TournamentPlayerCompactLeadingWidth else TournamentPlayerIdColumnWidth,
                                        modifier = Modifier.padding(bottom = MtsTheme.spacing.sm),
                                    )
                                }
                                DataTableDivider()
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
