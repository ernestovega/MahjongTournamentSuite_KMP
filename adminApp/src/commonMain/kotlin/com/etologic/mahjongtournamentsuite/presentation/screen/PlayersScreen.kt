package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountRules
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
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
import com.etologic.mahjongtournamentsuite.presentation.components.LazyColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.LoadMissingSession
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogButtons
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogText
import com.etologic.mahjongtournamentsuite.presentation.components.MtsDialogTitle
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SectionCard
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.horizontalScrollWithIndicators
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.rememberBackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
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
import mahjongtournamentsuite.adminapp.generated.resources.assign_ema_player_to_player
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_2
import mahjongtournamentsuite.adminapp.generated.resources.clear_assignment_message
import mahjongtournamentsuite.adminapp.generated.resources.clear_search
import mahjongtournamentsuite.adminapp.generated.resources.country
import mahjongtournamentsuite.adminapp.generated.resources.country_n
import mahjongtournamentsuite.adminapp.generated.resources.edit_account
import mahjongtournamentsuite.adminapp.generated.resources.edit_email
import mahjongtournamentsuite.adminapp.generated.resources.ema_number
import mahjongtournamentsuite.adminapp.generated.resources.filter_by_code
import mahjongtournamentsuite.adminapp.generated.resources.name
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
import mahjongtournamentsuite.adminapp.generated.resources.player_n
import mahjongtournamentsuite.adminapp.generated.resources.round
import mahjongtournamentsuite.adminapp.generated.resources.save
import mahjongtournamentsuite.adminapp.generated.resources.save_id_cards
import mahjongtournamentsuite.adminapp.generated.resources.save_id_list
import mahjongtournamentsuite.adminapp.generated.resources.search
import mahjongtournamentsuite.adminapp.generated.resources.substitute
import mahjongtournamentsuite.adminapp.generated.resources.substitution
import mahjongtournamentsuite.adminapp.generated.resources.table
import mahjongtournamentsuite.adminapp.generated.resources.tables
import mahjongtournamentsuite.adminapp.generated.resources.team
import mahjongtournamentsuite.adminapp.generated.resources.team_n
import mahjongtournamentsuite.adminapp.generated.resources.this_assignment
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

        TrackedDialog("AssignEmaPlayerToPlayer") {
            AlertDialog(
                onDismissRequest = { assignmentSlotId = null },
                modifier = Modifier.appFocusGroup(),
                title = { MtsDialogTitle(stringResource(Res.string.assign_ema_player_to_player, assignmentSlot.id)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = assignmentSearchQuery,
                            onValueChange = { assignmentSearchQuery = it },
                            label = { Text(stringResource(Res.string.search)) },
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
                                                            if (index == 0) {
                                                                searchFocusRequester.requestFocus()
                                                            } else {
                                                                requestPlayerFocus(index - 1)
                                                            }
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
                    MtsDialogButtons {
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
                    }
                },
            )
        }
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

        TrackedDialog("ClearAssignment") {
            AlertDialog(
                modifier = Modifier.appFocusGroup(),
                onDismissRequest = { clearAssignmentSlotId = null },
                title = { MtsDialogTitle(stringResource(Res.string.clear_assignment)) },
                text = {
                    MtsDialogText(
                        stringResource(
                            Res.string.clear_assignment_message,
                            assignedName ?: stringResource(Res.string.this_assignment),
                            clearAssignmentSlot.id,
                        ),
                    )
                },
                confirmButton = {
                    MtsDialogButtons {
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
                    }
                },
            )
        }
    }

    AppScaffold(
        title = stringResource(Res.string.tournament_players),
        subtitle = tournaments.firstOrNull { it.id == tournamentId }?.name?.ifBlank { null },
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
                    LazyColumnWithScrollbar(
                        state = assignmentsListState,
                        scrollbar = MtsScrollbar.Margin,
                        modifier = Modifier.weight(1f, fill = false),
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
                                            ) { Text(stringResource(Res.string.assign), maxLines = 1, overflow = TextOverflow.Clip) }
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

private val TournamentPlayerPhotoSize = 40.dp
private val TournamentPlayerPhotoColumnWidth = 48.dp
private val TournamentPlayerCountryColumnWidth = 64.dp
private val TournamentPlayerIdColumnWidth = 48.dp
private val TournamentPlayerEmaColumnWidth = 96.dp

// The widths of the name and the team when there is room. The space left over stays before the menu.
private val TournamentPlayerNameMaxWidth = 300.dp
private val TournamentPlayerTeamColumnWidth = 180.dp

// The button that opens the tables of a player shows its text from this width of the list, and only the arrow below it.
private val TablesToggleLabelMinWidth = 1000.dp
private val TablesToggleLabeledWidth = 112.dp
// Wide enough for the title of the column above the arrow.
private val TablesToggleIconWidth = 64.dp

/**
 * Inside the row of the name and the team: the same width in the header and in every row, so the columns line up.
 * Not filled: the fixed width, or less when the row is narrower. Never the width of the text.
 * On a narrow screen the name keeps two thirds of the room and the team one third.
 */
@Composable
private fun RowScope.TournamentPlayerNameModifier(): Modifier = Modifier.weight(2f, fill = false).width(TournamentPlayerNameMaxWidth)

@Composable
private fun RowScope.TournamentPlayerTeamModifier(): Modifier = Modifier.weight(1f, fill = false).width(TournamentPlayerTeamColumnWidth)

// The photo column of a phone. The tables title below it takes the same width.
private val TournamentPlayerCompactLeadingWidth = 44.dp
private val RoundTableCellWidth = 36.dp

/** One round and the table where a player sits in it. */
internal data class PlayerRoundTable(val roundId: Int, val tableId: Int)

/** Every player's table per round, in round order. A player has at most one table per round. */
internal fun roundTablesByPlayer(tables: List<TournamentTable>): Map<Int, List<PlayerRoundTable>> = tables
    .sortedWith(compareBy(TournamentTable::roundId, TournamentTable::tableId))
    .flatMap { table -> table.playerIds.map { playerId -> playerId to PlayerRoundTable(table.roundId, table.tableId) } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })

/**
 * The "Tables" title and a small two-row table: the round on top and the table below.
 * The table is as wide as the row: the cells share the free width. When the rounds do not fit, the title and the
 * table scroll sideways from one edge of the row to the other, with a scrollbar on desktop and web.
 */
@Composable
private fun PlayerRoundTablesToggle(
    expanded: Boolean,
    showLabel: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(Res.string.tables)
    val icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore
    if (showLabel) {
        TextButton(onClick = onToggle, modifier = Modifier.testTag(PlayerRoundTablesToggleTag)) {
            Text(label, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null)
        }
    } else {
        IconButton(onClick = onToggle, modifier = Modifier.testTag(PlayerRoundTablesToggleTag)) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun PlayerRoundTablesStrip(
    roundTables: List<PlayerRoundTable>,
    titleWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val shape = MaterialTheme.shapes.small
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().testTag(PlayerRoundTablesTag)) {
        // Whole pixels, so the cells add up to the row exactly and nothing scrolls by a pixel.
        val cellWidths = with(density) {
            val divider = 1.dp.roundToPx()
            val free = maxWidth.roundToPx() -
                (RoundTableStripPadding * 2 + titleWidth + RoundTableStripGap + RoundTableLabelWidth).roundToPx() -
                // The dividers between cells and the 1 dp inside the border on each side.
                divider * (roundTables.size + 2)
            val minCell = RoundTableCellWidth.roundToPx()
            val even = free / roundTables.size
            if (even < minCell) {
                List(roundTables.size) { minCell.toDp() }
            } else {
                // The pixels left over go to the first cells, one each.
                val extra = free - even * roundTables.size
                List(roundTables.size) { index -> (even + if (index < extra) 1 else 0).toDp() }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScrollWithIndicators(scrollState)
                    .padding(horizontal = RoundTableStripPadding),
                horizontalArrangement = Arrangement.spacedBy(RoundTableStripGap),
                verticalAlignment = Alignment.Top,
            ) {
                // The title sits under the ID (desktop) or the photo (phone).
                Box(
                    modifier = Modifier.width(titleWidth).height(RoundTableCellHeight * 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.tables),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .border(1.dp, borderColor, shape)
                        .padding(1.dp),
                ) {
                    // The labels stay as the first column so each row says what it holds.
                    RoundTableCell(
                        top = stringResource(Res.string.round),
                        bottom = stringResource(Res.string.table),
                        width = RoundTableLabelWidth,
                        isLabel = true,
                    )
                    roundTables.forEachIndexed { index, roundTable ->
                        Box(Modifier.width(1.dp).height(RoundTableCellHeight * 2).background(borderColor))
                        RoundTableCell(
                            top = roundTable.roundId.toString(),
                            bottom = roundTable.tableId.toString(),
                            width = cellWidths[index],
                        )
                    }
                }
            }
        }
    }
}

/** Test tag of each player's round and table strip. */
internal const val PlayerRoundTablesTag = "playerRoundTables"
internal const val PlayerRoundTablesToggleTag = "playerRoundTablesToggle"
private val RoundTableStripPadding = 12.dp
private val RoundTableStripGap = 12.dp
private val RoundTableLabelWidth = 60.dp
private val RoundTableCellHeight = 28.dp

@Composable
private fun RoundTableCell(
    top: String,
    bottom: String,
    width: Dp,
    isLabel: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundTableCellHeight)
                .background(colors.surfaceVariant)
                .padding(horizontal = MtsTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = top,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundTableCellHeight)
                .padding(horizontal = MtsTheme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bottom,
                style = if (isLabel) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (isLabel) colors.onSurfaceVariant else colors.onSurface,
                fontWeight = if (isLabel) null else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
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
    columnWidth: Dp,
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
