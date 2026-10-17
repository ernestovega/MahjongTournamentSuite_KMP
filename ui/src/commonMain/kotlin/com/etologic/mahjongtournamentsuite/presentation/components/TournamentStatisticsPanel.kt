package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
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
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StaggeredColumns(minColumnWidth = 480.dp) {
            TournamentTotalsCard(statistics, tournamentCount, playerCount)
            TournamentHandsCard(statistics)
            TournamentRecordsCard(statistics, playersAreSums = playerCount != null)
            val minimum = MIN_HANDS_FOR_LEADERBOARD
            val points = statistics.players.filter { it.tablesPlayed > 0 }.sortedByDescending { it.totalPoints }
            LeaderboardCard(
                "Most points", points, names, { "${formatPoints(it.totalPoints)} points" }, onOpenPlayer,
                "The sum of the table points of each player, from the completed tables.",
            )
            LeaderboardCard(
                "Best win rate", statistics.winRateLeaders, names, { it.winRate.percent() }, onOpenPlayer,
                "Players with at least $minimum hands. Hands won divided by hands played.",
                detail = { "${it.handsPlayed} hands" },
            )
            LeaderboardCard(
                "Lowest deal-in rate", statistics.lowestDealInLeaders, names, { it.dealInRate.percent() }, onOpenPlayer,
                "Players with at least $minimum hands. Deal-ins divided by hands played.",
                detail = { "${it.handsPlayed} hands" },
            )
            LeaderboardCard(
                "Most self-draws", statistics.selfDrawLeaders, names, { it.selfDraws.toString() }, onOpenPlayer,
                detail = { "${it.handsPlayed} hands" },
            )
        }
        if (statistics.tablesWithHands < statistics.tablesCompleted) {
            Text(
                if (statistics.tablesWithHands == 0) {
                    "Hand statistics need tables with entered hands. No table here has them."
                } else {
                    "Hand values use ${statistics.tablesWithHands} of ${statistics.tablesCompleted} tables."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TournamentTotalsCard(statistics: TournamentStatistics, tournamentCount: Int?, playerCount: Int?) {
    StatCard("Totals", Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tournamentCount != null) BigNumber("Tournaments", tournamentCount.toString())
            BigNumber(
                "Tables",
                if (statistics.tablesCompleted == statistics.tablesTotal) {
                    statistics.tablesCompleted.toString()
                } else {
                    "${statistics.tablesCompleted} of ${statistics.tablesTotal}"
                },
            )
            BigNumber("Players", (playerCount ?: statistics.players.count { it.tablesPlayed > 0 }).toString())
            BigNumber("Hands", if (statistics.handsPlayed > 0) statistics.handsPlayed.toString() else EMPTY)
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
    val chicken = statistics.chickenHands.coerceAtMost(byDiscard)
    val parts = listOf(
        Triple("Won by discard", byDiscard - chicken, won.copy(alpha = 0.75f)),
        Triple("Chicken hands", chicken, MaterialTheme.colorScheme.tertiary),
        Triple("Won by self-draw", selfDraws, won.copy(alpha = 0.45f)),
        Triple("Drawn", statistics.drawnHands, MaterialTheme.colorScheme.outline),
    ).filter { it.second > 0 || it.first != "Chicken hands" }
    StatCard("Hands", Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectorChart(
                parts.map { Slice(it.second, it.third) }, null, 200.dp, 0.26f,
                centerValue = statistics.handsPlayed.toString(), centerLabel = "hands",
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
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = RECORDS_PER_ROW,
        ) {
            BigNumber("Draw rate", statistics.drawRate.percent(), Modifier.weight(1f))
            BigNumber("Average winning hand", statistics.averageHandScore.decimal(), Modifier.weight(1f))
            if (statistics.penaltyCount > 0) BigNumber("Penalties", statistics.penaltyCount.toString(), Modifier.weight(1f))
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
    StatCard("Records", Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = RECORDS_PER_ROW,
        ) {
            BigNumber("Highest hand score", statistics.highestHand.text(), Modifier.weight(1f))
            if (!playersAreSums) {
                BigNumber("Highest table score", players.mapNotNull { it.bestTableScore }.maxOrNull().text(), Modifier.weight(1f))
                BigNumber("Hands won on a table", players.mapNotNull { it.mostHandsWonAtATable }.maxOrNull().text(), Modifier.weight(1f))
                BigNumber("Highest penalty got", players.mapNotNull { it.highestPenalty }.maxOrNull().text(), Modifier.weight(1f))
                BigNumber("Tables won in a row", (streaks.maxOfOrNull { it.tableWins } ?: 0).toString(), Modifier.weight(1f))
                BigNumber("Hands won in a row", streaks.mapNotNull { it.handsWon }.maxOrNull().text(), Modifier.weight(1f))
                BigNumber("Hands without a deal-in", streaks.mapNotNull { it.handsWithoutDealIn }.maxOrNull().text(), Modifier.weight(1f))
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
    /** A small text before the value of each player, for example the hands that the rate comes from. */
    detail: ((PlayerStatistics) -> String)? = null,
) {
    if (entries.isEmpty()) return
    StatCard(title, Modifier.fillMaxWidth(), info = info) {
        entries.take(5).forEachIndexed { index, player ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth()
                    .then(if (onOpenPlayer != null) Modifier.clickable { onOpenPlayer(player.playerId) } else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}.", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                Text(
                    names[player.playerId] ?: "Player ${player.playerId}",
                    Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (detail != null) {
                    Text(
                        detail(player),
                        Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(value(player), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
