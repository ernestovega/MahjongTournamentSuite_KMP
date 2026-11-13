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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentText
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import com.etologic.mahjongtournamentsuite.presentation.util.normalizeSearchText
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.filter_clear
import mahjongtournamentsuite.playerapp.generated.resources.filter_search_label
import mahjongtournamentsuite.playerapp.generated.resources.player_position
import mahjongtournamentsuite.playerapp.generated.resources.scope_players
import mahjongtournamentsuite.playerapp.generated.resources.tab_stats
import mahjongtournamentsuite.playerapp.generated.resources.tab_tables
import mahjongtournamentsuite.playerapp.generated.resources.tables_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.tables_team_empty
import mahjongtournamentsuite.playerapp.generated.resources.team_n
import mahjongtournamentsuite.playerapp.generated.resources.teams_search_empty
import mahjongtournamentsuite.playerapp.generated.resources.teams_search_hint
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedIconButton as IconButton

/** The test tag of the search field of the team list. */
const val TEAM_SEARCH_TAG = "team-search"

/** The name of a team, or null when the tournament has no team with that id. */
internal fun PlayerTournamentView.teamNameOf(teamId: Int?): String? = teamId?.let { teamNames[it] }

/** The players of a team, by slot. */
internal fun PlayerTournamentView.membersOf(teamId: Int): List<Int> = players.values.filter { it.team == teamId }.map { it.slotId }.sorted()

/** The tables where at least one player of the team plays, by round and table. */
internal fun PlayerTournamentView.tablesOfTeam(teamId: Int): List<PlayerTable> {
    val members = membersOf(teamId).toSet()
    return tables.filter { table -> table.playerSlots().any { it in members } }.sortedWith(compareBy({ it.roundId }, { it.tableId }))
}

/** One line of the team list of a tournament. */
internal data class TeamListRow(
    val teamId: Int,
    val name: String?,
    /** The names of the players of the team. The search also looks at them. */
    val members: List<String>,
    val position: Int?,
    val points: Double?,
    val mine: Boolean,
)

/** The teams of the tournament in the order of the team ranking. Teams without a place go last, by name. */
internal fun PlayerTournamentView.teamRows(): List<TeamListRow> {
    val ranking = rankings.teams.associateBy { it.teamId }
    val myTeam = mySlotId?.let { players[it]?.team }
    val ids = (teamNames.keys + players.values.map { it.team }).filter { it > 0 }.distinct()
    return ids
        .map { id ->
            TeamListRow(
                teamId = id,
                name = teamNames[id],
                members = membersOf(id).map(::nameOf),
                position = ranking[id]?.position,
                points = ranking[id]?.points,
                mine = id == myTeam,
            )
        }
        .sortedWith(compareBy<TeamListRow> { it.position ?: Int.MAX_VALUE }.thenBy { it.name.orEmpty().lowercase() }.thenBy { it.teamId })
}

/** True when the line has every word of [query] in the name of the team or of one of its players. */
internal fun TeamListRow.matches(query: String): Boolean {
    val words = query.split(' ', '\t').map(::normalizeSearchText).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = (listOfNotNull(name) + members).joinToString(" ") { normalizeSearchText(it) }
    return words.all { it in text }
}

/**
 * The Teams tab of a tournament with teams. Without a chosen team it lists the teams, with a search on top.
 * With [selectedTeam] it shows that team in three tabs: its players, its tables and its statistics.
 */
@Composable
internal fun ColumnScope.TeamsTab(
    view: PlayerTournamentView,
    selectedTeam: Int?,
    /** 0 shows the players of the chosen team, 1 the tables and 2 the statistics. */
    teamTab: Int,
    onTeamTabChange: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onOpenPlayer: (Int) -> Unit,
    onOpenTable: (PlayerTable) -> Unit,
    onStatisticsLoading: (Boolean) -> Unit,
) {
    // A restored team that the tournament no longer has shows the list.
    if (selectedTeam != null && view.membersOf(selectedTeam).isNotEmpty()) {
        TeamDetail(view, selectedTeam, teamTab, onTeamTabChange, onOpenPlayer, onOpenTable, onStatisticsLoading)
    } else {
        TeamList(view, onSelect)
    }
}

@Composable
private fun TeamList(view: PlayerTournamentView, onSelect: (Int) -> Unit) {
    var query by rememberPersistentText("teams-search", "")
    val rows = remember(view) { view.teamRows() }
    val shown = rows.filter { it.matches(query.orEmpty()) }
    LazyColumn(
        state = rememberPersistentLazyListState("teams"),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query.orEmpty(),
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag(TEAM_SEARCH_TAG),
                singleLine = true,
                label = { Text(stringResource(Res.string.filter_search_label)) },
                placeholder = { Text(stringResource(Res.string.teams_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    stringResource(Res.string.teams_search_empty),
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(shown, key = { it.teamId }) { row -> TeamListCard(row) { onSelect(row.teamId) } }
    }
}

@Composable
private fun TeamListCard(row: TeamListRow, onClick: () -> Unit) {
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
            Icon(Icons.Default.Groups, contentDescription = null, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(
                    row.name ?: stringResource(Res.string.team_n, row.teamId),
                    fontWeight = if (row.mine) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.members.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            row.position?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(Res.string.player_position, it), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    row.points?.let { points -> Text(formatPoints(points), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

/** One team inside the Teams tab: its players, its tables and its statistics. */
@Composable
private fun ColumnScope.TeamDetail(
    view: PlayerTournamentView,
    teamId: Int,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenPlayer: (Int) -> Unit,
    onOpenTable: (PlayerTable) -> Unit,
    onStatisticsLoading: (Boolean) -> Unit,
) {
    val members = view.membersOf(teamId).toSet()
    HideOnScrollTabs(tab to teamId) {
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = tabRowColor(),
            contentColor = TabContentColor,
            indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = TabContentColor) },
        ) {
            listOf(
                stringResource(Res.string.scope_players),
                stringResource(Res.string.tab_tables),
                stringResource(Res.string.tab_stats),
            ).forEachIndexed { index, title ->
                CompactTab(
                    selected = tab == index,
                    onClick = { onTabChange(index) },
                    title = title,
                    trackingName = listOf("TeamPlayers", "TeamTables", "TeamStatistics")[index],
                )
            }
        }
    }
    when (tab) {
        1 -> {
            val tables = view.tablesOfTeam(teamId)
            if (tables.isEmpty()) {
                Box(Modifier.padding(16.dp)) {
                    AppEmptyState(Icons.Default.Groups, stringResource(Res.string.tables_empty_title), stringResource(Res.string.tables_team_empty))
                }
            } else {
                val state = rememberPersistentLazyListState("team-tables-$teamId")
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(tables, key = { "${it.roundId}_${it.tableId}" }) { table ->
                            // The card marks the first player of the team at the table.
                            TableSummaryCard(view, table, highlightSlot = table.playerSlots().firstOrNull { it in members }) { onOpenTable(table) }
                        }
                    }
                    TableScrollbar(state, tables, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
        2 -> TeamStatisticsTab(
            tournamentId = view.tournament.id,
            members = view.membersOf(teamId),
            ranking = view.rankings.teams.firstOrNull { it.teamId == teamId },
            onOpenPlayer = onOpenPlayer,
            onLoadingChange = onStatisticsLoading,
        )
        else -> {
            // The players of the team, in the order of the ranking of the players.
            val positions = view.rankings.players.associate { it.playerId to it.position }
            val rows = view.playerRows().filter { it.slotId in members }.sortedBy { positions[it.slotId] ?: Int.MAX_VALUE }
            LazyColumn(
                state = rememberPersistentLazyListState("team-players-$teamId"),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(rows, key = { it.slotId }) { row -> PlayerListCard(row.copy(team = null)) { onOpenPlayer(row.slotId) } }
            }
        }
    }
}
