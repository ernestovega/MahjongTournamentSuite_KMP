package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.remember
import com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import kotlin.math.roundToInt
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.hands_drawn
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_discard
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate_info
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in_info
import mahjongtournamentsuite.ui.generated.resources.leader_most_points
import mahjongtournamentsuite.ui.generated.resources.leader_most_points_info
import mahjongtournamentsuite.ui.generated.resources.leader_most_self_draws
import mahjongtournamentsuite.ui.generated.resources.leader_most_self_draws_info
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_unit
import mahjongtournamentsuite.ui.generated.resources.stat_average_winning_hand
import mahjongtournamentsuite.ui.generated.resources.stat_draw_rate
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_hands_without_dealin
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_on_table
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_highest_hand_score
import mahjongtournamentsuite.ui.generated.resources.stat_highest_penalty
import mahjongtournamentsuite.ui.generated.resources.stat_highest_table_score
import mahjongtournamentsuite.ui.generated.resources.stat_penalties
import mahjongtournamentsuite.ui.generated.resources.stat_penalties_info
import mahjongtournamentsuite.ui.generated.resources.stat_players
import mahjongtournamentsuite.ui.generated.resources.stat_tables
import mahjongtournamentsuite.ui.generated.resources.stat_tables_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_tournaments
import mahjongtournamentsuite.ui.generated.resources.stats_hand_stats_missing
import mahjongtournamentsuite.ui.generated.resources.stats_hand_values_use
import mahjongtournamentsuite.ui.generated.resources.stats_records
import mahjongtournamentsuite.ui.generated.resources.stats_totals
import mahjongtournamentsuite.ui.generated.resources.tables_completed_of
import org.jetbrains.compose.resources.stringResource

/**
 * The statistics of one tournament, or of all tournaments, in the same cards as the player statistics.
 * The cards flow into several columns on a wide screen.
 * [tournamentCount] and [playerCount] are set for the statistics of all tournaments.
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
) {
    @Suppress("NAME_SHADOWING")
    val names = remember(names, PlaceholderNames.playerTemplate) { PlaceholderNames.localizeAll(names) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StaggeredColumns(minColumnWidth = 480.dp) {
            TournamentTotalsCard(statistics, tournamentCount, playerCount)
            TournamentHandsCard(statistics)
            TournamentRecordsCard(statistics, playersAreSums = playerCount != null)
            val minimum = MIN_HANDS_FOR_LEADERBOARD
            val points = statistics.players.filter { it.tablesPlayed > 0 }.sortedByDescending { it.totalPoints }
            LeaderboardCard(
                stringResource(Res.string.leader_most_points), points, names, { formatPoints(it.totalPoints) }, onOpenPlayer,
                stringResource(Res.string.leader_most_points_info),
                calculation = { "(${it.tablesPlayed})" },
            )
            LeaderboardCard(
                stringResource(Res.string.leader_best_win_rate), statistics.winRateLeaders, names, { it.winRate.percent() }, onOpenPlayer,
                stringResource(Res.string.leader_best_win_rate_info, minimum),
                calculation = { "(${it.handsWon}/${it.handsPlayed})" },
            )
            LeaderboardCard(
                stringResource(Res.string.leader_lowest_deal_in), statistics.lowestDealInLeaders, names, { it.dealInRate.percent() }, onOpenPlayer,
                stringResource(Res.string.leader_lowest_deal_in_info, minimum),
                calculation = { "(${it.dealIns}/${it.handsPlayed})" },
            )
            LeaderboardCard(
                stringResource(Res.string.leader_most_self_draws), statistics.selfDrawLeaders, names, { it.selfDraws.toString() }, onOpenPlayer,
                stringResource(Res.string.leader_most_self_draws_info),
                calculation = { "(${it.handsPlayed})" },
            )
        }
        if (statistics.tablesWithHands < statistics.tablesCompleted) {
            Text(
                if (statistics.tablesWithHands == 0) {
                    stringResource(Res.string.stats_hand_stats_missing)
                } else {
                    stringResource(Res.string.stats_hand_values_use, statistics.tablesWithHands, statistics.tablesCompleted)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TournamentTotalsCard(statistics: TournamentStatistics, tournamentCount: Int?, playerCount: Int?) {
    StatCard(stringResource(Res.string.stats_totals), Modifier.fillMaxWidth()) {
        StatGrid(Modifier.fillMaxWidth()) {
            if (tournamentCount != null) BigNumber(stringResource(Res.string.stat_tournaments), tournamentCount.toString())
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

/** How the hands ended: won by discard, won by self-draw or drawn. */
@Composable
private fun TournamentHandsCard(statistics: TournamentStatistics) {
    if (statistics.handsPlayed == 0) return
    val won = MaterialTheme.colorScheme.primary
    // The totals of the tournament, not the sums of the players, because the players may be only some of them.
    val selfDraws = statistics.selfDraws
    val byDiscard = (statistics.handsPlayed - statistics.drawnHands - selfDraws).coerceAtLeast(0)
    val parts = listOf(
        Triple(stringResource(Res.string.hands_won_by_discard), byDiscard, won.copy(alpha = 0.75f)),
        Triple(stringResource(Res.string.hands_won_by_self_draw), selfDraws, won.copy(alpha = 0.45f)),
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
                parts.map { Slice(it.second, it.third) }, null, 200.dp, 0.26f,
                centerValue = statistics.handsPlayed.toString(), centerLabel = stringResource(Res.string.outcome_hands_unit),
            )
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { (label, value, color) ->
                    LegendRow(
                        color, label,
                        "$value (${(value * 100.0 / statistics.handsPlayed).roundToInt()}%)",
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_draw_rate), statistics.drawRate.percent())
            BigNumber(stringResource(Res.string.stat_average_winning_hand), statistics.averageHandScore.decimal())
            if (statistics.penaltyCount > 0) {
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
 * the biggest penalty and the longest runs of one player.
 * [playersAreSums] is true when each player holds the sums of all tournaments. Then the values of one table are not known.
 */
@Composable
private fun TournamentRecordsCard(statistics: TournamentStatistics, playersAreSums: Boolean) {
    val players = statistics.players
    val streaks = players.map { it.streaks }
    StatCard(stringResource(Res.string.stats_records), Modifier.fillMaxWidth()) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_highest_hand_score), statistics.highestHand.text())
            if (!playersAreSums) {
                BigNumber(stringResource(Res.string.stat_highest_table_score), players.mapNotNull { it.bestTableScore }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_hands_won_on_table), players.mapNotNull { it.mostHandsWonAtATable }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_highest_penalty), players.mapNotNull { it.highestPenalty }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_tables_won_row), (streaks.maxOfOrNull { it.tableWins } ?: 0).toString())
                BigNumber(stringResource(Res.string.stat_hands_won_row), streaks.mapNotNull { it.handsWon }.maxOrNull().text())
                BigNumber(stringResource(Res.string.stat_hands_without_dealin), streaks.mapNotNull { it.handsWithoutDealIn }.maxOrNull().text())
            }
        }
    }
}

/** A ranked list of the best five players with one value each. */
@Composable
private fun LeaderboardCard(
    title: String,
    entries: List<PlayerStatistics>,
    names: Map<Int, String>,
    value: (PlayerStatistics) -> String,
    onOpenPlayer: ((Int) -> Unit)?,
    info: String? = null,
    /** A small gray text after the value of each player, for example the numbers that the rate comes from. */
    calculation: ((PlayerStatistics) -> String)? = null,
) {
    if (entries.isEmpty()) return
    val clickable = onOpenPlayer != null
    StatCard(title, Modifier.fillMaxWidth(), info = info) {
        entries.take(5).forEachIndexed { index, player ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        if (onOpenPlayer != null) {
                            Modifier.clickable(onClickLabel = "Open the statistics of the player") { onOpenPlayer(player.playerId) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}.", Modifier.width(36.dp), style = MaterialTheme.typography.titleLarge)
                Text(
                    names[player.playerId] ?: "Player ${player.playerId}",
                    Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    // A name in the color of the links and an arrow show that the player opens.
                    color = if (clickable) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                Text(value(player), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (calculation != null) {
                    Text(
                        calculation(player),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (clickable) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        Modifier.padding(start = 4.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
