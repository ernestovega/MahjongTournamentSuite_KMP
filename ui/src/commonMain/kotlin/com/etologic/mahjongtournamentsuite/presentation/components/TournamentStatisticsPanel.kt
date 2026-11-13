package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundTotals
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.model.byRound
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.hands_drawn
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_discard
import mahjongtournamentsuite.ui.generated.resources.hands_won_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.leader_best_hand_value
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in
import mahjongtournamentsuite.ui.generated.resources.leader_most_self_draws
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_unit
import mahjongtournamentsuite.ui.generated.resources.round_short
import mahjongtournamentsuite.ui.generated.resources.stat_average_hands_per_table
import mahjongtournamentsuite.ui.generated.resources.stat_average_winning_hand
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand_points
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
import mahjongtournamentsuite.ui.generated.resources.stats_leaders
import mahjongtournamentsuite.ui.generated.resources.stats_records
import mahjongtournamentsuite.ui.generated.resources.stats_totals
import mahjongtournamentsuite.ui.generated.resources.tables_completed_of
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
    /** The rounds of the tournament. When set, chips on the Hands and the leaders cards show one round. */
    roundIds: List<Int>? = null,
) {
    @Suppress("NAME_SHADOWING")
    val names = remember(names, PlaceholderNames.playerTemplate) { PlaceholderNames.localizeAll(names) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StaggeredColumns(minColumnWidth = 480.dp) {
            TournamentTotalsCard(statistics, tournamentCount, playerCount)
            TournamentHandsCard(statistics, roundIds?.let { ids -> remember(statistics, ids) { statistics.byRound(ids).map { it.round } } })
            TournamentRecordsCard(statistics, playersAreSums = playerCount != null)
        }
        LeadersCard(statistics, names, roundIds, onOpenPlayer)
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

/** The numbers of the Hands card: those of the whole tournament or those of one round. */
private class HandsNumbers(
    val handsPlayed: Int,
    val selfDraws: Int,
    val drawnHands: Int,
    val averageHandsPerTable: Double?,
    val averageHandScore: Double?,
    val penaltyCount: Int,
    val penaltyPoints: Int?,
) {
    val byDiscard: Int get() = (handsPlayed - drawnHands - selfDraws).coerceAtLeast(0)
}

/**
 * How the hands ended: won by discard, won by self-draw or drawn.
 * With [rounds], chips choose between the totals and one round. The card keeps its shape, only the numbers change.
 * A tap on the chosen round goes back to the totals.
 */
@Composable
private fun TournamentHandsCard(statistics: TournamentStatistics, rounds: List<TournamentRoundTotals>?) {
    if (statistics.handsPlayed == 0) return
    var chosenRound by remember(statistics) { mutableStateOf<Int?>(null) }
    val round = rounds?.firstOrNull { it.roundId == chosenRound }
    // The totals of the tournament, not the sums of the players, because the players may be only some of them.
    val numbers = if (round == null) {
        HandsNumbers(
            handsPlayed = statistics.handsPlayed,
            selfDraws = statistics.selfDraws,
            drawnHands = statistics.drawnHands,
            averageHandsPerTable = statistics.averageHandsPerTable,
            averageHandScore = statistics.averageHandScore,
            penaltyCount = statistics.penaltyCount,
            penaltyPoints = statistics.penaltyPoints,
        )
    } else {
        HandsNumbers(
            handsPlayed = round.handsPlayed,
            selfDraws = round.selfDraws,
            drawnHands = round.drawnHands,
            averageHandsPerTable = round.averageHandsPerTable,
            averageHandScore = round.averageHandScore,
            penaltyCount = round.penaltyCount,
            penaltyPoints = null,
        )
    }
    val won = MaterialTheme.colorScheme.primary
    val parts = listOf(
        Triple(stringResource(Res.string.hands_won_by_discard), numbers.byDiscard, won.copy(alpha = 0.75f)),
        Triple(stringResource(Res.string.hands_won_by_self_draw), numbers.selfDraws, won.copy(alpha = 0.45f)),
        Triple(stringResource(Res.string.hands_drawn), numbers.drawnHands, MaterialTheme.colorScheme.outline),
    )
    StatCard(stringResource(Res.string.stat_hands), Modifier.fillMaxWidth()) {
        if (!rounds.isNullOrEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = round == null,
                    onClick = { chosenRound = null },
                    label = { Text(stringResource(Res.string.stats_totals)) },
                )
                rounds.forEach { item ->
                    FilterChip(
                        selected = item.roundId == round?.roundId,
                        // A tap on the chosen round goes back to the totals.
                        onClick = { chosenRound = if (item.roundId == round?.roundId) null else item.roundId },
                        label = { Text(stringResource(Res.string.round_short, item.roundId)) },
                        enabled = item.handsPlayed > 0,
                    )
                }
            }
        }
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
                centerValue = numbers.handsPlayed.toString(),
                centerLabel = stringResource(Res.string.outcome_hands_unit),
            )
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { (label, value, color) ->
                    val share = if (numbers.handsPlayed > 0) (value * 100.0 / numbers.handsPlayed).roundToInt() else 0
                    LegendRow(color, label, "$value ($share%)")
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_average_hands_per_table), numbers.averageHandsPerTable.decimal())
            BigNumber(stringResource(Res.string.stat_average_winning_hand), numbers.averageHandScore.decimal())
            // The penalties show when the tournament has any, also for a round without them, so the card keeps its shape.
            if (statistics.penaltyCount > 0) {
                val points = numbers.penaltyPoints
                BigNumber(
                    stringResource(Res.string.stat_penalties),
                    if (points != null) "${numbers.penaltyCount} ($points)" else numbers.penaltyCount.toString(),
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
            statistics.highestHandGain?.let { gain ->
                BigNumber(stringResource(Res.string.stat_best_hand_points), gain.toString())
            }
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
    roundIds: List<Int>?,
    onOpenPlayer: ((Int) -> Unit)?,
) {
    var chosenRound by remember(statistics) { mutableStateOf<Int?>(null) }
    val players = remember(statistics, chosenRound) {
        val round = chosenRound
        if (round == null) {
            statistics.players
        } else {
            statistics.players.map { player -> PlayerStatistics(player.playerId, player.results.filter { it.roundId == round }) }
        }
    }
    val playedRounds = remember(statistics) { statistics.players.flatMap { player -> player.results.map { it.roundId } }.toSet() }
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
    if (leaders.isEmpty() && chosenRound == null) return
    StatCard(stringResource(Res.string.stats_leaders), Modifier.fillMaxWidth()) {
        if (!roundIds.isNullOrEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = chosenRound == null,
                    onClick = { chosenRound = null },
                    label = { Text(stringResource(Res.string.stats_totals)) },
                )
                roundIds.forEach { roundId ->
                    FilterChip(
                        selected = roundId == chosenRound,
                        // A tap on the chosen round goes back to the totals.
                        onClick = { chosenRound = if (roundId == chosenRound) null else roundId },
                        label = { Text(stringResource(Res.string.round_short, roundId)) },
                        enabled = roundId in playedRounds,
                    )
                }
            }
        }
        StaggeredColumns(minColumnWidth = 280.dp) {
            leaders.forEach { leader -> LeaderSkills(leader, names, onOpenPlayer) }
        }
    }
}

/** One leader: the board, the name and the value of the player, and the skills chart of the player. */
@Composable
private fun LeaderSkills(leader: Leader, names: Map<Int, String>, onOpenPlayer: ((Int) -> Unit)?) {
    val playerId = leader.player.playerId
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        Text(leader.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            Modifier.then(
                if (onOpenPlayer != null) {
                    Modifier.clickable(onClickLabel = "Open the statistics of the player") { onOpenPlayer(playerId) }
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
