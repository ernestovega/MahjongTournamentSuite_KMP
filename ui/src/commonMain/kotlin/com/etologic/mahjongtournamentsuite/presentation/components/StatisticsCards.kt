package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RivalWeights
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.card_fold
import mahjongtournamentsuite.ui.generated.resources.card_unfold
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.more_information
import mahjongtournamentsuite.ui.generated.resources.rival_ahead_of_you
import mahjongtournamentsuite.ui.generated.resources.rival_compare
import mahjongtournamentsuite.ui.generated.resources.rival_deal_ins
import mahjongtournamentsuite.ui.generated.resources.rival_ema
import mahjongtournamentsuite.ui.generated.resources.rival_player
import mahjongtournamentsuite.ui.generated.resources.rival_self_draws
import mahjongtournamentsuite.ui.generated.resources.rival_shared_tables
import mahjongtournamentsuite.ui.generated.resources.rival_third_party_deal_ins
import mahjongtournamentsuite.ui.generated.resources.rival_wins_against_you
import mahjongtournamentsuite.ui.generated.resources.rivals_info
import mahjongtournamentsuite.ui.generated.resources.rivals_none
import mahjongtournamentsuite.ui.generated.resources.rivals_title
import mahjongtournamentsuite.ui.generated.resources.stat_chicken_hands_on_table
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_hands_without_dealin
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_on_table
import mahjongtournamentsuite.ui.generated.resources.stat_hands_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_highest_hand_score
import mahjongtournamentsuite.ui.generated.resources.stat_highest_penalty
import mahjongtournamentsuite.ui.generated.resources.stat_highest_table_score
import mahjongtournamentsuite.ui.generated.resources.stat_most_chicken_hands
import mahjongtournamentsuite.ui.generated.resources.stat_rank
import mahjongtournamentsuite.ui.generated.resources.stat_tables
import mahjongtournamentsuite.ui.generated.resources.stat_tables_won_row
import mahjongtournamentsuite.ui.generated.resources.stat_tournaments
import mahjongtournamentsuite.ui.generated.resources.stats_records
import mahjongtournamentsuite.ui.generated.resources.stats_standing
import mahjongtournamentsuite.ui.generated.resources.stats_totals
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The place, the points and the score of a player, or of a team, in one tournament. With [places], the place at the
 * end of each round out of [of], a small chart under the numbers shows how the place went round by round.
 */
@Composable
fun PlayerStandingCard(
    position: Int,
    points: Double,
    score: Int,
    modifier: Modifier = Modifier,
    places: Map<Int, Int>? = null,
    of: Int = 0,
) {
    StatCard(stringResource(Res.string.stats_standing), modifier) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_rank), position.toString())
            BigNumber(stringResource(Res.string.column_points), formatPoints(points))
            BigNumber(stringResource(Res.string.column_score), score.toString())
        }
        // Two places or more make a line. The rounds go from the first one to the last one with a place.
        if (places != null && places.size >= 2 && of > 1) {
            val last = places.keys.max()
            // Not wider than the numbers above it, so the line keeps its shape on a computer.
            PlaceChart(
                (1..last).map { places[it] },
                of,
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 560.dp).fillMaxWidth().padding(top = MtsTheme.spacing.sm),
                roundLabels = true,
            )
        }
    }
}

@Composable
fun StatCard(
    title: String?,
    modifier: Modifier = Modifier,
    /** When [onToggle] is set, the title is a button that folds the card. */
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    /** A help text. An info icon on the right of the title shows it in a tooltip. */
    info: String? = null,
    /** An optional line around the card, for cards in a list that must not merge. */
    border: BorderStroke? = null,
    /** A line under the title of a card that folds. It stays visible, and a tap on it folds the card too. */
    summary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        border = border,
        // The same soft shadow under every card. An elevation shadow grows with the distance from the top of the
        // window, so the cards lower on the screen had a bigger shadow than the cards at the top.
        modifier = modifier.dropShadow(shape, CardShadow),
        shape = shape,
        // The color is opaque, so the shadow does not show through the card.
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(MtsTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // A card that does not fold shows its title at the top of the card.
            if (title != null && onToggle == null) CardTitle(title, Modifier.padding(top = 4.dp), info)
            if (title != null && onToggle != null) {
                run {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CardTitle(title, info = info, bottomGap = if (summary != null) MtsTheme.spacing.xs else 12.dp)
                            summary?.invoke()
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(if (expanded) Res.string.card_fold else Res.string.card_unfold),
                        )
                    }
                }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

/** The title of a card: big, centered, with room below. The optional [info] icon sits on the right of the title. */
@Composable
internal fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    bottomGap: Dp = 12.dp,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = bottomGap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (info != null) InfoTip(info, Modifier.padding(start = 8.dp))
    }
}

/**
 * A small card with the label on top and the value under it. Put it in a [StatGrid].
 * With [info], a small info icon follows the label and shows the text in a tooltip.
 * The [suffix] follows the value in a smaller size, for example " of 40".
 */
@Composable
fun BigNumber(label: String, value: String, modifier: Modifier = Modifier, info: String? = null, suffix: String? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (info != null) InfoTip(info)
            }
            Spacer(Modifier.weight(1f))
            val valueStyle = MaterialTheme.typography.headlineMedium.inTextFont()
            Text(
                buildAnnotatedString {
                    append(value)
                    if (suffix != null) withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize)) { append(suffix) }
                },
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shows [text] in a tooltip on hover, or while a finger touches [content]. */
@Composable
internal fun HoverTip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AppTooltipBox(text, modifier) { content() }
}

/** A small info icon. The tooltip shows [text] on hover, or while a finger touches the icon. */
@Composable
internal fun InfoTip(text: String, modifier: Modifier = Modifier) {
    HoverTip(text, modifier) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.more_information),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A count with its share of the played hands. */
internal fun count(value: Int, of: Int): String = if (of > 0) "$value (${(value * 100.0 / of).roundToInt()}%)" else value.toString()

/**
 * The best values of the player: the highest scores, the most hands and chicken hands at one table, the longest runs
 * and, last, the biggest penalty.
 */
@Composable
internal fun RecordsCard(statistics: PlayerStatistics, modifier: Modifier) {
    val streaks = statistics.streaks
    StatCard(stringResource(Res.string.stats_records), modifier) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_highest_table_score), statistics.bestTableScore.text())
            BigNumber(stringResource(Res.string.stat_highest_hand_score), statistics.bestHand.text())
            BigNumber(stringResource(Res.string.stat_hands_won_on_table), statistics.mostHandsWonAtATable.text())
            BigNumber(stringResource(Res.string.stat_tables_won_row), streaks.tableWins.toString())
            BigNumber(stringResource(Res.string.stat_hands_won_row), streaks.handsWon.text())
            BigNumber(stringResource(Res.string.stat_hands_without_dealin), streaks.handsWithoutDealIn.text())
            BigNumber(stringResource(Res.string.stat_most_chicken_hands), if (statistics.tablesWithHands > 0) statistics.chickenHands.toString() else EMPTY)
            BigNumber(stringResource(Res.string.stat_chicken_hands_on_table), statistics.mostChickenHandsAtATable.text())
            BigNumber(stringResource(Res.string.stat_highest_penalty), statistics.highestPenalty.text())
        }
    }
}

/**
 * The opponents who did the most harm to the player. The rule and the weights are in `RivalWeights`.
 * Each rival takes a short block: the heat and the name, a button that compares the player with the rival, and the
 * six numbers in small. [onCompareRival] gets the EMA number of the rival. Without it, or for a rival without an EMA
 * number, there is no button.
 */
@Composable
internal fun RivalsCard(
    statistics: PlayerStatistics,
    names: Map<String, String>,
    modifier: Modifier,
    onCompareRival: ((String) -> Unit)?,
    design: Int = RivalsDesign,
) {
    val rivals = remember(statistics) { statistics.toughestRivals() }
    StatCard(
        stringResource(Res.string.rivals_title),
        modifier,
        info = stringResource(
            Res.string.rivals_info,
            RivalWeights.TABLE_BEHIND.toInt(),
            RivalWeights.DISCARD_LOSS.toInt(),
            RivalWeights.SELF_DRAW_LOSS.toInt(),
            RivalWeights.OTHERS_DISCARD_WIN.toInt(),
        ),
    ) {
        if (rivals.isEmpty()) {
            Text(
                stringResource(Res.string.rivals_none, RivalWeights.MIN_SHARED_TABLES),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatCard
        }
        val labels = listOf(
            stringResource(Res.string.rival_shared_tables),
            stringResource(Res.string.rival_ahead_of_you),
            stringResource(Res.string.rival_wins_against_you),
            stringResource(Res.string.rival_deal_ins),
            stringResource(Res.string.rival_self_draws),
            stringResource(Res.string.rival_third_party_deal_ins),
        )
        if (design == 2) RivalNumbersRow(labels, header = true)
        rivals.forEachIndexed { index, rival ->
            if (index > 0 || design == 2) HorizontalDivider()
            // A rival with an EMA number has statistics to compare with. A key with a hash has none.
            val compare = onCompareRival?.takeIf { '#' !in rival.opponent }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RivalHeading(rivalName(rival.opponent, names), heat = FLAMES - index, compare = compare?.let { { it(rival.opponent) } })
                val values = listOf(
                    rival.sharedTables,
                    rival.tablesBehind,
                    rival.discardLosses + rival.selfDrawLosses + rival.othersDiscardWins,
                    rival.discardLosses,
                    rival.selfDrawLosses,
                    rival.othersDiscardWins,
                ).map { it.toString() }
                if (design == 2) RivalNumbersRow(values, header = false) else RivalSmallNumbers(labels, values)
            }
        }
    }
}

/** The design of the rivals while Ernesto chooses: 1 small numbers with their labels, 2 a table with one header. */
internal var RivalsDesign = 1

/** Six small numbers, each with its label above: in one row on a wide card, in two rows of three on a narrow one. */
@Composable
private fun RivalSmallNumbers(labels: List<String>, values: List<String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 6 * 72.dp) 6 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.zip(values).chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                minLines = 2,
                            )
                            Text(value, style = MaterialTheme.typography.titleMedium.inTextFont(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** One row of the rivals table: the labels in the [header], or the numbers of one rival. */
@Composable
private fun RivalNumbersRow(cells: List<String>, header: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        cells.forEach { cell ->
            Text(
                cell,
                Modifier.weight(1f),
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium.inTextFont(),
                fontWeight = if (header) null else FontWeight.Bold,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The name of a rival with a row of flames before it like the heat of a dish, and the button that compares the
 * player with the rival after it. [heat] flames burn out of [FLAMES] for the toughest rival. The others have fewer.
 */
@Composable
private fun RivalHeading(name: String, heat: Int, compare: (() -> Unit)?) {
    val flame = MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            repeat(FLAMES) { index ->
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    Modifier.size(18.dp),
                    tint = if (index < heat) flame else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Text(
            name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.inTextFont(),
            fontWeight = FontWeight.SemiBold,
        )
        if (compare != null) {
            DebouncedTextButton(onClick = compare, contentPadding = PaddingValues(horizontal = 8.dp)) {
                ButtonText(stringResource(Res.string.rival_compare))
            }
        }
    }
}

/** The flames in the heat row of a rival. */
private const val FLAMES = 3

@Composable
private fun rivalName(key: String, names: Map<String, String>): String = names[key] ?: when {
    '#' in key -> stringResource(Res.string.rival_player, key.substringAfter('#'))
    else -> stringResource(Res.string.rival_ema, key)
}

@Composable
internal fun OverviewCard(statistics: PlayerStatistics, modifier: Modifier) {
    val hasHands = statistics.tablesWithHands > 0
    StatCard(stringResource(Res.string.stats_totals), modifier) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber(stringResource(Res.string.stat_tournaments), statistics.tournamentsPlayed.toString())
            BigNumber(stringResource(Res.string.stat_tables), statistics.tablesPlayed.toString())
            BigNumber(stringResource(Res.string.stat_hands), if (hasHands) statistics.handsPlayed.toString() else EMPTY)
        }
        if (hasHands) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            HandOutcomes(statistics, 300.dp)
        }
    }
}

/** The number of penalties with the penalty points in brackets, for example "2 (20)". */
internal fun penaltiesText(s: PlayerStatistics): String = if (s.tablesWithHands > 0) "${s.penaltyCount} (${s.penaltyPoints})" else EMPTY
