package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.leader_best_hand_value
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in
import mahjongtournamentsuite.ui.generated.resources.leader_most_self_draws
import mahjongtournamentsuite.ui.generated.resources.leaders_average
import mahjongtournamentsuite.ui.generated.resources.leaders_legend_average
import mahjongtournamentsuite.ui.generated.resources.leaders_legend_top
import mahjongtournamentsuite.ui.generated.resources.open_player_statistics
import mahjongtournamentsuite.ui.generated.resources.stats_leaders
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One corner of the top players chart: the best player of one stat, the value of that player and the average of the players.
 * [reach] is how far the average gets towards the best player, from 0 to 1. A lower value is better when [lowerIsBetter].
 */
private class LeaderCorner(
    val title: String,
    val player: PlayerStatistics?,
    val value: String,
    val calculation: String,
    val average: String,
    val reach: Float,
)

private fun reach(average: Double?, best: Double?, lowerIsBetter: Boolean = false): Float {
    if (average == null || best == null) return 0f
    val ratio = if (lowerIsBetter) {
        if (average <= 0.0) 1.0 else best / average
    } else {
        if (best <= 0.0) 1.0 else average / best
    }
    return ratio.coerceIn(0.0, 1.0).toFloat()
}

@Composable
private fun leaderCorners(statistics: TournamentStatistics): List<LeaderCorner> {
    val players = statistics.players
    // The rates need enough hands, like the leaderboards: a player with one won hand would have a win rate of 100%.
    val eligible = players.filter { it.handsPlayed >= MIN_HANDS_FOR_LEADERBOARD }
    val winners = eligible.filter { it.handsWon > 0 }

    val handValue = winners.maxByOrNull { it.averageWinningScore ?: 0.0 }
    val handValueAverage = winners.mapNotNull { it.averageWinningScore }.averageOrNull()
    val winRate = eligible.maxByOrNull { it.winRate ?: 0.0 }
    val winRateAverage = eligible.mapNotNull { it.winRate }.averageOrNull()
    val dealIn = eligible.minByOrNull { it.dealInRate ?: 1.0 }
    val dealInAverage = eligible.mapNotNull { it.dealInRate }.averageOrNull()
    val selfDraws = players.filter { it.selfDraws > 0 }.maxByOrNull { it.selfDraws }
    val selfDrawsAverage = players.filter { it.handsPlayed > 0 }.map { it.selfDraws.toDouble() }.averageOrNull()

    // Clockwise from the top left corner.
    return listOf(
        LeaderCorner(
            stringResource(Res.string.leader_best_hand_value),
            handValue,
            handValue?.averageWinningScore.decimal(),
            handValue?.let { "(${it.handsWon})" }.orEmpty(),
            handValueAverage.decimal(),
            reach(handValueAverage, handValue?.averageWinningScore),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_best_win_rate),
            winRate,
            winRate?.winRate.percent(),
            winRate?.let { "(${it.handsWon}/${it.handsPlayed})" }.orEmpty(),
            winRateAverage.percent(),
            reach(winRateAverage, winRate?.winRate),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_most_self_draws),
            selfDraws,
            selfDraws?.selfDraws?.toString() ?: "-",
            selfDraws?.let { "(${it.handsPlayed})" }.orEmpty(),
            selfDrawsAverage.decimal(),
            reach(selfDrawsAverage, selfDraws?.selfDraws?.toDouble()),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_lowest_deal_in),
            dealIn,
            dealIn?.dealInRate.percent(),
            dealIn?.let { "(${it.dealIns}/${it.handsPlayed})" }.orEmpty(),
            dealInAverage.percent(),
            reach(dealInAverage, dealIn?.dealInRate, lowerIsBetter = true),
        ),
    )
}

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

/**
 * The best players as a spider chart with four corners: best hand value, best win rate, most self-draws and lowest deal-in.
 * Each corner shows the stat, the value and the photo and name of the best player; the name opens the player.
 * The outer square is the best player of each stat. The filled shape is the average player, as a share of the best one,
 * so it shows how far the best players are from the rest. Outside is always better, also for the deal-in rate.
 */
@Composable
internal fun LeadersCard(
    statistics: TournamentStatistics,
    names: Map<Int, String>,
    photos: Map<Int, String?>,
    onOpenPlayer: ((Int) -> Unit)?,
) {
    val corners = leaderCorners(statistics)
    if (corners.all { it.player == null }) return
    StatCard(stringResource(Res.string.stats_leaders), Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            // The corners of the chart sit under the middle of each half, so each name stands right above or below its corner.
            val chartHeight = min(width / 2, 240.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    LeaderCornerLabel(corners[0], names, photos, onOpenPlayer, Modifier.weight(1f), top = true)
                    LeaderCornerLabel(corners[1], names, photos, onOpenPlayer, Modifier.weight(1f), top = true)
                }
                LeadersSpider(corners, Modifier.fillMaxWidth().height(chartHeight), width)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    LeaderCornerLabel(corners[3], names, photos, onOpenPlayer, Modifier.weight(1f), top = false)
                    LeaderCornerLabel(corners[2], names, photos, onOpenPlayer, Modifier.weight(1f), top = false)
                }
                LeadersLegend(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** The chart: the rings, the four axes to the corners, the square of the best players and the shape of the average player. */
@Composable
private fun LeadersSpider(corners: List<LeaderCorner>, modifier: Modifier, width: Dp) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val average = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // The corners are at the middle of each half, or closer when the chart is not tall enough.
        val offset = minOf(width.toPx() / 4f, size.height / 2f - 8.dp.toPx())
        val radius = offset * sqrt(2f)
        fun point(index: Int, fraction: Float): Offset {
            // Top left, top right, bottom right, bottom left.
            val angle = -3 * PI / 4 + PI / 2 * index
            return Offset(center.x + (radius * fraction * cos(angle)).toFloat(), center.y + (radius * fraction * sin(angle)).toFloat())
        }
        fun shape(fractions: List<Float>) = Path().apply {
            fractions.forEachIndexed { i, f -> point(i, f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        listOf(0.25f, 0.5f, 0.75f).forEach { ring -> drawPath(shape(List(4) { ring }), grid, style = Stroke(width = 1.dp.toPx())) }
        repeat(4) { i -> drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx()) }
        val reaches = corners.map { it.reach }
        drawPath(shape(reaches), average.copy(alpha = 0.2f))
        drawPath(shape(reaches), average, style = Stroke(width = 2.dp.toPx()))
        val best = corners.map { if (it.player != null) 1f else 0f }
        drawPath(shape(best), primary, style = Stroke(width = 2.5.dp.toPx()))
        corners.forEachIndexed { i, corner ->
            if (corner.player != null) drawCircle(primary, 6.dp.toPx(), point(i, 1f))
            drawCircle(average, 3.5.dp.toPx(), point(i, corner.reach))
        }
    }
}

/** The text at one corner: the stat, the value of the best player, the photo and name of the player, and the average. */
@Composable
private fun LeaderCornerLabel(
    corner: LeaderCorner,
    names: Map<Int, String>,
    photos: Map<Int, String?>,
    onOpenPlayer: ((Int) -> Unit)?,
    modifier: Modifier,
    top: Boolean,
) {
    val player = corner.player
    val openLabel = stringResource(Res.string.open_player_statistics)
    Column(
        modifier.padding(horizontal = MtsTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val stat: @Composable () -> Unit = {
            Text(
                corner.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                Text(corner.value, style = MaterialTheme.typography.titleLarge.inTextFont(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (corner.calculation.isNotEmpty()) {
                    Text(
                        corner.calculation,
                        Modifier.padding(bottom = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val person: @Composable () -> Unit = {
            if (player != null) {
                val name = names[player.playerId] ?: "Player ${player.playerId}"
                val open = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .then(if (onOpenPlayer != null) Modifier.clickable(onClickLabel = openLabel) { onOpenPlayer(player.playerId) } else Modifier)
                    .padding(horizontal = MtsTheme.spacing.xs, vertical = 2.dp)
                val nameText: @Composable (Modifier) -> Unit = { nameModifier ->
                    Text(
                        name,
                        nameModifier,
                        style = MaterialTheme.typography.bodyMedium.inTextFont(),
                        fontWeight = FontWeight.Bold,
                        // A name in the color of the links shows that the player opens.
                        color = if (onOpenPlayer != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    // On a phone half of the card is narrow: the photo goes above the name, so the name keeps one line.
                    if (maxWidth < 200.dp) {
                        Column(open, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PlayerPhoto(photos[player.playerId], name, size = 32.dp)
                            nameText(Modifier)
                        }
                    } else {
                        Row(open, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                            PlayerPhoto(photos[player.playerId], name, size = 32.dp)
                            nameText(Modifier.weight(1f, fill = false))
                        }
                    }
                }
            }
        }
        val average: @Composable () -> Unit = {
            Text(
                stringResource(Res.string.leaders_average, corner.average),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // The stat is always furthest from the chart, so the player and the average stand next to the corner.
        if (top) {
            stat()
            person()
            average()
        } else {
            average()
            person()
            stat()
        }
    }
}

@Composable
private fun LeadersLegend(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, stringResource(Res.string.leaders_legend_top))
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(Res.string.leaders_legend_average))
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
