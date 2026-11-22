package com.etologic.mahjongtournamentsuite.presentation.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
                Modifier.fillMaxWidth().background(colors.primary).padding(horizontal = MtsTheme.spacing.lg, vertical = MtsTheme.spacing.md),
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
        Cell(weights[3], row.points?.let(::formatPoints), style, TextAlign.End)
        Cell(weights[4], row.score?.let(::signedText), style, TextAlign.End, color = row.score?.let { scoreColor(it) })
        Cell(weights[5], row.totalPoints?.let(::formatPoints), style.copy(fontWeight = FontWeight.SemiBold), TextAlign.End)
        Cell(weights[6], row.totalScore?.let(::signedText), style.copy(fontWeight = FontWeight.SemiBold), TextAlign.End, color = row.totalScore?.let { scoreColor(it) })
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
