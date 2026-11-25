package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DraggableScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LastPlaceIcon
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.PlatformBackHandler
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.SeatDisplay
import com.etologic.mahjongtournamentsuite.presentation.components.SeatLevelMedal
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.hideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberHideOnScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentOptionalInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import kotlinx.coroutines.flow.map
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.menu_sign_in
import mahjongtournamentsuite.playerapp.generated.resources.ranking_teams
import mahjongtournamentsuite.playerapp.generated.resources.round_n
import mahjongtournamentsuite.playerapp.generated.resources.scope_tournament
import mahjongtournamentsuite.playerapp.generated.resources.sign_in_needed_title
import mahjongtournamentsuite.playerapp.generated.resources.stats_round_table
import mahjongtournamentsuite.playerapp.generated.resources.status_finished
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_all
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_me
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables_team
import mahjongtournamentsuite.playerapp.generated.resources.table_hands_done
import mahjongtournamentsuite.playerapp.generated.resources.table_n
import mahjongtournamentsuite.playerapp.generated.resources.tables_all_empty
import mahjongtournamentsuite.playerapp.generated.resources.tables_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.tables_me_sign_in
import mahjongtournamentsuite.playerapp.generated.resources.tables_mine_empty
import mahjongtournamentsuite.playerapp.generated.resources.tables_no_match
import mahjongtournamentsuite.playerapp.generated.resources.tables_not_linked
import mahjongtournamentsuite.playerapp.generated.resources.tables_team_empty
import mahjongtournamentsuite.playerapp.generated.resources.tables_team_sign_in
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The width of the tab rows and of the tabs of a tournament, except the statistics, on a wide screen. */
internal val TournamentColumnWidth = 720.dp

@Composable
fun TournamentScreen(navController: NavHostController, tournamentId: String, myTables: Boolean = false) {
    val (view, message) = rememberTournamentView(tournamentId)
    val session = koinInject<PlayerSessionPresenter>()
    // Null until the session is known, so a guest never sees the empty texts of an account for a moment.
    val signedIn by produceState<Boolean?>(null) { value = session.hasSession() }
    // An account that is not linked to a player has no own tables. It starts on All tables.
    // A negative value means that the player did not choose a tab.
    // The name changed when the Players tab came in, so an old saved value does not open the wrong tab.
    var tab by rememberPersistentInt("tournamentTab", MainTab.TABLES)

    // A player opens on its own screen, over the tournament. Back comes to the same tab, with the same scroll.
    fun openPlayer(slot: Int, statistics: Boolean = false) {
        navController.navigate(TournamentPlayerRoute(tournamentId, slot, if (statistics) PlayerTab.STATISTICS else PlayerTab.TABLES))
    }

    // The own results: the Results tab of the screen of the signed-in player.
    fun openMyResults(slot: Int) {
        navController.navigate(TournamentPlayerRoute(tournamentId, slot, PlayerTab.SUMMARY))
    }
    // The team open in the Teams tab, and the tab of that team: 0 is Players and 1 is Tables.
    var selectedTeam by rememberPersistentOptionalInt("selectedTeam")
    var teamTab by rememberPersistentInt("teamTab", 0)
    // The tabs in their order. Teams shows only in a tournament with teams.
    val inRegistration = view?.tournament?.inRegistration == true
    val mainTabs = MainTab.visible(isTeams = view?.tournament?.isTeams == true, inRegistration = inRegistration)
    // A tournament that takes registrations opens on Players, the list of the registrations.
    val shownTab = tab.takeIf { it in mainTabs } ?: mainTabs.first()
    val teamOpen = shownTab == MainTab.TEAMS && selectedTeam != null
    PlatformBackHandler(enabled = teamOpen) { selectedTeam = null }
    var chosenTablesTab by rememberPersistentInt("tablesTab", -1)
    // The QR of the tournament opens My tables one time. Later the player moves freely.
    var myTablesOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(myTables) {
        if (myTables && !myTablesOpened) {
            tab = MainTab.TABLES
            chosenTablesTab = 0
            myTablesOpened = true
        }
    }
    // The values are ids: 0 is My tables, 1 is All tables and 2 is Team tables (only in a tournament with teams).
    val tablesTabs = if (view?.tournament?.isTeams == true) listOf(0, 2, 1) else listOf(0, 1)
    val tablesTabId = chosenTablesTab.takeIf { it in tablesTabs } ?: if (view?.mySlotId == null) 1 else 0
    val tablesTab = tablesTabs.indexOf(tablesTabId)
    // The statistics tab tells this screen when it loads, so the progress bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }
    // The tab rows hide when the content scrolls down and show when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()

    val account = rememberAccountActions(navController)
    account.Dialogs()
    val drawExplanation = rememberDrawExplanationState()
    drawExplanation.Dialog()
    // The slot of the signed-in player when the floating button of the own results shows: on Tables and Ranking.
    val myResultsSlot = view?.mySlotId?.takeIf { shownTab == MainTab.TABLES || shownTab == MainTab.RANKING }
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        // An open team names the screen.
        title = when {
            teamOpen -> view?.teamNameOf(selectedTeam) ?: stringResource(Res.string.ranking_teams)
            else -> view?.tournament?.name ?: stringResource(Res.string.scope_tournament)
        },
        subtitle = if (teamOpen) view?.tournament?.name else null,
        isLoading = (view == null && message == null) || ((shownTab == MainTab.STATISTICS || teamOpen) && statisticsLoading),
        autoFocusFirst = false,
        onBack = { if (teamOpen) selectedTeam = null else navController.popBackStack() },
        // The same buttons as the list of tournaments. On a phone they go into the menu.
        // A signed-in player of the tournament also has a way to the own results.
        actions = { InfoTopBarButtons(navController, drawExplanation, onMyResults = view?.mySlotId?.let { slot -> { openMyResults(slot) } }) },
        // On Tables and Ranking a floating button opens the own results.
        floatingActionButton = myResultsSlot?.let { slot ->
            { MyResultsButton { openMyResults(slot) } }
        },
    ) {
        // The tab row touches the top bar, so the screen has no padding. The content under the tabs has its own.
        // The provider is outside the column, so all the tab rows use the same state.
        // The lists end above the floating buttons, so their last row never stays under them.
        val fabClearance = if (myResultsSlot == null) 0.dp else FabClearance + if (MyTableButtonState.shown) MyTableButtonClearance else 0.dp
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll, LocalFabClearance provides fabClearance) {
            // The screen uses the full width. The tab rows and the other tabs keep a narrow column. The statistics use all the width.
            ScreenColumn(maxWidth = Dp.Infinity, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (view == null) {
                    MessageDialog(message)
                    return@ScreenColumn
                }
                HideOnScrollTabs(shownTab) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val selectedIndex = mainTabs.indexOf(shownTab)
                        MtsTabRow(
                            selectedTabIndex = selectedIndex,
                            modifier = Modifier.widthIn(max = TournamentColumnWidth),
                        ) {
                            mainTabs.forEach { id ->
                                CompactTab(
                                    selected = shownTab == id,
                                    onClick = {
                                        // The Teams tab goes back to its list when it is already open.
                                        if (id == shownTab && id == MainTab.TEAMS) selectedTeam = null
                                        tab = id
                                    },
                                    title = stringResource(MainTab.title(id)),
                                    trackingName = MainTab.trackingName(id),
                                    icon = {
                                        val icon = when (id) {
                                            MainTab.TABLES -> Icons.Default.TableRestaurant
                                            MainTab.RANKING -> Icons.Default.Leaderboard
                                            MainTab.PLAYERS -> Icons.Default.Person
                                            MainTab.TEAMS -> Icons.Default.Groups
                                            MainTab.STATISTICS -> StatisticsIcon
                                            else -> Icons.Default.Info
                                        }
                                        Icon(icon, contentDescription = null, Modifier.size(20.dp))
                                    },
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).align(Alignment.CenterHorizontally).hideOnScroll(hideOnScroll)
                        .then(if (shownTab == MainTab.STATISTICS) Modifier.fillMaxWidth() else Modifier.widthIn(max = TournamentColumnWidth).fillMaxWidth()),
                ) {
                    MessageDialog(message)
                    val open: (PlayerTable) -> Unit = { navController.navigate(TableRoute(tournamentId, it.roundId, it.tableId)) }
                    when (shownTab) {
                        MainTab.TABLES -> {
                            HideOnScrollTabs(tablesTab) {
                                MtsTabRow(
                                    selectedTabIndex = tablesTab,
                                ) {
                                    tablesTabs.forEachIndexed { index, id ->
                                        val title = when (id) {
                                            0 -> stringResource(Res.string.tab_tables_me)
                                            2 -> stringResource(Res.string.tab_tables_team)
                                            else -> stringResource(Res.string.tab_tables_all)
                                        }
                                        CompactTab(
                                            selected = tablesTab == index,
                                            onClick = { chosenTablesTab = id },
                                            title = title,
                                            trackingName = when (id) {
                                                0 -> "TablesMe"
                                                2 -> "TablesTeam"
                                                else -> "TablesAll"
                                            },
                                        )
                                    }
                                }
                            }
                            if (signedIn == null && tablesTabId != 1) {
                                Unit
                            } else if (signedIn == false && tablesTabId != 1) {
                                // A guest has no own tables or team. The tab says how to get them.
                                SignInPrompt(
                                    message = stringResource(if (tablesTabId == 0) Res.string.tables_me_sign_in else Res.string.tables_team_sign_in),
                                    onSignIn = { navController.navigate(SignInRoute()) },
                                )
                            } else if (tablesTabId == 0) {
                                TableList(
                                    view,
                                    view.myTables,
                                    if (view.mySlotId == null) {
                                        stringResource(Res.string.tables_not_linked)
                                    } else {
                                        stringResource(Res.string.tables_mine_empty)
                                    },
                                    open,
                                    stickyRounds = false,
                                )
                            } else if (tablesTabId == 2) {
                                TableList(
                                    view,
                                    view.teamTables,
                                    stringResource(Res.string.tables_team_empty),
                                    open,
                                    filterName = "team",
                                )
                            } else {
                                TableList(view, view.tables, stringResource(Res.string.tables_all_empty), open, filterName = "all")
                            }
                        }
                        MainTab.RANKING -> RankingTab(view) { openPlayer(it) }
                        MainTab.PLAYERS -> if (inRegistration) RegistrationsTab(view.tournament) else PlayersTab(view) { openPlayer(it) }
                        MainTab.TEAMS -> TeamsTab(
                            view = view,
                            selectedTeam = selectedTeam,
                            teamTab = teamTab,
                            onTeamTabChange = { teamTab = it },
                            onSelect = {
                                selectedTeam = it
                                teamTab = 0
                            },
                            onOpenPlayer = { openPlayer(it) },
                            onOpenTable = open,
                            onStatisticsLoading = { statisticsLoading = it },
                        )
                        MainTab.STATISTICS -> TournamentStatisticsTab(tournamentId) { statisticsLoading = it }
                        else -> TournamentInfoTab(view)
                    }
                }
            }
        }
    }
}

internal fun datesOf(tournament: Tournament): String? = formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)

/**
 * The four players of a table in the order of hand 1 (East, South, West, North).
 * Before the players set the seats, the list has the players of the draw and the order has no meaning.
 */
internal fun PlayerTable.seatsFor(view: PlayerTournamentView, withLevelChange: Boolean = false): List<SeatDisplay> {
    val t = table
    fun penalty(select: (TableHand) -> String) = hands.sumOf { select(it).trim().toIntOrNull() ?: 0 }
    fun seat(id: String, score: String, points: String, penalty: Int): SeatDisplay {
        val slot = id.toIntOrNull()
        val after = if (withLevelChange) view.statisticsAfter(slot, roundId) else null
        return SeatDisplay(
            name = view.nameOf(slot),
            country = slot?.let { view.players[it]?.country }.orEmpty(),
            score = score,
            points = points,
            penalty = penalty,
            isMine = slot != null && slot == view.mySlotId,
            slotId = slot,
            // The table screen shows the level after this round. The table list shows the level now.
            level = after?.level ?: if (withLevelChange) null else view.levelOf(slot),
            skillScore = after?.skillScore ?: if (withLevelChange) null else view.skillScoreOf(slot),
            levelProvisional = after?.isLevelProvisional ?: view.isLevelProvisional(slot),
            levelChange = if (withLevelChange && table.isCompleted) view.levelChangeAt(slot, roundId) else null,
        )
    }
    if (!seatsAssigned) {
        return (0 until 4).map { seat(t.playerIds.getOrNull(it)?.toString().orEmpty(), "", "", 0) }
    }
    // The table points come only from the scores of the table.
    val scores = t.seatScores
    val points = t.seatPoints
    return listOf(
        seat(t.playerEastId, scores[0], points[0], penalty { it.playerEastPenalty }),
        seat(t.playerSouthId, scores[1], points[1], penalty { it.playerSouthPenalty }),
        seat(t.playerWestId, scores[2], points[2], penalty { it.playerWestPenalty }),
        seat(t.playerNorthId, scores[3], points[3], penalty { it.playerNorthPenalty }),
    )
}

/** The empty state of a tab that needs an account, with the button that opens the sign-in. */
@Composable
private fun SignInPrompt(message: String, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(Icons.AutoMirrored.Filled.Login, stringResource(Res.string.sign_in_needed_title), message)
        Button(onClick = onSignIn) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(Res.string.menu_sign_in), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun TableList(
    view: PlayerTournamentView,
    tables: List<PlayerTable>,
    emptyText: String,
    onOpen: (PlayerTable) -> Unit,
    stickyRounds: Boolean = true,
    filterName: String? = null,
) {
    if (tables.isEmpty()) {
        Box(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) { EmptyState(Icons.Default.People, stringResource(Res.string.tables_empty_title), emptyText) }
        return
    }
    // A long list has filters on top. They hide with the tabs when the list scrolls down.
    var filterText by rememberPersistentText("table-filter-" + filterName.orEmpty(), null)
    val filter = if (filterName == null) TableFilter() else TableFilter.decode(filterText)
    val shown = filter.apply(view, tables)
    val rounds = shown.sortedBy { it.roundId }.groupBy { it.roundId }
    Column {
        if (filterName != null) {
            HideOnScrollTabs {
                TableFilterBar(
                    filter = filter,
                    onChange = { filterText = it.encode() },
                    rounds = tables.map { it.roundId }.distinct().sorted(),
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
                EmptyState(Icons.Default.People, stringResource(Res.string.tables_no_match), "")
            }
            return@Column
        }
        val state = rememberPersistentLazyListState("tables-" + emptyText)
        // A new filter starts the list at the top. The restored filter keeps the restored position.
        var lastFilter by remember { mutableStateOf(filter) }
        LaunchedEffect(filter) {
            if (filter != lastFilter) {
                lastFilter = filter
                state.scrollToItem(0)
            }
        }
        Box(Modifier.weight(1f)) {
            // The list uses all the width. Its padding is inside, so it ends at the tabs and at the bottom of the screen.
            LazyColumn(
                state = state,
                // The draggable scrollbar of the tables is the scrollbar of this list.
                modifier = Modifier.fillMaxSize().scrollIndicators(state, scrollbar = MtsScrollbar.None),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp + LocalFabClearance.current),
            ) {
                rounds.forEach { (roundId, roundTables) ->
                    // In the sticky case, the title of a round stays at the top while its tables scroll under it.
                    if (stickyRounds) {
                        stickyHeader(key = "round_$roundId") { RoundHeader(roundId, view.roundStartOf(roundId)) }
                    } else {
                        item(key = "round_$roundId") { RoundHeader(roundId, view.roundStartOf(roundId)) }
                    }
                    items(roundTables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                        TableSummaryCard(view, table, highlightSlot = view.mySlotId, onClick = { onOpen(table) })
                    }
                }
            }
            TableScrollbar(state, shown, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** The scrollbar of a table list. While the person drags it, a bubble says which round and table show. */
@Composable
internal fun TableScrollbar(state: LazyListState, tables: List<PlayerTable>, modifier: Modifier = Modifier) {
    val roundNames = tables.map { it.roundId }.distinct().associateWith { stringResource(Res.string.round_n, it) }
    val tableNames = tables.map { it.tableId }.distinct().associateWith { stringResource(Res.string.table_n, it) }
    DraggableScrollbar(state, modifier) { item ->
        val ids = (item.key as? String)?.split('_')?.mapNotNull { it.toIntOrNull() }
        if (ids == null || ids.size != 2) return@DraggableScrollbar null
        listOfNotNull(roundNames[ids[0]], tableNames[ids[1]]).joinToString(" · ").ifEmpty { null }
    }
}

/** The title of a group of tables. It has a solid background, so the tables do not show through it. */
@Composable
private fun RoundHeader(roundId: Int, start: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .layout { measurable, constraints ->
                // The content padding of the list narrows the header, so it grows back to the full width.
                val extra = 32.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-16.dp.roundToPx(), 0) }
            }
            .background(colors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The round name keeps its whole width: the start takes the rest, aligned to the end, and wraps when it does not fit.
        Text(
            stringResource(Res.string.round_n, roundId),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        if (start != null) {
            // The clock is part of the text, so it stays next to the start also when the start wraps to two lines.
            val clock = "clock"
            Text(
                buildAnnotatedString {
                    appendInlineContent(clock, "\u23F0")
                    append("\u00A0")
                    append(start)
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                inlineContent = mapOf(
                    clock to InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, Modifier.fillMaxSize(), tint = colors.onSurfaceVariant)
                    },
                ),
            )
        }
    }
}

/** A table with its four players. The points of the table show only for a complete table. */
@Composable
internal fun TableSummaryCard(
    view: PlayerTournamentView,
    table: PlayerTable,
    highlightSlot: Int?,
    /** True in a list without round headers, such as the tables of one player: the header names the round too. */
    showRound: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val complete = table.isComplete
    val ongoing = !complete && table.doneHands > 0
    // The header color shows the state of the table: finished, ongoing or not started.
    val headerColor = when {
        complete -> colors.primaryContainer
        ongoing -> colors.tertiaryContainer
        else -> colors.surfaceContainerHighest
    }
    val headerContent = when {
        complete -> colors.onPrimaryContainer
        ongoing -> colors.onTertiaryContainer
        else -> colors.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(headerColor).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (showRound) stringResource(Res.string.stats_round_table, table.roundId, table.tableId) else stringResource(Res.string.table_n, table.tableId),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headerContent,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                val statusIcon = when {
                    complete -> Icons.Default.CheckCircle
                    ongoing -> Icons.Default.PlayArrow
                    else -> Icons.Default.HourglassEmpty
                }
                Icon(statusIcon, contentDescription = null, Modifier.size(20.dp), tint = headerContent)
                Text(
                    when {
                        complete -> stringResource(Res.string.status_finished)
                        else -> stringResource(Res.string.table_hands_done, table.doneHands, table.hands.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = headerContent,
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Seat order (east to north) while the table is open. Winner to last when it is complete.
            val seats = table.seatsFor(view).let { all ->
                if (complete) {
                    // The table points come from the scores and use a decimal comma ("1,5"), so sort by score.
                    all.sortedByDescending { it.score.trim().toIntOrNull() ?: Int.MIN_VALUE }
                } else {
                    all
                }
            }
            seats.forEachIndexed { position, seat ->
                val highlighted = seat.slotId != null && seat.slotId == highlightSlot
                val teammate = !highlighted && view.isTeammate(seat.slotId)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            when {
                                highlighted -> colors.primaryContainer.copy(alpha = 0.45f)
                                // A darker tone of the same green marks the other members of my team.
                                // The light theme uses the primary green, because the other color has the tone of the card there.
                                teammate -> if (colors.background.luminance() < 0.5f) {
                                    colors.onPrimaryContainer.copy(alpha = 0.2f)
                                } else {
                                    colors.primary.copy(alpha = 0.3f)
                                }
                                else -> Color.Transparent
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                ) {
                    if (complete) {
                        // Seats are sorted by result here. The fourth place has a sad face.
                        if (position < 3) PositionTrophy(position + 1, size = 24.dp) else LastPlaceIcon(size = 24.dp)
                    }
                    PlayerFlag(seat.country)
                    SeatLevelMedal(seat, size = 24.dp)
                    Text(
                        seat.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        // The names of the players are always bold.
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (complete && seat.points.isNotBlank()) {
                        // The table points have the same outlined style as in the ranking.
                        Surface(
                            shape = MtsTheme.shapes.pill,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, colors.onSurface),
                        ) {
                            Text(
                                formatPoints(seat.points),
                                Modifier.padding(horizontal = MtsTheme.spacing.sm, vertical = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        seat.score.ifBlank { "-" },
                        Modifier.width(56.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
