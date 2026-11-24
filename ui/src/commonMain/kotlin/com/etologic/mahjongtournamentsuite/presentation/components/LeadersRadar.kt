package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.compositeOver
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
import mahjongtournamentsuite.ui.generated.resources.leaders_average_shape
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
 * The best players as a spider web with four corners. The photo of the best player of each stat sits on its corner,
 * with the stat, the value and the name next to it; the photo and the name open the player.
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
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
            LeadersWeb(corners, names, photos, onOpenPlayer)
            Text(
                stringResource(Res.string.leaders_average_shape),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun cornerAngle(index: Int, count: Int): Double = -PI / 2 + 2 * PI * index / count

/**
 * The web, the photos on its corners and the texts beside them. On a wide card the texts of the side corners stand
 * beside their photos. On a narrow card they stand above or below them, in three columns, so the web keeps its size.
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
    val outline = MaterialTheme.colorScheme.outline
    val count = corners.size
    val radiusState = remember { mutableFloatStateOf(0f) }
    val centerYState = remember { mutableFloatStateOf(0f) }
    val openLabel = stringResource(Res.string.open_player_statistics)
    fun open(player: PlayerStatistics?): Modifier = if (player != null && onOpenPlayer != null) Modifier.clickable(onClickLabel = openLabel) { onOpenPlayer(player.playerId) } else Modifier

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Canvas(Modifier.fillMaxSize()) {
                val radius = radiusState.floatValue
                if (radius <= 0f) return@Canvas
                val center = Offset(size.width / 2f, centerYState.floatValue)
                fun point(index: Int, fraction: Float): Offset {
                    val angle = cornerAngle(index, count)
                    return Offset(center.x + (radius * fraction * cos(angle)).toFloat(), center.y + (radius * fraction * sin(angle)).toFloat())
                }
                fun shape(fractions: List<Float>) = Path().apply {
                    fractions.forEachIndexed { i, f -> point(i, f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    close()
                }
                listOf(0.25f, 0.5f, 0.75f).forEach { ring -> drawPath(shape(List(count) { ring }), grid, style = Stroke(width = 1.dp.toPx())) }
                drawPath(shape(List(count) { 1f }), outline, style = Stroke(width = 1.5.dp.toPx()))
                repeat(count) { i -> drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx()) }
                val reaches = corners.map { it.reach }
                drawPath(shape(reaches), primary.copy(alpha = 0.22f))
                drawPath(shape(reaches), primary, style = Stroke(width = 2.dp.toPx()))
                reaches.forEachIndexed { i, reach -> drawCircle(primary, 3.5.dp.toPx(), point(i, reach)) }
            }
            corners.forEach { corner ->
                val player = corner.player
                Box(
                    Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, if (player != null) primary else grid, CircleShape)
                        .then(open(player))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (player != null) {
                        val name = names[player.playerId] ?: "Player ${player.playerId}"
                        PlayerPhoto(photos[player.playerId], name, size = AvatarSize - 6.dp)
                    }
                }
            }
            corners.forEachIndexed { index, corner ->
                val dx = cos(cornerAngle(index, count))
                val align = when {
                    dx > 0.3 -> TextAlign.Start
                    dx < -0.3 -> TextAlign.End
                    else -> TextAlign.Center
                }
                CornerLabel(corner, names, align, open(corner.player))
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val canvas = measurables[0]
        val avatars = measurables.subList(1, 1 + count).map { it.measure(Constraints.fixed(AvatarSize.roundToPx(), AvatarSize.roundToPx())) }
        val labelMeasurables = measurables.subList(1 + count, 1 + 2 * count)
        val avatarRadius = AvatarSize.toPx() / 2f
        val gap = 6.dp.toPx()
        val side = (0 until count).map { abs(cos(cornerAngle(it, count))) > 0.3 }
        // Wide: the side texts stand beside the photos, as wide as the longest word allows. Narrow: three columns.
        val sideLabelWidth = 150.dp.toPx()
        // The side corner that is furthest out sets how big the web can be.
        val sideCos = (0 until count).filter { side[it] }.maxOfOrNull { abs(cos(cornerAngle(it, count))).toFloat() } ?: 1f
        val wideRadius = (width / 2f - avatarRadius - gap - sideLabelWidth) / sideCos
        val wide = wideRadius >= 80.dp.toPx()
        val radius = if (wide) min(wideRadius, 130.dp.toPx()) else (width / 3f) / sideCos
        val column = width / 3
        val labels = labelMeasurables.mapIndexed { index, label ->
            val maxWidth = when {
                !side[index] -> if (wide) width / 2 else column
                wide -> max(0, (width / 2f - radius * abs(cos(cornerAngle(index, count))).toFloat() - avatarRadius - gap).toInt())
                else -> column
            }
            label.measure(Constraints(maxWidth = maxWidth))
        }
        // The place of each text, with the center of the web at (0, 0).
        val places = labels.mapIndexed { index, label ->
            val angle = cornerAngle(index, count)
            val cx = (radius * cos(angle)).toFloat()
            val cy = (radius * sin(angle)).toFloat()
            val up = sin(angle) < 0
            if (wide && side[index]) {
                val x = if (cos(angle) > 0) cx + avatarRadius + gap else cx - avatarRadius - gap - label.width
                Offset(x, cy - label.height / 2f)
            } else {
                val y = if (up) cy - avatarRadius - gap - label.height else cy + avatarRadius + gap
                Offset(cx - label.width / 2f, y)
            }
        }
        val top = min(places.minOf { it.y }, -radius - avatarRadius)
        val bottom = max(places.indices.maxOf { places[it].y + labels[it].height }, radius + avatarRadius)
        val height = ceil(bottom - top).toInt()
        val centerX = width / 2f
        val centerY = -top
        radiusState.floatValue = radius
        centerYState.floatValue = centerY
        val canvasPlaceable = canvas.measure(Constraints.fixed(width, height))
        layout(width, height) {
            canvasPlaceable.place(0, 0)
            avatars.forEachIndexed { index, avatar ->
                val angle = cornerAngle(index, count)
                avatar.place(
                    (centerX + radius * cos(angle) - avatarRadius).roundToInt(),
                    (centerY + radius * sin(angle) - avatarRadius).roundToInt(),
                )
            }
            labels.forEachIndexed { index, label ->
                label.place(
                    (centerX + places[index].x).roundToInt().coerceIn(0, max(0, width - label.width)),
                    (centerY + places[index].y).roundToInt(),
                )
            }
        }
    }
}

/** The text of one corner: the stat, the value of the best player and the name of the player. */
@Composable
private fun CornerLabel(corner: LeaderCorner, names: Map<Int, String>, align: TextAlign, open: Modifier) {
    val player = corner.player
    val horizontal = when (align) {
        TextAlign.Start -> Alignment.Start
        TextAlign.End -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    // On a narrow card a side text may cross a line of the web: the color of the card behind the text hides it.
    val card = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(MaterialTheme.colorScheme.surface)
    Column(
        Modifier.clip(MaterialTheme.shapes.small).background(card).padding(horizontal = 2.dp),
        horizontalAlignment = horizontal,
    ) {
        Text(
            corner.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
            Text(corner.value, style = MaterialTheme.typography.titleLarge.inTextFont(), fontWeight = FontWeight.Bold)
            if (corner.calculation.isNotEmpty()) {
                Text(
                    corner.calculation,
                    Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (player != null) {
            Text(
                names[player.playerId] ?: "Player ${player.playerId}",
                Modifier.clip(MaterialTheme.shapes.small).then(open),
                style = MaterialTheme.typography.bodyMedium.inTextFont(),
                fontWeight = FontWeight.Bold,
                // A name in the color of the links shows that the player opens.
                color = MaterialTheme.colorScheme.primary,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
