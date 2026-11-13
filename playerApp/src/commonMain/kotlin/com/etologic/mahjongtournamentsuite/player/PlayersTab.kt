package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.LocalHideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.hideOnScroll
import com.etologic.mahjongtournamentsuite.presentation.components.rememberHideOnScrollState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentInt
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_search_label
import mahjongtournamentsuite.playerapp.generated.resources.player_position
import mahjongtournamentsuite.playerapp.generated.resources.players_search_empty
import mahjongtournamentsuite.playerapp.generated.resources.players_search_hint
import mahjongtournamentsuite.playerapp.generated.resources.ranking_teams
import mahjongtournamentsuite.playerapp.generated.resources.scope_player
import mahjongtournamentsuite.playerapp.generated.resources.scope_players
import mahjongtournamentsuite.playerapp.generated.resources.stats_title
import mahjongtournamentsuite.playerapp.generated.resources.tab_info
import mahjongtournamentsuite.playerapp.generated.resources.tab_ranking
import mahjongtournamentsuite.playerapp.generated.resources.tab_stats
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables
import mahjongtournamentsuite.playerapp.generated.resources.tables_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.tables_player_empty_message
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

/** The test tag of the search field of the player list. */
const val PLAYER_SEARCH_TAG = "player-search"

/** One line of the player list of a tournament. */
internal data class PlayerListRow(
    val slotId: Int,
    val name: String,
    val country: String?,
    val team: String?,
    val position: Int?,
    val mine: Boolean,
)

/** The players of the tournament by name, each with the place in the ranking and the team. */
internal fun PlayerTournamentView.playerRows(): List<PlayerListRow> {
    val positions = rankings.players.associate { it.playerId to it.position }
    return players.values
        .map { entry ->
            PlayerListRow(
                slotId = entry.slotId,
                name = nameOf(entry.slotId),
                country = entry.country.takeIf { it.isNotBlank() },
                team = if (tournament.isTeams) teamNames[entry.team] else null,
                position = positions[entry.slotId],
                mine = entry.slotId == mySlotId,
            )
        }
        .sortedWith(compareBy<PlayerListRow> { it.name.lowercase() }.thenBy { it.slotId })
}

/** True when the line has every word of [query] in the name, the country or the team. Accents and case do not count. */
internal fun PlayerListRow.matches(query: String): Boolean {
    val words = query.split(' ', '\t').map(::normalizeSearchText).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = listOfNotNull(name, country, team).joinToString(" ") { normalizeSearchText(it) }
    return words.all { it in text }
}

/** The Players tab of a tournament: the players, with a search on top. A tap opens the screen of the player ([onSelect]). */
@Composable
internal fun PlayersTab(view: PlayerTournamentView, onSelect: (Int) -> Unit) {
    PlayerList(view, onSelect)
}

@Composable
private fun PlayerList(view: PlayerTournamentView, onSelect: (Int) -> Unit) {
    var query by rememberPersistentText("players-search", "")
    val rows = remember(view) { view.playerRows() }
    val shown = rows.filter { it.matches(query.orEmpty()) }
    LazyColumn(
        state = rememberPersistentLazyListState("players"),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query.orEmpty(),
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag(PLAYER_SEARCH_TAG),
                singleLine = true,
                // The title is short. The hint, which is longer, shows while the person types.
                label = { Text(stringResource(Res.string.filter_search_label)) },
                placeholder = { Text(stringResource(Res.string.players_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNullOrEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.filter_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(Res.string.players_search_empty),
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        items(shown, key = { it.slotId }) { row -> PlayerListCard(row) { onSelect(row.slotId) } }
    }
}

@Composable
internal fun PlayerListCard(row: PlayerListRow, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (row.mine) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerFlag(row.country)
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    fontWeight = if (row.mine) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.team?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            row.position?.let {
                Text(stringResource(Res.string.player_position, it), style = MaterialTheme.typography.labelLarge.inTextFont(), color = colors.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/** The tabs of the screen of a player. The values are fixed ids, used in [TournamentPlayerRoute]. */
internal object PlayerTab {
    const val TABLES = 0
    const val STATISTICS = 1
}

/**
 * The screen of one player of a tournament, over the tournament screen: the tables and the statistics of the player,
 * in two tabs. Back goes to the screen that opened it, with its tab and its scroll as they were.
 */
@Composable
internal fun TournamentPlayerScreen(navController: NavHostController, route: TournamentPlayerRoute) {
    val (view, message) = rememberTournamentView(route.tournamentId)
    val slotId = route.slotId
    var tab by rememberPersistentInt("playerTab", route.tab)
    // The statistics tell this screen when they load, so the progress bar stays until the content shows.
    var statisticsLoading by remember { mutableStateOf(false) }
    // The tab row hides when the content scrolls down and shows when it scrolls up.
    val hideOnScroll = rememberHideOnScrollState()
    val account = rememberAccountActions(navController)
    account.Dialogs()
    AppScaffold(
        rootLeadingActions = { account.Buttons() },
        title = view?.takeIf { slotId in it.players }?.nameOf(slotId) ?: stringResource(Res.string.scope_player),
        subtitle = view?.tournament?.name,
        isLoading = (view == null && message == null) || (tab == PlayerTab.STATISTICS && statisticsLoading),
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        CompositionLocalProvider(LocalHideOnScroll provides hideOnScroll) {
            ScreenColumn(maxWidth = Dp.Infinity, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (view == null) {
                    MessageDialog(message)
                    return@ScreenColumn
                }
                HideOnScrollTabs(tab) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MtsTabRow(selectedTabIndex = tab, modifier = Modifier.widthIn(max = TournamentColumnWidth)) {
                            listOf(stringResource(Res.string.tab_tables), stringResource(Res.string.tab_stats)).forEachIndexed { index, title ->
                                CompactTab(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    title = title,
                                    trackingName = if (index == PlayerTab.TABLES) "PlayerTables" else "PlayerStatistics",
                                    icon = {
                                        val icon = if (index == PlayerTab.TABLES) Icons.Default.TableRestaurant else StatisticsIcon
                                        Icon(icon, contentDescription = null, Modifier.size(20.dp))
                                    },
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).align(Alignment.CenterHorizontally).hideOnScroll(hideOnScroll)
                        .widthIn(max = TournamentColumnWidth).fillMaxWidth(),
                ) {
                    MessageDialog(message)
                    if (slotId !in view.players) {
                        // A saved screen of a player that the tournament no longer has.
                        Box(Modifier.padding(16.dp)) {
                            AppEmptyState(Icons.Default.People, stringResource(Res.string.tables_empty_title), stringResource(Res.string.tables_player_empty_message))
                        }
                        return@Column
                    }
                    PlayerContent(view, slotId, tab, { statisticsLoading = it }) { table ->
                        navController.navigate(TableRoute(route.tournamentId, table.roundId, table.tableId))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlayerContent(
    view: PlayerTournamentView,
    slotId: Int,
    tab: Int,
    onStatisticsLoading: (Boolean) -> Unit,
    onOpenTable: (PlayerTable) -> Unit,
) {
    val ranking = view.rankings.players.firstOrNull { it.playerId == slotId }
    val tables = view.tables.filter { slotId in it.playerSlots() }.sortedBy { it.roundId }
    if (tab == PlayerTab.STATISTICS) {
        val places = remember(view, slotId) { view.rankingsByRound().placesOfPlayer(slotId) }
        PlayerSlotStatisticsTab(view.tournament.id, slotId, ranking = ranking, places = places, playerCount = view.players.size, onLoadingChange = onStatisticsLoading)
    } else if (tables.isEmpty()) {
        Box(Modifier.padding(16.dp)) {
            AppEmptyState(Icons.Default.People, stringResource(Res.string.tables_empty_title), stringResource(Res.string.tables_player_empty_message))
        }
    } else {
        // Each player has its own scroll position.
        val state = rememberPersistentLazyListState("player-tables-$slotId")
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                    TableSummaryCard(view, table, highlightSlot = slotId, showRound = true) { onOpenTable(table) }
                }
            }
            TableScrollbar(state, tables, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** The tabs of the tournament screen. The values are fixed ids, saved through a page refresh. [visible] gives the order. */
internal object MainTab {
    const val TABLES = 0
    const val RANKING = 1
    const val PLAYERS = 2
    const val STATISTICS = 3
    const val INFO = 4
    const val TEAMS = 5

    /** The tabs in their order. Teams shows only in a tournament with teams, after Players. */
    fun visible(isTeams: Boolean): List<Int> = if (isTeams) {
        listOf(TABLES, RANKING, PLAYERS, TEAMS, STATISTICS, INFO)
    } else {
        listOf(TABLES, RANKING, PLAYERS, STATISTICS, INFO)
    }

    fun title(id: Int): StringResource = when (id) {
        TABLES -> Res.string.tab_tables
        RANKING -> Res.string.tab_ranking
        PLAYERS -> Res.string.scope_players
        TEAMS -> Res.string.ranking_teams
        STATISTICS -> Res.string.stats_title
        else -> Res.string.tab_info
    }

    /** A fixed name for the analytics. It does not change with the language. */
    fun trackingName(id: Int): String = when (id) {
        TABLES -> "Tables"
        RANKING -> "Ranking"
        PLAYERS -> "Players"
        TEAMS -> "Teams"
        STATISTICS -> "Statistics"
        else -> "Info"
    }
}
