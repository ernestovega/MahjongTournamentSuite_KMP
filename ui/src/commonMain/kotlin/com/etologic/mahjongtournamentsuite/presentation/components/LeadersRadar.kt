package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.leader_best_hand_value
import mahjongtournamentsuite.ui.generated.resources.leader_best_self_draw_rate
import mahjongtournamentsuite.ui.generated.resources.leader_best_win_rate
import mahjongtournamentsuite.ui.generated.resources.leader_lowest_deal_in
import mahjongtournamentsuite.ui.generated.resources.open_player_statistics
import mahjongtournamentsuite.ui.generated.resources.stats_leaders
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One corner of the top players web: the best player of one stat and the value of that player.
 * [reach] is how far the average player gets towards the best one, from 0 to 1.
 */
private class LeaderCorner(
    val title: String,
    val player: PlayerStatistics?,
    val value: String,
    val calculation: String,
    val reach: Float,
)

/** How far [average] gets towards [best], from 0 to 1. A lower value is better when [lowerIsBetter]. */
private fun reach(average: Double?, best: Double?, lowerIsBetter: Boolean = false): Float {
    if (average == null || best == null) return 0f
    val ratio = if (lowerIsBetter) {
        if (average <= 0.0) 1.0 else best / average
    } else {
        if (best <= 0.0) 1.0 else average / best
    }
    return ratio.coerceIn(0.0, 1.0).toFloat()
}

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

@Composable
private fun leaderCorners(statistics: TournamentStatistics): List<LeaderCorner> {
    val players = statistics.players
    // The rates need enough hands, like the leaderboards: a player with one won hand would have a win rate of 100%.
    val eligible = players.filter { it.handsPlayed >= MIN_HANDS_FOR_LEADERBOARD }
    val winners = eligible.filter { it.handsWon > 0 }
    // The self-draw rate counts the won hands: one won hand by self-draw would be 100%, so it needs a few of them.
    val drawers = winners.filter { it.handsWon >= MIN_WINS_FOR_SELF_DRAW_RATE }.ifEmpty { winners }

    val winRate = eligible.maxByOrNull { it.winRate ?: 0.0 }
    val selfDraw = drawers.maxByOrNull { it.selfDrawRate ?: 0.0 }
    val dealIn = eligible.minByOrNull { it.dealInRate ?: 1.0 }
    val handValue = winners.maxByOrNull { it.averageWinningScore ?: 0.0 }

    // The same four skills, in the same places, as the skills chart of a player: clockwise from the top.
    return listOf(
        LeaderCorner(
            stringResource(Res.string.leader_best_win_rate),
            winRate,
            winRate?.winRate.percent(),
            winRate?.let { "(${it.handsWon}/${it.handsPlayed})" }.orEmpty(),
            reach(eligible.mapNotNull { it.winRate }.averageOrNull(), winRate?.winRate),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_best_self_draw_rate),
            selfDraw,
            selfDraw?.selfDrawRate.percent(),
            selfDraw?.let { "(${it.selfDraws}/${it.handsWon})" }.orEmpty(),
            reach(drawers.mapNotNull { it.selfDrawRate }.averageOrNull(), selfDraw?.selfDrawRate),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_lowest_deal_in),
            dealIn,
            dealIn?.dealInRate.percent(),
            dealIn?.let { "(${it.dealIns}/${it.handsPlayed})" }.orEmpty(),
            reach(eligible.mapNotNull { it.dealInRate }.averageOrNull(), dealIn?.dealInRate, lowerIsBetter = true),
        ),
        LeaderCorner(
            stringResource(Res.string.leader_best_hand_value),
            handValue,
            handValue?.averageWinningScore.decimal(),
            handValue?.let { "(${it.handsWon})" }.orEmpty(),
            reach(winners.mapNotNull { it.averageWinningScore }.averageOrNull(), handValue?.averageWinningScore),
        ),
    )
}

private val AvatarSize = 44.dp

/** The won hands that a player needs before the self-draw rate counts. */
private const val MIN_WINS_FOR_SELF_DRAW_RATE = 3

/**
 * The best players as a spider web with four corners, the skills of the skills chart in the same places.
 * Each corner shows the stat, the photo and the name of the best player, and the value; a tap opens the player.
 * The shaded shape is the average player as a share of the best one, so it shows how far the best are from the rest.
 * Outside is always better, also for the deal-in rate.
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
        LeadersWeb(corners, names, photos, onOpenPlayer)
    }
}

private fun cornerAngle(index: Int, count: Int): Double = -PI / 2 + 2 * PI * index / count

/**
 * The web and one block of text at the end of each axis: above the top one, below the bottom one and beside the
 * side ones. The side blocks take at most a third of the width each, so the web keeps the middle. The top and bottom
 * blocks move further out when the side blocks are taller than the web, and the web grows taller with them.
 */
@Composable
private fun LeadersWeb(
    corners: List<LeaderCorner>,
    names: Map<Int, String>,
    photos: Map<Int, String?>,
    onOpenPlayer: ((Int) -> Unit)?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val count = corners.size
    val radiusState = remember { mutableFloatStateOf(0f) }
    val centerYState = remember { mutableFloatStateOf(0f) }
    // The web is as tall as the room between the top and bottom blocks, so on a phone it is a tall diamond.
    val heightRadiusState = remember { mutableFloatStateOf(0f) }
    val openLabel = stringResource(Res.string.open_player_statistics)

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Canvas(Modifier.fillMaxSize()) {
                val radius = radiusState.floatValue
                val heightRadius = heightRadiusState.floatValue
                if (radius <= 0f) return@Canvas
                val center = Offset(size.width / 2f, centerYState.floatValue)
                fun point(index: Int, fraction: Float): Offset {
                    val angle = cornerAngle(index, count)
                    return Offset(center.x + (radius * fraction * cos(angle)).toFloat(), center.y + (heightRadius * fraction * sin(angle)).toFloat())
                }
                fun shape(fractions: List<Float>) = Path().apply {
                    fractions.forEachIndexed { i, f -> point(i, f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    close()
                }
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ring -> drawPath(shape(List(count) { ring }), grid, style = Stroke(width = 1.dp.toPx())) }
                repeat(count) { i -> drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx()) }
                val reaches = corners.map { it.reach }
                drawPath(shape(reaches), primary.copy(alpha = 0.22f))
                drawPath(shape(reaches), primary, style = Stroke(width = 2.dp.toPx()))
                reaches.forEachIndexed { i, reach -> drawCircle(primary, 3.5.dp.toPx(), point(i, reach)) }
            }
            corners.forEach { corner ->
                val player = corner.player
                val open = if (player != null && onOpenPlayer != null) {
                    Modifier.clickable(onClickLabel = openLabel) { onOpenPlayer(player.playerId) }
                } else {
                    Modifier
                }
                CornerBlock(corner, names, photos, open)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.toPx()
        val side = (0 until count).map { abs(cos(cornerAngle(it, count))) > 0.3 }
        val blocks = measurables.drop(1).mapIndexed { index, block ->
            val maxWidth = if (side[index]) min(width / 3, 170.dp.roundToPx()) else min(width, 260.dp.roundToPx())
            block.measure(Constraints(maxWidth = maxWidth))
        }
        val sideWidth = blocks.filterIndexed { i, _ -> side[i] }.maxOfOrNull { it.width } ?: 0
        val sideHalfHeight = blocks.filterIndexed { i, _ -> side[i] }.maxOfOrNull { it.height / 2f } ?: 0f
        val radius = (width / 2f - sideWidth - gap).coerceIn(40.dp.toPx(), 130.dp.toPx())
        // A top or bottom block stays clear of the side blocks.
        val endDistance = max(radius + gap, sideHalfHeight + gap)
        val places = blocks.mapIndexed { index, block ->
            val angle = cornerAngle(index, count)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            when {
                side[index] -> Offset(if (dx > 0) radius + gap else -radius - gap - block.width, -block.height / 2f)
                dy < 0 -> Offset(-block.width / 2f, -endDistance - block.height)
                else -> Offset(-block.width / 2f, endDistance)
            }
        }
        val top = places.minOf { it.y }
        val bottom = places.indices.maxOf { places[it].y + blocks[it].height }
        val height = ceil(bottom - top).toInt()
        val centerX = width / 2f
        val centerY = -top
        radiusState.floatValue = radius
        centerYState.floatValue = centerY
        heightRadiusState.floatValue = endDistance - gap
        val canvas = measurables[0].measure(Constraints.fixed(width, height))
        layout(width, height) {
            canvas.place(0, 0)
            blocks.forEachIndexed { index, block ->
                block.place(
                    (centerX + places[index].x).roundToInt().coerceIn(0, max(0, width - block.width)),
                    (centerY + places[index].y).roundToInt(),
                )
            }
        }
    }
}

/** One corner: the stat, the photo of the best player, the name and the value, one under the other. */
@Composable
private fun CornerBlock(corner: LeaderCorner, names: Map<Int, String>, photos: Map<Int, String?>, open: Modifier) {
    val player = corner.player
    val name = player?.let { names[it.playerId] ?: "Player ${it.playerId}" }
    Column(
        Modifier.clip(MaterialTheme.shapes.medium).then(open).padding(MtsTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        Text(
            corner.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, if (player != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (player != null && name != null) PlayerPhoto(photos[player.playerId], name, size = AvatarSize - 6.dp)
        }
        if (name != null) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.inTextFont(),
                fontWeight = FontWeight.Bold,
                // A name in the color of the links shows that the player opens.
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CenteredValue(corner.value, corner.calculation)
    }
}

/**
 * The value in the middle of its block, and the calculation in brackets to its right. The calculation takes the same
 * room on the left too, so it does not push the value off the middle.
 */
@Composable
private fun CenteredValue(value: String, calculation: String) {
    val gapSize = MtsTheme.spacing.xs
    Layout(
        content = {
            Text(value, style = MaterialTheme.typography.titleLarge.inTextFont(), fontWeight = FontWeight.Bold)
            if (calculation.isNotEmpty()) {
                Text(calculation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val gap = gapSize.roundToPx()
        val valueText = measurables[0].measure(constraints.copy(minWidth = 0))
        // The calculation keeps one line.
        val side = measurables.getOrNull(1)?.measure(Constraints())
        val sideRoom = side?.let { it.width + gap } ?: 0
        val width = (valueText.width + 2 * sideRoom).coerceIn(constraints.minWidth, max(constraints.minWidth, constraints.maxWidth))
        layout(width, valueText.height) {
            // In the middle; in a block too narrow for that, as far right as the calculation lets it.
            val x = ((width - valueText.width) / 2).coerceAtMost(max(0, width - valueText.width - sideRoom))
            valueText.place(x, 0)
            // On the line of the value, at its foot.
            side?.place(x + valueText.width + gap, valueText.height - side.height - 3.dp.roundToPx())
        }
    }
}
