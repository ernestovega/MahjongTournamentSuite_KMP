package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundTotals
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.domain.model.forRound
import com.etologic.mahjongtournamentsuite.domain.model.playedRounds
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames
import kotlin.math.ceil
import kotlin.math.roundToInt
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.hands_drawn
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_discard
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.leader_best_hand_value
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in
import mahjongtournamentsuite.ui.generated.resources.leader_most_self_draws
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_unit
import mahjongtournamentsuite.ui.generated.resources.open_player_statistics
import mahjongtournamentsuite.ui.generated.resources.round_short
import mahjongtournamentsuite.ui.generated.resources.rounds_hands_of_round
import mahjongtournamentsuite.ui.generated.resources.rounds_title
import mahjongtournamentsuite.ui.generated.resources.stat_average_hand_time
import mahjongtournamentsuite.ui.generated.resources.stat_average_hand_time_info
import mahjongtournamentsuite.ui.generated.resources.stat_average_hands_per_table
import mahjongtournamentsuite.ui.generated.resources.stat_average_winning_hand
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand_points
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_hands_without_dealin
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_on_table
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_highest_hand_score
import mahjongtournamentsuite.ui.generated.resources.stat_highest_table_score
import mahjongtournamentsuite.ui.generated.resources.stat_penalties
import mahjongtournamentsuite.ui.generated.resources.stat_penalties_info
import mahjongtournamentsuite.ui.generated.resources.stat_players
import mahjongtournamentsuite.ui.generated.resources.stat_tables
import mahjongtournamentsuite.ui.generated.resources.stat_tables_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_tournaments
import mahjongtournamentsuite.ui.generated.resources.stats_hand_stats_missing
import mahjongtournamentsuite.ui.generated.resources.stats_hand_values_use
import mahjongtournamentsuite.ui.generated.resources.stats_leaders
import mahjongtournamentsuite.ui.generated.resources.stats_records
import mahjongtournamentsuite.ui.generated.resources.stats_totals
import mahjongtournamentsuite.ui.generated.resources.tables_completed_of
import org.jetbrains.compose.resources.stringResource

/**
 * The statistics of one tournament, or of all tournaments, in the same cards as the player statistics.
 * The cards flow into several columns on a wide screen.
 * [tournamentCount] and [playerCount] are set for the statistics of all tournaments.
 *
 * With [roundIds], one row of chips chooses between the whole tournament and one round, and every card shows that round.
 * The panel shows the chips at its top, unless the screen shows them itself with [TournamentRoundChips]:
 * then it passes the chosen round in [selectedRound] and [onSelectRound].
 */
@Composable
fun TournamentStatisticsPanel(
    statistics: TournamentStatistics,
    names: Map<Int, String>,
    modifier: Modifier = Modifier,
    tournamentCount: Int? = null,
    /** The number of players, when [statistics] holds only some of them. */
    playerCount: Int? = null,
    onOpenPlayer: ((Int) -> Unit)? = null,
    /** The rounds of the tournament. When set, every card can show one round. */
    roundIds: List<Int>? = null,
    /** The round that the screen chose with [TournamentRoundChips], or null for the whole tournament. */
    selectedRound: Int? = null,
    /** Set when the screen shows [TournamentRoundChips] itself. */
    onSelectRound: ((Int?) -> Unit)? = null,
) {
    @Suppress("NAME_SHADOWING")
    val names = remember(names, PlaceholderNames.playerTemplate) { PlaceholderNames.localizeAll(names) }
    var ownRound by remember(statistics) { mutableStateOf<Int?>(null) }
    val round = (if (onSelectRound != null) selectedRound else ownRound)?.takeIf { roundIds != null }
    val shown = remember(statistics, round) { round?.let { statistics.forRound(it) } ?: statistics }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (roundIds != null && onSelectRound == null) {
            TournamentRoundChips(statistics, roundIds, ownRound, { ownRound = it }, edge = 0.dp)
        }
        StaggeredColumns(minColumnWidth = 480.dp) {
            // One tournament has no Totals card: the Hands card shows its hands. All tournaments keep it.
            if (tournamentCount != null) TournamentTotalsCard(shown, tournamentCount, playerCount)
            TournamentHandsCard(
                shown,
                showPenalties = statistics.penaltyCount > 0,
                showHandTime = statistics.averageHandTimeMs != null,
                // The trend by round shows only with the whole tournament, when three rounds or more have hands.
                trend = if (round == null && roundIds != null) remember(statistics, roundIds) { handsTrend(statistics, roundIds) } else null,
            )
            // Top players goes second, right after Hands, and takes one column like the other cards.
            LeadersCard(shown, names, onOpenPlayer)
            TournamentRecordsCard(shown, playersAreSums = playerCount != null, oneRound = round != null)
        }
        if (shown.tablesWithHands < shown.tablesCompleted) {
            Text(
                if (shown.tablesWithHands == 0) {
                    stringResource(Res.string.stats_hand_stats_missing)
                } else {
                    stringResource(Res.string.stats_hand_values_use, shown.tablesWithHands, shown.tablesCompleted)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The chips that choose the whole tournament or one round of [roundIds]. A round without a completed table has no chip.
 * A tap on the chosen round goes back to the whole tournament. The chips are one row that scrolls sideways.
 * The row reaches [edge] further on each side than its place, to the edge of the screen or of a card.
 */
@Composable
fun TournamentRoundChips(
    statistics: TournamentStatistics,
    roundIds: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    edge: Dp = 0.dp,
) {
    val played = remember(statistics, roundIds) { statistics.playedRounds(roundIds) }
    if (played.isEmpty()) return
    RoundChipsRow(modifier, edge) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.stats_totals)) },
        )
        played.forEach { roundId ->
            FilterChip(
                selected = roundId == selected,
                onClick = { onSelect(if (roundId == selected) null else roundId) },
                label = { Text(stringResource(Res.string.round_short, roundId)) },
            )
        }
    }
}

/**
 * The chips of the rounds that a player played in one tournament, from [results]: Totals and one chip per round.
 * They work like [TournamentRoundChips]: a tap on the chosen round goes back to Totals.
 */
@Composable
fun PlayerRoundChips(
    results: List<TableResult>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    edge: Dp = 0.dp,
) {
    val played = remember(results) { results.map { it.roundId }.distinct().sorted() }
    if (played.isEmpty()) return
    RoundChipsRow(modifier, edge) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.stats_totals)) },
        )
        played.forEach { roundId ->
            FilterChip(
                selected = roundId == selected,
                onClick = { onSelect(if (roundId == selected) null else roundId) },
                label = { Text(stringResource(Res.string.round_short, roundId)) },
            )
        }
    }
}

@Composable
private fun TournamentTotalsCard(statistics: TournamentStatistics, tournamentCount: Int, playerCount: Int?) {
    StatCard(stringResource(Res.string.stats_totals), Modifier.fillMaxWidth()) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_tournaments), tournamentCount.toString())
            BigNumber(
                stringResource(Res.string.stat_tables),
                if (statistics.tablesCompleted == statistics.tablesTotal) {
                    statistics.tablesCompleted.toString()
                } else {
                    stringResource(Res.string.tables_completed_of, statistics.tablesCompleted, statistics.tablesTotal)
                },
            )
            BigNumber(stringResource(Res.string.stat_players), (playerCount ?: statistics.players.count { it.tablesPlayed > 0 }).toString())
            BigNumber(stringResource(Res.string.stat_hands), if (statistics.handsPlayed > 0) statistics.handsPlayed.toString() else EMPTY)
        }
    }
}

/**
 * How the hands ended: won by discard, won by self-draw or drawn, and the averages and the penalties.
 * [showPenalties] is true when the whole tournament has penalties, so the card keeps its shape in a round without them.
 * [showHandTime] is the same for the time per hand, which only hands saved with a time have.
 * [trend] adds a line chart with the share of each way to end a hand in each round.
 */
@Composable
private fun TournamentHandsCard(
    statistics: TournamentStatistics,
    showPenalties: Boolean,
    showHandTime: Boolean,
    trend: List<TournamentRoundTotals>?,
) {
    if (statistics.handsPlayed == 0) return
    val byDiscard = (statistics.handsPlayed - statistics.drawnHands - statistics.selfDraws).coerceAtLeast(0)
    val won = MaterialTheme.colorScheme.primary
    val parts = listOf(
        Triple(stringResource(Res.string.hands_won_by_discard), byDiscard, won.copy(alpha = 0.75f)),
        Triple(stringResource(Res.string.hands_won_by_self_draw), statistics.selfDraws, won.copy(alpha = 0.45f)),
        Triple(stringResource(Res.string.hands_drawn), statistics.drawnHands, MaterialTheme.colorScheme.outline),
    )
    StatCard(stringResource(Res.string.stat_hands), Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectorChart(
                parts.map { Slice(it.second, it.third) },
                null,
                200.dp,
                0.26f,
                centerValue = statistics.handsPlayed.toString(),
                centerLabel = stringResource(Res.string.outcome_hands_unit),
            )
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { (label, value, color) ->
                    val share = if (statistics.handsPlayed > 0) (value * 100.0 / statistics.handsPlayed).roundToInt() else 0
                    LegendRow(color, label, "$value ($share%)")
                }
            }
        }
        // The trend by round goes under the ring, and the averages and the penalties close the card.
        if (trend != null) {
            HorizontalDivider(Modifier.padding(vertical = MtsTheme.spacing.sm))
            Text(
                stringResource(Res.string.rounds_title),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            var selected by remember(trend) { mutableStateOf<Int?>(null) }
            val share = { part: Int, of: Int -> if (of > 0) part * 100.0 / of else null }
            val discard = trend.map { share(it.winsByDiscard, it.handsPlayed) }
            val selfDraw = trend.map { share(it.selfDraws, it.handsPlayed) }
            val drawn = trend.map { share(it.drawnHands, it.handsPlayed) }
            val top = listOf(discard, selfDraw, drawn).flatten().filterNotNull().maxOrNull() ?: 100.0
            // A round scale in tens, so the guides read as whole percentages.
            val scaleTop = (ceil(top * 1.1 / 10) * 10).coerceIn(20.0, 100.0)
            val labels = parts.map { it.first }
            val titles = trend.map { stringResource(Res.string.rounds_hands_of_round, it.roundId, it.handsPlayed) }
            RoundLineChart(
                names = trend.map { stringResource(Res.string.round_short, it.roundId) },
                series = listOf(
                    LineSeries(discard, parts[0].third),
                    LineSeries(selfDraw, parts[1].third),
                    LineSeries(drawn, parts[2].third),
                ),
                minValue = 0.0,
                maxValue = scaleTop,
                selected = selected,
                // Every move of the pointer over a round selects it again, so the selection must not toggle.
                onSelect = { selected = it },
                format = { "${it.roundToInt()}%" },
                axisValues = true,
                height = 200.dp,
                // The bubble of the chosen round: its hands, then the share of each way to end them.
                tooltip = selected?.let { index ->
                    (
                        listOf(titles[index]) +
                            listOf(discard, selfDraw, drawn).mapIndexed { line, values -> "${labels[line]}: ${values[index]?.roundToInt() ?: 0}%" }
                        ).joinToString("\n")
                },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_average_hands_per_table), statistics.averageHandsPerTable.decimal())
            BigNumber(stringResource(Res.string.stat_average_winning_hand), statistics.averageHandScore.decimal())
            if (showHandTime) {
                BigNumber(
                    stringResource(Res.string.stat_average_hand_time),
                    statistics.averageHandTimeMs?.let(::minutesAndSeconds) ?: EMPTY,
                    info = stringResource(Res.string.stat_average_hand_time_info),
                )
            }
            // The penalties show when the tournament has any, also for a round without them, so the card keeps its shape.
            if (showPenalties) {
                val points = statistics.penaltyPoints
                BigNumber(
                    stringResource(Res.string.stat_penalties),
                    if (points != null) "${statistics.penaltyCount} ($points)" else statistics.penaltyCount.toString(),
                    info = stringResource(Res.string.stat_penalties_info),
                )
            }
        }
    }
}

/**
 * The best values of the tournament: the highest hand and table score, the most hands won on a table,
 * and the longest runs of one player. No highest penalty: the organizer asked to leave it out.
 * [playersAreSums] is true when each player holds the sums of all tournaments. Then the values of one table are not known.
 */
@Composable
private fun TournamentRecordsCard(statistics: TournamentStatistics, playersAreSums: Boolean, oneRound: Boolean) {
    val players = statistics.players
    val streaks = players.map { it.streaks }
    StatCard(stringResource(Res.string.stats_records), Modifier.fillMaxWidth()) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_highest_hand_score), statistics.highestHand.text())
            statistics.highestHandGain?.let { gain ->
                BigNumber(stringResource(Res.string.stat_best_hand_points), gain.toString())
            }
            if (!playersAreSums) {
                BigNumber(stringResource(Res.string.stat_highest_table_score), players.mapNotNull { it.bestTableScore }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_hands_won_on_table), players.mapNotNull { it.mostHandsWonAtATable }.maxOrNull().text())
                // In one round each player plays one table, so a run of tables means nothing there.
                if (!oneRound) BigNumber(stringResource(Res.string.stat_tables_won_row), (streaks.maxOfOrNull { it.tableWins } ?: 0).toString())
                BigNumber(stringResource(Res.string.stat_hands_won_row), streaks.mapNotNull { it.handsWon }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_hands_without_dealin), streaks.mapNotNull { it.handsWithoutDealIn }.maxOrNull().text())
            }
        }
    }
}

/** The best player of one leaderboard and the skill that the board is about, if it is one of the skills chart. */
private class Leader(
    val title: String,
    val player: PlayerStatistics,
    val value: String,
    val calculation: String,
    /** The index of the skill in [skillsOf], or null when the board is not about a skill. */
    val skill: Int?,
)

/**
 * The leader of the best average hand value, the best win rate, the lowest deal-in rate and the most self-draws, each with the
 * skills chart of that player. The skill of the board stands out in the chart. With [roundIds], chips choose between
 * the whole tournament and one round. A tap on the chosen round goes back to the totals.
 */
@Composable
private fun LeadersCard(
    statistics: TournamentStatistics,
    names: Map<Int, String>,
    onOpenPlayer: ((Int) -> Unit)?,
) {
    val players = statistics.players
    val minimum = MIN_HANDS_FOR_LEADERBOARD
    // The same boards as the leaderboards of the tournament statistics: the first of each one.
    val eligible = players.filter { it.handsPlayed >= minimum }
    val leaders = listOfNotNull(
        eligible.filter { it.handsWon > 0 }.maxByOrNull { it.averageWinningScore ?: 0.0 }?.let {
            Leader(
                stringResource(Res.string.leader_best_hand_value),
                it,
                it.averageWinningScore.decimal(),
                "(${it.handsWon})",
                skill = 3,
            )
        },
        eligible.maxByOrNull { it.winRate ?: 0.0 }?.let {
            Leader(
                stringResource(Res.string.leader_best_win_rate),
                it,
                it.winRate.percent(),
                "(${it.handsWon}/${it.handsPlayed})",
                skill = 0,
            )
        },
        eligible.minByOrNull { it.dealInRate ?: 1.0 }?.let {
            Leader(
                stringResource(Res.string.leader_lowest_deal_in),
                it,
                it.dealInRate.percent(),
                "(${it.dealIns}/${it.handsPlayed})",
                skill = 2,
            )
        },
        players.filter { it.selfDraws > 0 }.maxByOrNull { it.selfDraws }?.let {
            Leader(
                stringResource(Res.string.leader_most_self_draws),
                it,
                it.selfDraws.toString(),
                "(${it.handsPlayed})",
                skill = 1,
            )
        },
    )
    if (leaders.isEmpty()) return
    StatCard(stringResource(Res.string.stats_leaders), Modifier.fillMaxWidth()) {
        // At most two charts side by side, so a wide card shows them two by two.
        StaggeredColumns(minColumnWidth = 280.dp, spacing = MtsTheme.spacing.md, maxColumns = 2) {
            leaders.forEach { leader -> LeaderSkills(leader, names, onOpenPlayer) }
        }
    }
}

/** One leader: the board, the name and the value of the player, and the skills chart of the player. */
@Composable
private fun LeaderSkills(leader: Leader, names: Map<Int, String>, onOpenPlayer: ((Int) -> Unit)?) {
    val playerId = leader.player.playerId
    val openLabel = stringResource(Res.string.open_player_statistics)
    // A subcard, like the small stats of Hands and Records.
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Column(Modifier.fillMaxWidth().padding(MtsTheme.spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
            Text(leader.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.then(
                    if (onOpenPlayer != null) {
                        Modifier.clickable(onClickLabel = openLabel) { onOpenPlayer(playerId) }
                    } else {
                        Modifier
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            ) {
                Text(
                    names[playerId] ?: "Player $playerId",
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge.inTextFont(),
                    fontWeight = FontWeight.Bold,
                    // A name in the color of the links shows that the player opens.
                    color = if (onOpenPlayer != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(leader.value, style = MaterialTheme.typography.bodyLarge.inTextFont(), fontWeight = FontWeight.Bold)
                Text(leader.calculation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadarChart(skillsOf(leader.player), Modifier.fillMaxWidth().height(240.dp), highlight = leader.skill)
        }
    }
}

/**
 * The chips of the totals and the rounds, in one row. The row reaches [bleed] further on each side than its place,
 * and scrolls sideways when the chips do not fit. When they fit, they sit in the middle.
 */
@Composable
private fun RoundChipsRow(modifier: Modifier, bleed: Dp, content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(
        modifier.layout { measurable, constraints ->
            val extra = (bleed * 2).roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
            layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
        }.fillMaxWidth(),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth).padding(horizontal = bleed),
            horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The rounds with hands, for the trend of the Hands card. Null with fewer than three of them: two points make no trend. */
private fun handsTrend(statistics: TournamentStatistics, roundIds: List<Int>): List<TournamentRoundTotals>? = statistics.byRound(roundIds).map { it.round }.filter { it.handsPlayed > 0 }.takeIf { it.size >= 3 }

/** A time as minutes and seconds, like 4:05. */
private fun minutesAndSeconds(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
