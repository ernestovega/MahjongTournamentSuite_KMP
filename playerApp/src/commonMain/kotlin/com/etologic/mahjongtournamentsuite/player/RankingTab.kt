package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.presentation.components.BestHandIcon
import com.etologic.mahjongtournamentsuite.presentation.components.ChickenIcon
import com.etologic.mahjongtournamentsuite.presentation.components.CompactTab
import com.etologic.mahjongtournamentsuite.presentation.components.HideOnScrollTabs
import com.etologic.mahjongtournamentsuite.presentation.components.MtsScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.MtsTabRow
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerFlag
import com.etologic.mahjongtournamentsuite.presentation.components.PlayerPhoto
import com.etologic.mahjongtournamentsuite.presentation.components.PositionTrophy
import com.etologic.mahjongtournamentsuite.presentation.components.SelectField
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.components.positionColor
import com.etologic.mahjongtournamentsuite.presentation.components.ShowTabsAtTop
import com.etologic.mahjongtournamentsuite.presentation.components.rememberPersistentLazyListState
import com.etologic.mahjongtournamentsuite.presentation.components.scrollIndicators
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.field_search
import mahjongtournamentsuite.playerapp.generated.resources.icon_chicken_mono
import mahjongtournamentsuite.playerapp.generated.resources.jump_to_me
import mahjongtournamentsuite.playerapp.generated.resources.ranking_best_hands
import mahjongtournamentsuite.playerapp.generated.resources.ranking_best_short
import mahjongtournamentsuite.playerapp.generated.resources.ranking_chicken_hands
import mahjongtournamentsuite.playerapp.generated.resources.ranking_chicken_short
import mahjongtournamentsuite.playerapp.generated.resources.ranking_empty
import mahjongtournamentsuite.playerapp.generated.resources.ranking_empty_title
import mahjongtournamentsuite.playerapp.generated.resources.ranking_frozen_notice
import mahjongtournamentsuite.playerapp.generated.resources.ranking_hidden_message
import mahjongtournamentsuite.playerapp.generated.resources.ranking_hidden_title
import mahjongtournamentsuite.playerapp.generated.resources.ranking_points_score
import mahjongtournamentsuite.playerapp.generated.resources.ranking_teams
import mahjongtournamentsuite.playerapp.generated.resources.ranking_title_after_round
import mahjongtournamentsuite.playerapp.generated.resources.ranking_title_now
import mahjongtournamentsuite.playerapp.generated.resources.scope_me
import mahjongtournamentsuite.playerapp.generated.resources.scope_players
import mahjongtournamentsuite.playerapp.generated.resources.team_hide_players
import mahjongtournamentsuite.playerapp.generated.resources.team_n
import mahjongtournamentsuite.playerapp.generated.resources.team_show_players
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** [short] is the label of the tab of the ranking. It is short because the tabs share one row. */
private enum class RankingKind(val title: StringResource, val short: StringResource) {
    PLAYERS(Res.string.scope_players, Res.string.scope_players),
    TEAMS(Res.string.ranking_teams, Res.string.ranking_teams),
    CHICKEN(Res.string.ranking_chicken_hands, Res.string.ranking_chicken_short),
    BEST(Res.string.ranking_best_hands, Res.string.ranking_best_short),
}

/** One line of a ranking list. The chip is the main number, outlined. The score is the plain number next to it. */
private data class RankingRow(
    val key: String,
    val position: Int,
    val title: String,
    val subtitle: String?,
    /** The country code of the player. The line shows its flag next to the name. */
    val country: String? = null,
    /** The photo of the player. A team line has none and shows no photo. */
    val photoUrl: String? = null,
    val chip: String,
    val chipChicken: Boolean = false,
    /** A row of the best hands: the first three show a gold, silver or bronze hand instead of a trophy. */
    val bestHand: Boolean = false,
    val score: String? = null,
    val mine: Boolean = false,
    val playerId: Int? = null,
    /** The players of a team, by score. Not empty only for a team. The row can be folded. */
    val members: List<RankingRow> = emptyList(),
    /** The player is excluded from the EMA ranking. The name shows with strikethrough. */
    val excluded: Boolean = false,
)

/** The ranking tab. A second row of tabs shows the rankings that the tournament has. */
@Composable
internal fun RankingTab(view: PlayerTournamentView, onPlayerClick: (Int) -> Unit) {
    // The ranking of now, or the one at the end of a past round, like it was then.
    val byRound = remember(view) { view.rankingsByRound() }
    val playedRounds = remember(byRound) { byRound.keys.sorted() }
    var afterRound by rememberSaveable { mutableStateOf<Int?>(null) }
    // While the results are hidden, the ranking stays as it was after the last visible round, and says so.
    val hiddenFrom = view.tournament.resultsHiddenFromRound
    val round = afterRound?.takeIf { it in byRound } ?: hiddenFrom?.let { it - 1 }?.takeIf { it in byRound }
    val rankings = round?.let(byRound::getValue) ?: view.rankings
    var sharing by remember { mutableStateOf(false) }
    // Before the first hand every player has 0 points: the tab says that the ranking comes later, without the rows.
    val started = view.rankings.hasResults
    val kinds = buildList {
        add(RankingKind.PLAYERS)
        if (!started) return@buildList
        if (view.tournament.isTeams && rankings.teams.isNotEmpty()) add(RankingKind.TEAMS)
        if (view.tournament.countChickenHands && rankings.chickenHands.isNotEmpty()) add(RankingKind.CHICKEN)
        if (view.tournament.countBestHands && rankings.bestHands.isNotEmpty()) add(RankingKind.BEST)
    }
    var chosen by remember { mutableStateOf(RankingKind.PLAYERS) }
    val kind = chosen.takeIf { it in kinds } ?: RankingKind.PLAYERS
    val myTeam = rankings.players.firstOrNull { it.playerId == view.mySlotId }?.teamId

    val allRows = when (kind) {
        RankingKind.PLAYERS -> rankings.players.map { row ->
            RankingRow(
                key = "p${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = row.substitution?.label(),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                photoUrl = view.players[row.playerId]?.photoUrl,
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
                excluded = row.substitution?.struckThrough == true,
            )
        }
        RankingKind.TEAMS -> rankings.teams.map { row ->
            val members = rankings.players
                .filter { it.teamId == row.teamId }
                .sortedWith(compareByDescending<PlayerRanking> { it.score }.thenByDescending { it.points })
                .map { member ->
                    RankingRow(
                        key = "t${row.teamId}_p${member.playerId}",
                        position = member.position,
                        title = view.nameOf(member.playerId),
                        subtitle = member.substitution?.label(),
                        country = view.players[member.playerId]?.country?.takeIf { it.isNotBlank() },
                        photoUrl = view.players[member.playerId]?.photoUrl,
                        chip = formatPoints(member.points),
                        score = "${member.score}",
                        mine = member.playerId == view.mySlotId,
                        playerId = member.playerId,
                        excluded = member.substitution?.struckThrough == true,
                    )
                }
            RankingRow(
                key = "t${row.teamId}",
                position = row.position,
                title = view.teamNames[row.teamId] ?: stringResource(Res.string.team_n, row.teamId),
                subtitle = null,
                chip = formatPoints(row.points),
                score = "${row.score}",
                mine = row.teamId == myTeam,
                members = members,
            )
        }
        RankingKind.CHICKEN -> rankings.chickenHands.map { row ->
            RankingRow(
                key = "c${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = stringResource(Res.string.ranking_points_score, formatPoints(row.points), row.score),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                photoUrl = view.players[row.playerId]?.photoUrl,
                chip = "${row.chickenHands}",
                chipChicken = true,
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
        RankingKind.BEST -> rankings.bestHands.mapIndexed { index, row ->
            RankingRow(
                key = "b${index}_${row.playerId}",
                position = row.position,
                title = view.nameOf(row.playerId),
                subtitle = stringResource(Res.string.ranking_points_score, formatPoints(row.points), row.score),
                country = view.players[row.playerId]?.country?.takeIf { it.isNotBlank() },
                photoUrl = view.players[row.playerId]?.photoUrl,
                chip = "${row.handScore}",
                bestHand = true,
                mine = row.playerId == view.mySlotId,
                playerId = row.playerId,
            )
        }
    }
    val rows = if (started) allRows else emptyList()

    if (sharing) {
        val which = if (round == null) stringResource(Res.string.ranking_title_now) else stringResource(Res.string.ranking_title_after_round, round)
        val subtitle = if (kind == RankingKind.PLAYERS) which else "$which · ${stringResource(kind.title)}"
        RankingShareDialog(
            tournament = view.tournament.name,
            subtitle = subtitle,
            lines = rows.map { SharedRankingLine(it.position, it.title, it.country.orEmpty().takeIf { _ -> it.playerId != null }, it.chip, it.score, it.mine, it.excluded) },
            pastRound = round != null,
            onDismiss = { sharing = false },
        )
    }

    RankingRows(
        rows,
        emptyTitle = if (hiddenFrom != null) stringResource(Res.string.ranking_hidden_title) else stringResource(Res.string.ranking_empty_title),
        emptyText = if (hiddenFrom != null) stringResource(Res.string.ranking_hidden_message) else stringResource(Res.string.ranking_empty),
        emptyIcon = if (hiddenFrom != null) Icons.Default.VisibilityOff else Icons.Default.Leaderboard,
        onPlayerClick = onPlayerClick,
        // The choice of the round shows once a round has a ranking. Share needs lines to share.
        roundButton = if (playedRounds.isEmpty()) null else ({ RankingRoundButton(playedRounds, round, { afterRound = it }) }),
        onShare = { sharing = true },
        tabs = {
            if (kinds.size > 1) {
                val selectedIndex = kinds.indexOf(kind)
                HideOnScrollTabs(kinds.size) {
                    MtsTabRow(
                        selectedTabIndex = selectedIndex,
                    ) {
                        kinds.forEach { item ->
                            CompactTab(
                                selected = item == kind,
                                onClick = { chosen = item },
                                title = stringResource(item.short),
                                trackingName = item.name,
                                icon = {
                                    when (item) {
                                        RankingKind.PLAYERS -> Icon(Icons.Default.People, contentDescription = null, Modifier.size(20.dp))
                                        RankingKind.TEAMS -> Icon(Icons.Default.Groups, contentDescription = null, Modifier.size(20.dp))
                                        // The one-color chicken, in the color of the other tab icons, so it does not stand out.
                                        RankingKind.CHICKEN -> Icon(painterResource(Res.drawable.icon_chicken_mono), contentDescription = null, Modifier.size(20.dp))
                                        RankingKind.BEST -> BestHandIcon(size = 20.dp, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // Under the tabs (Ernesto, 2026-11-27): it is about the list below them.
            if (hiddenFrom != null && rows.isNotEmpty()) RankingFrozenNotice(hiddenFrom - 1)
        },
    )
}

/** The ranking stays as it was after [round]: the tournament hides the later results until the prize giving. */
@Composable
private fun RankingFrozenNotice(round: Int) {
    // Blue, the color of neutral information (Ernesto, 2026-11-27).
    Surface(
        color = MtsTheme.colors.info,
        contentColor = MtsTheme.colors.onInfo,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp).testTag("ranking_frozen_notice"),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(Res.string.ranking_frozen_notice, round), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The tabs of the rankings, then the list. The button that scrolls to the own line and the search are the first item of the list. */
@Composable
private fun RankingRows(
    rows: List<RankingRow>,
    emptyTitle: String,
    emptyText: String,
    emptyIcon: ImageVector,
    onPlayerClick: (Int) -> Unit,
    tabs: @Composable () -> Unit,
    /** The button that chooses the ranking of now or of a past round. Null before any round has a ranking. */
    roundButton: (@Composable () -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    if (rows.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs()
            EmptyState(emptyIcon, emptyTitle, emptyText)
        }
        return
    }
    val visible = rows
    val options = remember(rows) { rows.map { it.key to it.title } }
    val listState = rememberPersistentLazyListState("ranking")
    ShowTabsAtTop(listState)
    val scope = rememberCoroutineScope()
    val myIndex = visible.indexOfFirst { it.mine }
    Column {
        tabs()
        // On a phone the lines are narrow: less space around the position and the points, so the names keep more width.
        BoxWithConstraints {
            val narrow = maxWidth < 600.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.scrollIndicators(listState, scrollbar = MtsScrollbar.Overlay),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp + LocalFabClearance.current),
            ) {
                item(key = "controls") {
                    // One row that scrolls with the list: "Jump to me", then the search.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val jumpToMe = stringResource(Res.string.jump_to_me)
                        if (myIndex >= 0) {
                            Column(
                                Modifier.width(44.dp).height(44.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { scope.launch { listState.animateScrollToItem(myIndex + 1) } }
                                    .semantics { contentDescription = jumpToMe },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(20.dp), tint = MtsTheme.colors.link)
                                Text(stringResource(Res.string.scope_me), style = MaterialTheme.typography.labelSmall, color = MtsTheme.colors.link, maxLines = 1)
                            }
                        }
                        // The dropdown does not filter the list. Choosing a line scrolls the list to it.
                        SelectField<String>(
                            label = stringResource(Res.string.field_search),
                            options = options,
                            selected = null,
                            onSelect = { key ->
                                val index = visible.indexOfFirst { it.key == key }
                                if (index >= 0) scope.launch { listState.animateScrollToItem(index + 1) }
                            },
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                        roundButton?.invoke()
                        onShare?.let { RankingShareButton(showLabel = !narrow, onClick = it) }
                    }
                }
                items(visible, key = { it.key }) { row ->
                    RankingCard(row, onPlayerClick, narrow)
                }
            }
        }
    }
}

/** One ranking line. A team line folds out to show its players. */
@Composable
private fun RankingCard(row: RankingRow, onPlayerClick: (Int) -> Unit, narrow: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val foldable = row.members.isNotEmpty()
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val playerId = row.playerId
    val onClick: (() -> Unit)? = when {
        foldable -> ({ expanded = !expanded })
        playerId != null -> ({ onPlayerClick(playerId) })
        else -> null
    }
    Card(
        // The ripple and the hover follow the round corners of the card.
        if (onClick != null) Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick) else Modifier.fillMaxWidth(),
        border = if (row.mine) BorderStroke(2.dp, colors.primary) else null,
        colors = CardDefaults.cardColors(
            // Gold, silver and bronze for the first three places, softened so the text stays readable.
            containerColor = if (row.position in 1..3) {
                // The first place uses a bright, light yellow so it is easy to tell from bronze.
                val medal = if (row.position == 1) MtsTheme.colors.firstPlaceHighlight else positionColor(row.position)
                medal.copy(alpha = if (row.position == 1) 0.55f else 0.35f).compositeOver(colors.surfaceContainerHigh)
            } else {
                colors.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            RankingLine(row, trailing = if (foldable) expanded else null, narrow = narrow)
            if (foldable && expanded) {
                row.members.forEach { member ->
                    val memberId = member.playerId
                    Box(
                        if (memberId != null) Modifier.fillMaxWidth().clickable { onPlayerClick(memberId) } else Modifier.fillMaxWidth(),
                    ) { RankingLine(member, compact = true, narrow = narrow) }
                }
            }
        }
    }
}

/** The photo of a player in a ranking line: as tall as the name and the score chip. */
private val RankingPhotoSize = 36.dp

/** The content of a ranking line. [trailing] is null for a line that cannot be folded, else it is the folded state. */
@Composable
private fun RankingLine(row: RankingRow, compact: Boolean = false, trailing: Boolean? = null, narrow: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // A phone gets less space around the position and the points.
    val gap = if (narrow) 6.dp else 12.dp
    val edge = if (narrow) 8.dp else 12.dp
    Row(
        Modifier.fillMaxWidth().padding(start = if (compact) 24.dp else edge, top = 12.dp, end = edge, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (!compact) {
            Box(Modifier.width(if (narrow) 30.dp else 36.dp), contentAlignment = Alignment.Center) {
                if (row.position <= 3) {
                    // A circle behind the trophy or the hand keeps it visible on the colored card.
                    Box(
                        Modifier.size(if (narrow) 30.dp else 34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (row.bestHand) BestHandIcon(row.position, size = 24.dp) else PositionTrophy(row.position, size = 24.dp)
                    }
                } else {
                    Text("${row.position}", fontWeight = FontWeight.Bold)
                }
            }
        }
        // The photo comes after the position, then the flag with a fixed width, so the names are aligned.
        // A team has no photo and no flag.
        if (row.playerId != null) {
            PlayerPhoto(row.photoUrl, row.title, size = RankingPhotoSize)
            PlayerFlag(row.country)
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                // The names of the players are always bold.
                fontWeight = FontWeight.Bold,
                textDecoration = if (row.excluded) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Surface(
            shape = MtsTheme.shapes.pill,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, colors.onSurface),
        ) {
            Row(
                Modifier.padding(horizontal = if (narrow) MtsTheme.spacing.sm else MtsTheme.spacing.md, vertical = MtsTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            ) {
                if (row.chipChicken) ChickenIcon()
                Text(
                    row.chip,
                    style = MaterialTheme.typography.titleLarge.inTextFont(),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        }
        row.score?.let {
            Text(
                it,
                Modifier.widthIn(min = if (narrow) 44.dp else 56.dp),
                style = MaterialTheme.typography.titleLarge.inTextFont(),
                color = colors.onSurface,
                textAlign = TextAlign.End,
            )
        }
        if (trailing != null) {
            Icon(
                if (trailing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (trailing) Res.string.team_hide_players else Res.string.team_show_players),
                Modifier.size(24.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
