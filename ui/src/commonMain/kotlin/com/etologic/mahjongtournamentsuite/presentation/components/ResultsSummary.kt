package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.summary_col_place
import mahjongtournamentsuite.ui.generated.resources.summary_col_points
import mahjongtournamentsuite.ui.generated.resources.summary_col_round
import mahjongtournamentsuite.ui.generated.resources.summary_col_score
import mahjongtournamentsuite.ui.generated.resources.summary_col_seat
import mahjongtournamentsuite.ui.generated.resources.summary_col_table
import mahjongtournamentsuite.ui.generated.resources.summary_col_total_points
import mahjongtournamentsuite.ui.generated.resources.summary_col_total_score
import mahjongtournamentsuite.ui.generated.resources.summary_place_chart
import org.jetbrains.compose.resources.stringResource

/**
 * One round of a player in [ResultsSummaryCard]. The values of a table that is not finished yet are null, and so are
 * the totals and the place of a round without a ranking yet.
 */
@Immutable
data class ResultsSummaryRow(
    val round: Int,
    val table: Int,
    /** The seat of the player at the start of the table. Null before the players set the seats. */
    val wind: SeatWind?,
    /** The table points of the round: 4, 2, 1 or 0, or a share of them on a tie. */
    val points: Double?,
    val score: Int?,
    /** The table points and the score of the player after this round, as the ranking counts them. */
    val totalPoints: Double?,
    val totalScore: Int?,
    /** The place in the ranking after this round. */
    val place: Int?,
)

/** The narrowest the table gets. A narrower place scrolls it sideways. */
val ResultsSummaryMinWidth: Dp = 328.dp

/** The widest the table gets, so the numbers stay close to their headers on a computer. */
val ResultsSummaryMaxWidth: Dp = 560.dp

/**
 * The least each column needs for its values, before its title counts: round, table, seat, points, score,
 * total points, total score, place.
 */
private val ValueWidths = listOf(24.dp, 24.dp, 28.dp, 32.dp, 44.dp, 36.dp, 48.dp, 28.dp)

private val RowHeight = 34.dp

/**
 * All the rounds of one player of a tournament in one compact table, made to be seen at a glance and shared as an
 * image: round, table, seat, table points, score, the totals after the round and the place in the ranking after it.
 * The card has its own background, so a capture of it looks the same anywhere.
 */
@Composable
fun ResultsSummaryCard(
    tournament: String,
    player: String,
    /** Blank for a player without a country: the flag is then a globe. */
    country: String,
    emaId: String?,
    rows: List<ResultsSummaryRow>,
    modifier: Modifier = Modifier,
    /** Room at the right of the header for a button that the screen puts over the card, such as Share. */
    headerEndSpace: Dp = 0.dp,
    /** The players of the tournament: the bottom of the chart of the places. 0 leaves the chart out. */
    playerCount: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier,
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column {
            // The header: the tournament, then the player with the flag and the EMA number.
            Column(
                Modifier.fillMaxWidth().background(colors.primary)
                    .padding(start = MtsTheme.spacing.lg, top = MtsTheme.spacing.md, end = MtsTheme.spacing.lg + headerEndSpace, bottom = MtsTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
            ) {
                Text(
                    tournament,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                    PlayerFlag(country, width = 24.dp)
                    Text(
                        player,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!emaId.isNullOrBlank()) {
                        Text("EMA $emaId", style = MaterialTheme.typography.bodySmall, color = colors.onPrimary.copy(alpha = 0.8f), maxLines = 1)
                    }
                }
            }
            ResultsTable(rows)
            // Two places or more make a line: how the player went up and down the ranking, round by round.
            if (playerCount > 1 && rows.count { it.place != null } >= 2) {
                PlaceChart(rows, playerCount, Modifier.fillMaxWidth().padding(start = MtsTheme.spacing.md, top = MtsTheme.spacing.md, end = MtsTheme.spacing.md))
            }
            Text(
                "Mahjong Tournament Suite",
                Modifier.fillMaxWidth().padding(horizontal = MtsTheme.spacing.md, vertical = MtsTheme.spacing.sm),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** The titles of the columns and a row for each round. Each column is as wide as its title and its values need. */
@Composable
private fun ResultsTable(rows: List<ResultsSummaryRow>) {
    val colors = MaterialTheme.colorScheme
    val titles = listOf(
        stringResource(Res.string.summary_col_round),
        stringResource(Res.string.summary_col_table),
        stringResource(Res.string.summary_col_seat),
        stringResource(Res.string.summary_col_points),
        stringResource(Res.string.summary_col_score),
        stringResource(Res.string.summary_col_total_points),
        stringResource(Res.string.summary_col_total_score),
        stringResource(Res.string.summary_col_place),
    )
    // The plain font: the font of the titles is too wide for the columns of a phone.
    val base = MaterialTheme.typography.labelMedium.inTextFont().copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth - RowPadding * 2
        // A title takes two lines at most and never breaks a word, so its longest word sets how wide it must be.
        // When the columns do not fit, the titles get smaller, all of them by the same amount.
        val layout = remember(titles, available, base, density) {
            fun needs(size: Float): List<Dp> = titles.mapIndexed { index, title ->
                val sized = base.copy(fontSize = size.sp)
                val longest = title.trim().split(' ').maxOf { word -> measurer.measure(word, sized, maxLines = 1, softWrap = false).size.width }
                val wanted = with(density) { longest.toDp() } + CellPadding * 2 + 2.dp
                maxOf(wanted, ValueWidths[index])
            }
            val size = generateSequence(base.fontSize.value) { it - 0.5f }.takeWhile { it >= MIN_TITLE_SIZE }
                .firstOrNull { needs(it).fold(0.dp) { sum, width -> sum + width } <= available } ?: MIN_TITLE_SIZE
            base.copy(fontSize = size.sp) to needs(size).map { it.value }
        }
        val (style, weights) = layout
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).background(colors.primaryContainer).padding(horizontal = RowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titles.forEachIndexed { index, title ->
                    Text(
                        title,
                        Modifier.weight(weights[index]).padding(horizontal = CellPadding, vertical = MtsTheme.spacing.xs),
                        style = style,
                        color = colors.onPrimaryContainer,
                        maxLines = 2,
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                ResultRow(row, weights, striped = index % 2 == 1, last = index == rows.lastIndex)
            }
        }
    }
}

/** The smallest size of the titles of the columns, in sp. */
private const val MIN_TITLE_SIZE = 8f

private val RowPadding = 4.dp
private val CellPadding = 2.dp

@Composable
private fun ResultRow(row: ResultsSummaryRow, weights: List<Float>, striped: Boolean, last: Boolean) {
    val colors = MaterialTheme.colorScheme
    val background = if (striped) colors.surfaceContainerHigh else colors.surface
    val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Row(
        Modifier.fillMaxWidth().height(RowHeight).background(background).padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(weights[0], row.round.toString(), style, TextAlign.Center)
        Cell(weights[1], row.table.toString(), style, TextAlign.Center)
        Box(Modifier.weight(weights[2]), contentAlignment = Alignment.Center) {
            row.wind?.let { WindIcon(it, size = 20.dp) } ?: Text(PENDING, style = style, color = colors.onSurfaceVariant)
        }
        Cell(weights[3], row.points?.let(::formatPoints), style, TextAlign.Center)
        Cell(weights[4], row.score?.let(::signedText), style, TextAlign.Center, color = row.score?.let { scoreColor(it) })
        Cell(weights[5], row.totalPoints?.let(::formatPoints), style.copy(fontWeight = FontWeight.SemiBold), TextAlign.Center)
        Cell(weights[6], row.totalScore?.let(::signedText), style.copy(fontWeight = FontWeight.SemiBold), TextAlign.Center, color = row.totalScore?.let { scoreColor(it) })
        // The place after the last round is the one that matters most.
        Cell(weights[7], row.place?.let { "$it" }, style.copy(fontWeight = if (last) FontWeight.ExtraBold else FontWeight.SemiBold), TextAlign.Center)
    }
}

@Composable
private fun RowScope.Cell(weight: Float, text: String?, style: TextStyle, align: TextAlign, color: Color? = null) {
    Text(
        text ?: PENDING,
        Modifier.weight(weight).padding(horizontal = CellPadding),
        style = style,
        color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else color ?: MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
        softWrap = false,
    )
}

/** Like the capture of a ranking: only a loss stands out, in red. */
@Composable
private fun scoreColor(value: Int): Color = if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

private const val PENDING = "–"

/** The test tag of the chart of the places under the table. */
const val RESULTS_PLACE_CHART_TAG = "results-place-chart"

private val PlaceChartHeight = 112.dp

/**
 * A small line of the place in the ranking after each round: the first place at the top, the last one at the bottom.
 * Each round of the table has its spot, so the dots sit under their rows' order; a round without a place is a gap.
 * The place is written next to each dot, or, when they are too close, next to the first, the best and the last.
 */
@Composable
private fun PlaceChart(rows: List<ResultsSummaryRow>, playerCount: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val lastStyle = valueStyle.copy(color = colors.primary, fontWeight = FontWeight.ExtraBold)
    val title = stringResource(Res.string.summary_place_chart)
    Column(modifier.testTag(RESULTS_PLACE_CHART_TAG), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(PlaceChartHeight)) {
            val axis = 28.dp.toPx()
            val vertical = 14.dp.toPx()
            val side = 10.dp.toPx()
            val plotWidth = size.width - axis - side * 2
            val plotHeight = size.height - vertical * 2
            fun x(index: Int) = axis + side + if (rows.size == 1) plotWidth / 2f else plotWidth * index / (rows.size - 1)
            fun y(place: Int) = vertical + plotHeight * (place - 1) / (playerCount - 1).toFloat()
            // The first and the last place of the tournament, as two guide lines with their numbers.
            listOf(1, playerCount).forEach { place ->
                drawLine(colors.outlineVariant, Offset(axis, y(place)), Offset(size.width, y(place)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                val layout = measurer.measure("$place", labelStyle)
                drawText(layout, topLeft = Offset(axis - layout.size.width - 4.dp.toPx(), y(place) - layout.size.height / 2f))
            }
            val points = rows.mapIndexedNotNull { index, row -> row.place?.let { index to it } }
            // A gap of rounds without a place breaks the line.
            val path = Path()
            points.forEachIndexed { i, (index, place) ->
                if (i == 0 || points[i - 1].first != index - 1) path.moveTo(x(index), y(place)) else path.lineTo(x(index), y(place))
            }
            drawPath(path, colors.primary.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            val last = points.last().first
            val best = points.minBy { it.second }.first
            val spacing = if (rows.size > 1) plotWidth / (rows.size - 1) else plotWidth
            val crowded = spacing < 26.dp.toPx()
            points.forEach { (index, place) ->
                val isLast = index == last
                drawCircle(colors.surface, (if (isLast) 6f else 4.5f).dp.toPx(), Offset(x(index), y(place)))
                drawCircle(colors.primary, (if (isLast) 5f else 3.5f).dp.toPx(), Offset(x(index), y(place)))
                if (crowded && index != points.first().first && index != best && !isLast) return@forEach
                val layout = measurer.measure("$place", if (isLast) lastStyle else valueStyle)
                // The number goes above the dot, or below it near the top of the chart.
                val above = y(place) - layout.size.height - 5.dp.toPx()
                val top = if (above < 0f) y(place) + 5.dp.toPx() else above
                val left = (x(index) - layout.size.width / 2f).coerceIn(axis, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(left, top))
            }
        }
    }
}
