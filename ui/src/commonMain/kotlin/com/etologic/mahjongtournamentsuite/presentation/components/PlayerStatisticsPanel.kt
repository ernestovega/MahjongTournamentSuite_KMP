package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.STEADY_LEVEL_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.RivalWeights
import com.etologic.mahjongtournamentsuite.domain.model.SkillScale
import kotlin.math.max
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Two cards side by side need at least this width. Narrower screens stack the cards. */
private val WideLayoutWidth = 760.dp

/**
 * The statistics of one player in big cards: [header], skills, places and totals.
 * On a wide screen the cards flow into several columns, like a staggered grid.
 */
@Composable
fun PlayerStatisticsPanel(
    statistics: PlayerStatistics,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    /** The tournaments of the results. They give the date and time of each table in the place history. */
    tournaments: List<Tournament> = emptyList(),
    /** Display name of every rival key. See `RivalTally.opponent`. */
    rivalNames: Map<String, String> = emptyMap(),
    /** Opens the statistics of a rival. The key is the EMA number. Without it the rival names are plain text. */
    onOpenRival: ((String) -> Unit)? = null,
    /** Gives the final places in some tournaments. With it, the records show the highest rank of the [tournaments]. */
    loadStandings: (suspend (List<String>) -> Map<String, Standing>)? = null,
) {
    if (statistics.tablesPlayed == 0) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            header?.invoke()
            AppEmptyState(
                icon = StatisticsIcon,
                title = "No statistics yet",
                message = "The statistics show when the first table is completed.",
            )
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= WideLayoutWidth
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (wide) {
                StaggeredColumns(minColumnWidth = 480.dp) {
                    header?.invoke()
                    SkillsCard(statistics, Modifier.fillMaxWidth())
                    PlacesCard(statistics, tournaments, Modifier.fillMaxWidth())
                    OverviewCard(statistics, Modifier.fillMaxWidth())
                    RecordsCard(statistics, tournaments, loadStandings, Modifier.fillMaxWidth())
                    RivalsCard(statistics, rivalNames, Modifier.fillMaxWidth(), onOpenRival)
                }
            } else {
                header?.invoke()
                SkillsCard(statistics, Modifier.fillMaxWidth())
                PlacesCard(statistics, tournaments, Modifier.fillMaxWidth())
                OverviewCard(statistics, Modifier.fillMaxWidth())
                RecordsCard(statistics, tournaments, loadStandings, Modifier.fillMaxWidth())
                RivalsCard(statistics, rivalNames, Modifier.fillMaxWidth(), onOpenRival)
            }
            if (statistics.tablesWithHands < statistics.tablesPlayed) {
                Text(
                    if (statistics.tablesWithHands == 0) {
                        "Hand statistics need tables with entered hands. No table here has them."
                    } else {
                        "Hand values use ${statistics.tablesWithHands} of ${statistics.tablesPlayed} tables."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun StatCard(
    title: String?,
    modifier: Modifier = Modifier,
    /** When [onToggle] is set, the title is a button that folds the card. */
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    /** A help text. An info icon on the right of the title shows it in a tooltip. */
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        // The color is opaque, so the shadow does not show through the card.
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(MaterialTheme.colorScheme.surface),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // A card that does not fold shows its title at the top of the card.
            if (title != null && onToggle == null) CardTitle(title, Modifier.padding(top = 4.dp), info)
            if (title != null && onToggle != null) {
                run {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                        CardTitle(title, Modifier.weight(1f), info)
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Fold" else "Unfold",
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
private fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
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
internal fun BigNumber(label: String, value: String, modifier: Modifier = Modifier, info: String? = null, suffix: String? = null) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
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
            val valueStyle = MaterialTheme.typography.headlineMedium
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

/** Shows [text] in a tooltip on hover or on a long press of [content]. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HoverTip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { AppPlainTooltip(Modifier.widthIn(max = 340.dp)) { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) { content() }
}

/** A small info icon. The tooltip shows [text] on hover or on a long press. */
@Composable
internal fun InfoTip(text: String, modifier: Modifier = Modifier) {
    HoverTip(text, modifier) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "More information",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A count with its share of the played hands. */
private fun count(value: Int, of: Int): String =
    if (of > 0) "$value (${(value * 100.0 / of).roundToInt()}%)" else value.toString()

/** The best values of the player: the highest scores, the most hands at one table, the biggest penalty and the longest runs. */
@Composable
private fun RecordsCard(
    statistics: PlayerStatistics,
    tournaments: List<Tournament>,
    loadStandings: (suspend (List<String>) -> Map<String, Standing>)?,
    modifier: Modifier,
) {
    val streaks = statistics.streaks
    // The highest rank is the best final place of the player in any of the tournaments. It loads after the card shows.
    val tournamentIds = remember(tournaments) { tournaments.map { it.id }.filter { it.isNotEmpty() }.distinct() }
    var standings by remember(tournamentIds) { mutableStateOf<Map<String, Standing>?>(null) }
    LaunchedEffect(tournamentIds) {
        if (loadStandings != null && tournamentIds.isNotEmpty()) standings = loadStandings(tournamentIds)
    }
    val best = standings?.values?.minWithOrNull(compareBy<Standing> { it.rank }.thenByDescending { it.of })
    StatCard("Records", modifier) {
        StatGrid(Modifier.fillMaxWidth()) {
            if (loadStandings != null) BigNumber("Highest rank", best?.rank?.toString() ?: EMPTY, suffix = best?.let { " of ${it.of}" })
            BigNumber("Highest table score", statistics.bestTableScore.text())
            BigNumber("Highest hand score", statistics.bestHand.text())
            BigNumber("Hands won on a table", statistics.mostHandsWonAtATable.text())
            BigNumber("Highest penalty got", statistics.highestPenalty.text())
            BigNumber("Tables won in a row", streaks.tableWins.toString())
            BigNumber("Hands won in a row", streaks.handsWon.text())
            BigNumber("Hands without a deal-in", streaks.handsWithoutDealIn.text())
        }
    }
}



/** The opponents who did the most harm to the player. The rule and the weights are in `RivalWeights`. */
@Composable
private fun RivalsCard(
    statistics: PlayerStatistics,
    names: Map<String, String>,
    modifier: Modifier,
    onOpenRival: ((String) -> Unit)?,
) {
    val rivals = remember(statistics) { statistics.toughestRivals() }
    StatCard(
        "Best Rivals",
        modifier,
        info = "Rivals are ordered by a score. A higher score means a tougher rival.\n" +
                "Everything counts only on tables where you played with that rival.\n" +
                "The score adds points for each of these numbers:\n" +
                "• ${RivalWeights.TABLE_BEHIND.toInt()} for each \"Ahead of you\": a table where the rival finished above you.\n" +
                "• ${RivalWeights.DISCARD_LOSS.toInt()} for each \"Deal-in\": a hand that you discarded and the rival won.\n" +
                "• ${RivalWeights.SELF_DRAW_LOSS.toInt()} for each \"Self-draw\": a hand that the rival won by self-draw.\n" +
                "• ${RivalWeights.OTHERS_DISCARD_WIN.toInt()} for each \"Third party deal-in\": a hand that the rival won on the discard of another player, not yours.\n" +
                "The sum is divided by the shared tables so a rival is not first only because you met often.",
    ) {
        if (rivals.isEmpty()) {
            Text(
                "No rival yet. It needs ${RivalWeights.MIN_SHARED_TABLES} shared tables.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rivals.forEachIndexed { index, rival ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A rival with an EMA number has a statistics page. A key with a hash has none.
                val open = onOpenRival?.takeIf { '#' !in rival.opponent }
                RivalHeading(rivalName(rival.opponent, names), heat = FLAMES - index, open = open?.let { { it(rival.opponent) } })
                StatGrid(Modifier.fillMaxWidth()) {
                    BigNumber("Shared tables", rival.sharedTables.toString())
                    BigNumber("Ahead of you", rival.tablesBehind.toString())
                    BigNumber("Wins against you", (rival.discardLosses + rival.selfDrawLosses + rival.othersDiscardWins).toString())
                    BigNumber("Deal-ins", rival.discardLosses.toString())
                    BigNumber("Self-draws", rival.selfDrawLosses.toString())
                    BigNumber("Third party deal-ins", rival.othersDiscardWins.toString())
                }
            }
        }
    }
}

/**
 * The name of a rival, centered, with a row of flames over it like the heat of a dish.
 * [heat] flames burn out of [FLAMES] for the toughest rival. The others have fewer.
 * The name is a link when [open] is set.
 */
@Composable
private fun RivalHeading(name: String, heat: Int, open: (() -> Unit)?) {
    val flame = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(FLAMES) { index ->
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    Modifier.size(24.dp),
                    tint = if (index < heat) flame else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Text(
            name,
            if (open != null) Modifier.clickable(onClick = open) else Modifier,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (open != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textDecoration = if (open != null) TextDecoration.Underline else null,
        )
    }
}

/** The flames in the heat row of a rival. */
private const val FLAMES = 3

private fun rivalName(key: String, names: Map<String, String>): String = names[key] ?: when {
    '#' in key -> "Player ${key.substringAfter('#')}"
    else -> "EMA $key"
}

@Composable
private fun OverviewCard(statistics: PlayerStatistics, modifier: Modifier) {
    val hasHands = statistics.tablesWithHands > 0
    StatCard("Totals", modifier) {
        StatGrid(Modifier.fillMaxWidth()) {
            BigNumber("Tournaments", statistics.tournamentsPlayed.toString())
            BigNumber("Tables", statistics.tablesPlayed.toString())
            BigNumber("Hands", if (hasHands) statistics.handsPlayed.toString() else EMPTY)
        }
        if (hasHands) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            HandOutcomes(statistics, 300.dp)
        }
    }
}

/** The number of penalties with the penalty points in brackets, for example "2 (20)". */
private fun penaltiesText(s: PlayerStatistics): String =
    if (s.tablesWithHands > 0) "${s.penaltyCount} (${s.penaltyPoints})" else EMPTY

/** The hands won, lost and neutral (drawn, or won by a third player with a discard) in the inner ring. The way of each win or loss in the outer ring. */
@Composable
private fun HandOutcomes(s: PlayerStatistics, chartSize: Dp, modifier: Modifier = Modifier) {
    val won = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outline
    // [inOuter] parts are the sectors of the outer ring.
    class Part(val label: String, val value: Int, val color: Color, val sub: Boolean, val inOuter: Boolean = sub)
    val parts = listOf(
        Part("Hands won", s.handsWon, won, false),
        Part("By discard", s.winsByDiscard, won.copy(alpha = 0.75f), true),
        Part("By self-draw", s.selfDraws, won.copy(alpha = 0.45f), true),
        Part("Hands lost", s.dealIns + s.lostToSelfDraws, lost, false),
        Part("By discard (deal-in)", s.dealIns, lost.copy(alpha = 0.75f), true),
        Part("By selfdraw", s.lostToSelfDraws, lost.copy(alpha = 0.5f), true),
        Part("Neutral hands", s.otherWins + s.drawnHands, neutral, false),
        Part("Third player win", s.otherWins, neutral.copy(alpha = 0.75f), true),
        Part("Hands drawn", s.drawnHands, neutral.copy(alpha = 0.5f), true),
    )
    val inner = parts.filter { !it.sub }.map { Slice(it.value, it.color) }
    val outer = parts.filter { it.inOuter }.map { Slice(it.value, it.color) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val legend: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                parts.forEach { part ->
                    LegendRow(
                        part.color, part.label, count(part.value, s.handsPlayed),
                        Modifier.padding(start = if (part.sub) 20.dp else 0.dp),
                        bold = !part.sub,
                    )
                }
            }
        }
        val chart: @Composable () -> Unit = {
            SectorChart(inner, outer, chartSize, 0.18f, s.handsPlayed.toString(), "hands")
        }
        if (maxWidth >= 520.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chart()
                legend(Modifier.width(IntrinsicSize.Max))
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chart()
                legend(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** One sector of a [SectorChart]. */
internal class Slice(val value: Int, val color: Color)

/** Dark text on a light sector and white text on a dark sector. */
private fun textColorOn(background: Color): Color = if (background.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White

/**
 * A ring chart. [inner] is the main ring. The optional [outer] ring splits the sectors of the inner ring in parts.
 * Each sector with enough room shows its count and its share. The middle has the [centerValue] and the [centerLabel].
 * [thickness] is the width of a ring as a part of the chart size.
 */
@Composable
internal fun SectorChart(
    inner: List<Slice>,
    outer: List<Slice>?,
    size: Dp,
    thickness: Float,
    centerValue: String? = null,
    centerLabel: String? = null,
) {
    val empty = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ringWidth = this.size.minDimension * thickness
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val gap = 1.5f
            fun ring(slices: List<Slice>, outerRadius: Float) {
                val radius = outerRadius - ringWidth / 2f
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                val total = slices.sumOf { it.value }
                if (total == 0) {
                    drawArc(empty, 0f, 360f, false, topLeft, arcSize, style = Stroke(ringWidth))
                    return
                }
                val visible = slices.count { it.value > 0 }
                var start = -90f
                slices.filter { it.value > 0 }.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    val drawn = if (visible > 1) (sweep - gap).coerceAtLeast(0.5f) else sweep
                    drawArc(slice.color, start + (sweep - drawn) / 2f, drawn, false, topLeft, arcSize, style = Stroke(ringWidth))
                    val percent = (100.0 * slice.value / total).roundToInt()
                    val text = if (ringWidth >= 34.dp.toPx()) "${slice.value}\n$percent%" else "$percent%"
                    val layout = measurer.measure(
                        text,
                        TextStyle(
                            color = textColorOn(slice.color.compositeOver(surface)),
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    val extent = max(layout.size.width, layout.size.height)
                    val arcLength = radius * (sweep * PI / 180.0).toFloat()
                    if (arcLength >= extent + 4.dp.toPx() && ringWidth >= extent) {
                        val mid = (start + sweep / 2f) * PI / 180.0
                        drawText(
                            layout,
                            topLeft = Offset(
                                center.x + (radius * cos(mid)).toFloat() - layout.size.width / 2f,
                                center.y + (radius * sin(mid)).toFloat() - layout.size.height / 2f,
                            ),
                        )
                    }
                    start += sweep
                }
            }
            val edge = this.size.minDimension / 2f
            if (outer == null) {
                ring(inner, edge)
            } else {
                ring(outer, edge)
                ring(inner, edge - ringWidth - 4.dp.toPx())
            }
        }
        if (centerValue != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (centerLabel != null) {
                    Text(centerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * One axis of the skills chart. The axis is empty at [empty] and full at [full].
 * [text] is the real value that the player sees. [explanation] is the text of the tooltip.
 * [subLabel] is a second line under the [label].
 */
private class Skill(
    val label: String,
    val value: Double?,
    val full: Double,
    val text: String,
    val explanation: String,
    val empty: Double = 0.0,
    val subLabel: String? = null,
) {
    val fraction: Float get() = (((value ?: empty) - empty) / (full - empty)).coerceIn(0.0, 1.0).toFloat()
}

private fun skillsOf(s: PlayerStatistics): List<Skill> = listOf(
    // Share of the played hands that the player won. A strong player wins about 4 hands in 10.
    Skill(
        "Win rate", s.winRate, SkillScale.WIN_RATE_FULL, s.winRate.percent(),
        "How often you win a hand.\nHands won divided by hands played.\nAxis full at 40%.",
    ),
    // Share of the won hands that came from a self-draw. Half of the wins fills the axis.
    Skill(
        "Self-draw", s.selfDrawRate, SkillScale.SELF_DRAW_RATE_FULL, s.selfDrawRate.percent(),
        "How often your wins come from drawing the winning tile yourself.\nSelf-draw wins divided by hands won.\n" +
            "Axis full at 50%.",
        subLabel = "rate",
    ),
    // Share of the played hands that the player lost by discarding the winning tile. Lower is better.
    Skill(
        "Deal-in rate", s.dealInRate, SkillScale.DEAL_IN_RATE_FULL, s.dealInRate.percent(),
        "How often you discard the tile that another player wins with.\nDeal-ins divided by hands played.\n" +
            "A lower value is better.\nAxis full at 25%.",
    ),
    // Average points of the won hands, counted above the 8 point minimum.
    Skill(
        "Hand value", s.averageWinningScore, SkillScale.HAND_VALUE_FULL, s.averageWinningScore.decimal(),
        "The average points of the hands you won.\nPoints of all winning hands divided by hands won.\n" +
            "Axis starts at 8 (min. hand value) and is full at 32.",
        empty = SkillScale.HAND_VALUE_EMPTY,
        subLabel = "avg.",
    ),
)

private fun percentOf(value: Double): Int = (value * 100).roundToInt()

private val LEVEL_INFO =
    "The skill score goes from 0 to 100. It is the mean of the four skills in the chart.\n" +
        "Each skill gives 0 to 100 points:\n" +
        "- Win rate: ${percentOf(SkillScale.WIN_RATE_FULL)}% or more gives 100.\n" +
        "- Self-draw rate: ${percentOf(SkillScale.SELF_DRAW_RATE_FULL)}% or more gives 100.\n" +
        "- Deal-in rate: 0% gives 100 and ${percentOf(SkillScale.DEAL_IN_RATE_FULL)}% or more gives 0.\n" +
        "- Hand value: ${SkillScale.HAND_VALUE_EMPTY.roundToInt()} gives 0 and ${SkillScale.HAND_VALUE_FULL.roundToInt()} or more gives 100.\n" +
        "The level follows the score. The list on the right shows the limits.\n" +
        "A level needs $MIN_HANDS_FOR_LEADERBOARD hands. It is provisional under $STEADY_LEVEL_HANDS hands."

/** The score range of a level, for example "45 to 59". */
private fun PlayerLevel.rangeText(): String {
    val next = PlayerLevel.entries.getOrNull(ordinal + 1)
    return when {
        ordinal == 0 && next != null -> "under ${next.minScore}"
        next == null -> "$minScore or more"
        else -> "$minScore to ${next.minScore - 1}"
    }
}

/** The five levels with their medals and score ranges. The level of the player has a colored background. */
@Composable
private fun LevelLegend(current: PlayerLevel?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlayerLevel.entries.reversed().forEach { level ->
            val selected = level == current
            Row(
                Modifier.fillMaxWidth()
                    .background(
                        if (selected) level.colors().main.copy(alpha = 0.18f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LevelMedal(level, 28.dp)
                Text(
                    level.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
                Text(
                    level.rangeText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The level medal with its ribbon and the skill score on the left. The legend of the levels is on the right. */
@Composable
private fun LevelBadge(statistics: PlayerStatistics) {
    val level = statistics.level
    val score = statistics.skillScore
    // A flow row puts the legend beside the medal when there is room, and under it when there is not.
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(190.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (level == null || score == null) {
                    Text("Level: not rated yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LevelRibbonBadge(level)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (score == null) "Needs $MIN_HANDS_FOR_LEADERBOARD hands"
                        else "Skill score ${score.roundToInt()} of 100",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoTip(LEVEL_INFO)
                }
                if (score != null && statistics.isLevelProvisional) {
                    Text("Provisional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LevelLegend(level, Modifier.width(220.dp))
    }
}

/** The level badge above the skills chart. */
@Composable
private fun SkillsBlock(statistics: PlayerStatistics, chartHeight: Dp, modifier: Modifier = Modifier, showLevel: Boolean = true) {
    if (statistics.tablesWithHands == 0) {
        AppEmptyState(
            icon = StatisticsIcon,
            title = "No skills yet",
            message = "Skills need tables with entered hands.",
            modifier = modifier,
            compact = true,
        )
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLevel && LocalFeatureFlags.current.levelsEnabled) LevelBadge(statistics)
        RadarChart(skillsOf(statistics), Modifier.fillMaxWidth().height(chartHeight))
    }
}

@Composable
private fun SkillsCard(statistics: PlayerStatistics, modifier: Modifier) {
    StatCard("Skills", modifier) { SkillsBlock(statistics, 300.dp) }
}

/** The radar shapes are drawn on a canvas. The names and values are real views, so they can show a tooltip. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RadarChart(skills: List<Skill>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val count = skills.size
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Layout(
        modifier = modifier,
        content = {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = radiusFor(size.width, size.height, density)
                fun point(index: Int, fraction: Float): Offset {
                    val angle = -PI / 2 + 2 * PI * index / count
                    return Offset(center.x + (radius * fraction * cos(angle)).toFloat(), center.y + (radius * fraction * sin(angle)).toFloat())
                }
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ring ->
                    val path = Path()
                    repeat(count) { i -> point(i, ring).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                    path.close()
                    drawPath(path, grid, style = Stroke(width = 1.dp.toPx()))
                }
                repeat(count) { i -> drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx()) }
                val shape = Path()
                skills.forEachIndexed { i, skill ->
                    point(i, skill.fraction).let { if (i == 0) shape.moveTo(it.x, it.y) else shape.lineTo(it.x, it.y) }
                }
                shape.close()
                drawPath(shape, primary.copy(alpha = 0.25f))
                drawPath(shape, primary, style = Stroke(width = 2.dp.toPx()))
                skills.forEachIndexed { i, skill -> drawCircle(primary, 3.5.dp.toPx(), point(i, skill.fraction)) }
            }
            skills.forEach { skill ->
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { AppPlainTooltip(Modifier.widthIn(max = 280.dp)) { Text(skill.explanation) } },
                    state = rememberTooltipState(),
                ) {
                    Column(
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The info icon follows the last word of the name, so a name with a second line keeps the first line short.
                        val info: @Composable () -> Unit = {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "More information",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val rowSpacing = Arrangement.spacedBy(4.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = rowSpacing) {
                            Text(skill.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            if (skill.subLabel == null) info()
                        }
                        skill.subLabel?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = rowSpacing) {
                                Text(it, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                info()
                            }
                        }
                        Text(skill.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val canvas = measurables.first().measure(Constraints.fixed(width, height))
        val radius = radiusFor(width.toFloat(), height.toFloat(), density)
        val labels = measurables.drop(1).map { it.measure(Constraints(maxWidth = width / 3)) }
        layout(width, height) {
            canvas.place(0, 0)
            labels.forEachIndexed { index, label ->
                val angle = -PI / 2 + 2 * PI * index / count
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val x = width / 2f + dx * (radius + gap) + dx * label.width / 2f - label.width / 2f
                val y = height / 2f + dy * (radius + gap) + dy * label.height / 2f - label.height / 2f
                label.place(
                    x.roundToInt().coerceIn(0, max(0, width - label.width)),
                    y.roundToInt().coerceIn(0, max(0, height - label.height)),
                )
            }
        }
    }
}

/** The radius leaves room for the names and values around the chart. */
private fun radiusFor(width: Float, height: Float, density: Float): Float =
    min(width / 2f - 90f * density, height / 2f - 44f * density).coerceAtLeast(40f * density)

private val PlaceNames = listOf("1st", "2nd", "3rd", "4th")
private val ChartSizes = listOf(10, 20, 50)

@Composable
private fun PlacesCard(statistics: PlayerStatistics, tournaments: List<Tournament>, modifier: Modifier) {
    StatCard("Places", modifier) {
        val all = statistics.tablesPlayed
        var chosen by remember { mutableStateOf<Int?>(null) }
        val shown = chosen?.coerceAtMost(all) ?: all
        val window = if (shown == all) statistics else PlayerStatistics(statistics.playerId, statistics.results.takeLast(shown))
        if (all > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (ChartSizes.filter { it < all } + all).forEach { size ->
                    FilterChip(
                        selected = size == shown,
                        onClick = { chosen = if (size == all) null else size },
                        label = { Text(if (size == all) "ALL($all)" else "Last $size") },
                    )
                }
            }
        }
        PlaceSectors(window, 200.dp)
        if (all > 1) PlaceHistory(window.results, tournaments)
    }
}

/**
 * A ring with one sector for each place and the count of each place beside it.
 * The average place is in the middle. The colors match the trophies.
 * The ring and the legend sit in the middle of the width.
 */
@Composable
private fun PlaceSectors(statistics: PlayerStatistics, size: Dp, modifier: Modifier = Modifier) {
    val counts = statistics.placeCounts
    val tables = statistics.tablesPlayed
    val chart: @Composable () -> Unit = {
        SectorChart(
            counts.mapIndexed { index, value -> Slice(value, positionColor(index + 1)) },
            null, size, 0.26f,
            centerValue = statistics.averagePosition.decimal(),
            centerLabel = "avg. place",
        )
    }
    val legend: @Composable () -> Unit = {
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            counts.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(positionColor(index + 1), CircleShape))
                    if (index < 3) PositionTrophy(index + 1, size = 22.dp) else LastPlaceIcon(size = 22.dp)
                    Text(PlaceNames[index], Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$value (${if (tables == 0) 0 else (value * 100.0 / tables).roundToInt()}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    // On a narrow screen the legend wraps below the ring.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chart()
        legend()
    }
}

/** The level medal with the skill score under it. It is small, so it fits in a row of numbers. */
@Composable
private fun LevelMedalWithScore(statistics: PlayerStatistics, modifier: Modifier = Modifier) {
    if (!LocalFeatureFlags.current.levelsEnabled) return
    val level = statistics.level ?: return
    val score = statistics.skillScore ?: return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LevelRibbonBadge(level, medalSize = 64.dp)
        Text(
            "Skill score ${score.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The tournaments of one person. Each tournament is a card with its numbers, skills, places and hands.
 * A card folds. The 5 latest tournaments start unfolded. The bar on top sticks while the screen scrolls.
 * It has a tournament dropdown that accepts typing, and a from and a to date.
 * The dropdown unfolds the chosen tournament and scrolls to it. A date range replaces the paging.
 */
@Composable
fun PlayerTournamentsSection(
    items: List<Pair<Tournament, PlayerStatistics>>,
    modifier: Modifier = Modifier,
    /** Gives the final places in some tournaments. The section asks only for the tournaments that it shows. */
    loadStandings: (suspend (List<String>) -> Map<String, Standing>)? = null,
) {
    val screen = LocalScreenScroll.current
    val scope = rememberCoroutineScope()
    // The newest tournament comes first. Only the newest ones show at the start, and the newest ones are open.
    val sorted = remember(items) {
        items.sortedWith(
            compareByDescending<Pair<Tournament, PlayerStatistics>> { it.first.eventStartDate ?: "" }.thenByDescending { it.first.id },
        )
    }
    val expanded = remember(sorted.map { it.first.id }) {
        mutableStateMapOf<String, Boolean>().apply { sorted.forEachIndexed { index, item -> put(item.first.id, index < INITIAL_TOURNAMENTS) } }
    }
    var shown by remember(sorted.map { it.first.id }) { mutableStateOf(INITIAL_TOURNAMENTS) }
    val standings = remember(sorted.map { it.first.id }) { mutableStateMapOf<String, Standing>() }
    val requested = remember(sorted.map { it.first.id }) { mutableSetOf<String>() }
    // A date range replaces the "show more" paging. An empty date means no limit on that side.
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val rangeActive = from.isNotEmpty() || to.isNotEmpty()
    val visible = remember(sorted, shown, from, to) {
        if (rangeActive) {
            sorted.filter { item ->
                val date = item.first.eventStartDate ?: ""
                (from.isEmpty() || date >= from) && (to.isEmpty() || (date.isNotEmpty() && date <= to))
            }
        } else {
            sorted.take(shown)
        }
    }
    // The final places load for the tournaments on screen. A new page asks only for its own tournaments.
    LaunchedEffect(visible.map { it.first.id }) {
        val missing = visible.map { it.first.id }.filter { it !in requested }
        if (missing.isEmpty() || loadStandings == null) return@LaunchedEffect
        requested += missing
        try {
            standings += loadStandings(missing)
        } catch (cancelled: CancellationException) {
            requested -= missing.toSet()
            throw cancelled
        }
    }
    val cardTops = remember { mutableMapOf<String, Float>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var sectionHeight by remember { mutableStateOf(0) }
    var placeholderTop by remember { mutableStateOf(0f) }
    var placeholderInSection by remember { mutableStateOf(0) }
    var barHeight by remember { mutableStateOf(0) }

    fun contentY(coordinates: LayoutCoordinates): Float =
        coordinates.positionInRoot().y - (screen?.viewportTop ?: 0f) + (screen?.state?.value ?: 0)

    LaunchedEffect(selectedId) {
        val index = sorted.indexOfFirst { it.first.id == selectedId }
        if (index < 0) return@LaunchedEffect
        val match = sorted[index].first
        // A tournament outside the date range, or not shown yet, appears when the user picks it.
        val date = match.eventStartDate ?: ""
        if (rangeActive && !((from.isEmpty() || date >= from) && (to.isEmpty() || date <= to))) {
            from = ""
            to = ""
        }
        if (index >= shown) shown = index + 1
        expanded[match.id] = true
        // Wait for the layout of the new or unfolded card, then scroll under the sticky bar.
        withFrameNanos { }
        withFrameNanos { }
        val top = cardTops[match.id] ?: return@LaunchedEffect
        screen?.state?.animateScrollTo((top - barHeight - 8).toInt().coerceAtLeast(0))
    }

    Column(modifier.onGloballyPositioned { sectionHeight = it.size.height }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.fillMaxWidth().zIndex(1f).onGloballyPositioned {
                placeholderTop = contentY(it)
                placeholderInSection = it.positionInParent().y.toInt()
            },
        ) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        val limit = (sectionHeight - placeholderInSection - barHeight).coerceAtLeast(0)
                        val pushed = ((screen?.state?.value ?: 0) - placeholderTop).toInt()
                        IntOffset(0, if (screen == null) 0 else pushed.coerceIn(0, limit))
                    }
                    .onSizeChanged { barHeight = it.height }
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val options = remember(sorted) { sorted.map { it.first.id to it.first.name } }
                SearchableDropdownField(
                    "Tournament", options, selectedId, { selectedId = it },
                    Modifier.widthIn(min = 240.dp).weight(1f),
                )
                // The two dates stay in one line. On a narrow screen they share the full width.
                Row(Modifier.widthIn(max = 392.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("From", from, { from = it }, Modifier.weight(1f))
                    DateField("To", to, { to = it }, Modifier.weight(1f))
                }
                val anyOpen = expanded.values.any { it }
                TextButton(onClick = { visible.forEach { expanded[it.first.id] = !anyOpen } }) {
                    Icon(if (anyOpen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (anyOpen) "Fold all" else "Unfold all", maxLines = 1, softWrap = false)
                }
            }
        }
        // The same button as the one on top. It is also at the end of the list, so the user does not scroll back up.
        val foldAllButton: @Composable () -> Unit = {
            val anyVisibleOpen = visible.any { expanded[it.first.id] == true }
            OutlinedButton(onClick = { visible.forEach { expanded[it.first.id] = !anyVisibleOpen } }) {
                Icon(if (anyVisibleOpen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (anyVisibleOpen) "Fold all" else "Unfold all", maxLines = 1, softWrap = false)
            }
        }
        visible.forEach { (tournament, stats) ->
            val open = expanded[tournament.id] ?: false
            StatCard(
                tournament.name,
                Modifier.fillMaxWidth().onGloballyPositioned { cardTops[tournament.id] = contentY(it) },
                expanded = open,
                onToggle = { expanded[tournament.id] = !open },
            ) {
                val hasHands = stats.tablesWithHands > 0
                StatGrid(Modifier.fillMaxWidth()) {
                    LevelMedalWithScore(stats)
                    standings[tournament.id]?.let { BigNumber("Rank", it.rank.toString(), suffix = " of ${it.of}") }
                    BigNumber("Tables", stats.tablesPlayed.toString())
                    BigNumber("Hands", if (hasHands) stats.handsPlayed.toString() else EMPTY)
                    BigNumber("Best table score", stats.bestTableScore.text())
                    BigNumber("Best hand", stats.bestHand.text())
                    if (tournament.countChickenHands && hasHands) BigNumber("Chicken hands", stats.chickenHands.toString())
                    BigNumber("Penalties", penaltiesText(stats), info = "Number of penalties (total points lost)")
                }
                // The skills, the places and the hands go in one row when there is room. With less room they move to the next row.
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    val blocks = buildList<@Composable (Modifier) -> Unit> {
                        add { m -> TitledBlock("Skills", m) { SkillsBlock(stats, 240.dp, Modifier.fillMaxWidth(), showLevel = false) } }
                        add { m -> TitledBlock("Places", m) { PlaceSectors(stats, 170.dp, Modifier.fillMaxWidth()) } }
                        if (hasHands) add { m -> TitledBlock("Hands", m) { HandOutcomes(stats, 220.dp, Modifier.fillMaxWidth()) } }
                    }
                    val gap = 24.dp
                    val columns = ((maxWidth + gap) / (TOURNAMENT_BLOCK_MIN_WIDTH + gap)).toInt().coerceIn(1, blocks.size)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        blocks.chunked(columns).forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                                // A row with a single block, as the last row, uses the full width.
                                row.forEach { block -> block(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        if (rangeActive) {
            Text(
                if (visible.isEmpty()) "No tournament in this date range." else "${visible.size} of ${sorted.size} tournaments in this date range.",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (visible.size > 1) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { foldAllButton() }
        } else ShowMoreTournaments(
            shown = visible.size,
            total = sorted.size,
            onShowMore = { shown = (shown + TOURNAMENTS_STEP).coerceAtMost(sorted.size) },
            onShowAll = { shown = sorted.size },
            foldAll = if (visible.size > 1) foldAllButton else null,
        )
    }
}

/** The number of tournaments that show at the start, and the number that one tap of "show more" adds. */
private const val INITIAL_TOURNAMENTS = 5

/** A chart block of a tournament card with its title on top. The titles of a row line up at the top. */
@Composable
private fun TitledBlock(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A little smaller than the titles of the cards of "Me".
        CardTitle(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

/** The least width of a chart block in a tournament card. A narrower space moves the block to the next row. */
private val TOURNAMENT_BLOCK_MIN_WIDTH = 340.dp

private const val TOURNAMENTS_STEP = 5

/** The way to load more tournaments: by quantity or by date. Nothing shows when all tournaments are visible. */
@Composable
private fun ShowMoreTournaments(
    shown: Int,
    total: Int,
    onShowMore: () -> Unit,
    onShowAll: () -> Unit,
    /** The button that folds or unfolds all the cards. It sits with the buttons that show more tournaments. */
    foldAll: (@Composable () -> Unit)? = null,
) {
    if (total <= INITIAL_TOURNAMENTS && foldAll == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (total > INITIAL_TOURNAMENTS) {
            Text(
                "Showing $shown of $total tournaments. The newest tournaments come first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (shown < total) {
                OutlinedButton(onClick = onShowMore) { Text("Show $TOURNAMENTS_STEP more") }
                OutlinedButton(onClick = onShowAll) { Text("Show all") }
            }
            foldAll?.invoke()
        }
    }
}

/** Date and time of the table, in two lines. The date comes from the schedule of the round. */
private fun tableTime(result: TableResult, tournaments: List<Tournament>): String {
    val tournament = tournaments.firstOrNull { it.id == result.tournamentId } ?: tournaments.singleOrNull()
    val schedule = tournament?.roundSchedules?.firstOrNull { it.roundId == result.roundId }
    val date = (schedule?.date ?: tournament?.eventStartDate)?.split("-")
        ?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0].takeLast(2)}" } ?: EMPTY
    return "$date\n${schedule?.startTime ?: EMPTY}"
}

/** The short name of the tournament, the round and the table, for example "EMA Open - Round 2 - Table 5". */
private fun tableLabel(result: TableResult, tournaments: List<Tournament>): String {
    val tournament = tournaments.firstOrNull { it.id == result.tournamentId } ?: tournaments.singleOrNull()
    val name = tournament?.let { it.shortName.ifBlank { it.name } }.orEmpty()
    return listOf(name, "Round ${result.roundId}", "Table ${result.tableId}").filter { it.isNotBlank() }.joinToString(" - ")
}

@Composable
private fun PlaceHistory(results: List<TableResult>, tournaments: List<Tournament>) {
    val places = results.map { it.position }
    val times = results.map { tableTime(it, tournaments) }
    val tips = results.mapIndexed { index, result -> tableLabel(result, tournaments) + "\n" + times[index].replace('\n', ' ') }
    Text("Place in the last ${places.size} tables", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
    val pointColors = (1..4).map { positionColor(it) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        textAlign = TextAlign.Center,
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
    Canvas(Modifier.fillMaxSize()) {
        val left = 36.dp.toPx()
        val pad = 12.dp.toPx()
        val bottom = 44.dp.toPx()
        val width = size.width - left - pad * 2
        val height = size.height - pad - bottom
        fun y(place: Int) = pad + height * (place - 1) / 3f
        fun x(step: Int) = left + if (places.size == 1) width / 2f else width * step / (places.size - 1)
        (1..4).forEach { place ->
            drawLine(grid, Offset(left, y(place)), Offset(size.width - pad, y(place)), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(PlaceNames[place - 1], labelStyle)
            drawText(layout, topLeft = Offset(0f, y(place) - layout.size.height / 2f))
        }
        val path = Path()
        places.forEachIndexed { step, place ->
            if (step == 0) path.moveTo(x(step), y(place)) else path.lineTo(x(step), y(place))
        }
        drawPath(path, line.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        places.forEachIndexed { step, place ->
            drawCircle(pointColors[place - 1], 5.dp.toPx(), Offset(x(step), y(place)))
        }
        // Show a date only when it has room. Crowded charts show every second, third, ... date.
        val spacing = if (places.size > 1) width / (places.size - 1) else width
        val stride = kotlin.math.ceil(54.dp.toPx() / spacing).toInt().coerceAtLeast(1)
        times.forEachIndexed { step, text ->
            if (step % stride != 0 && step != places.lastIndex) return@forEachIndexed
            if (step != places.lastIndex && places.lastIndex - step < stride) return@forEachIndexed
            val layout = measurer.measure(text, labelStyle)
            val labelX = (x(step) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(labelX, size.height - bottom + 10.dp.toPx()))
        }
    }
    // Each dot has a transparent box over it. The box shows the tournament, round, table, date and time in a tooltip.
    val plotWidth = maxWidth - 36.dp - 24.dp
    val plotHeight = maxHeight - 12.dp - 44.dp
    places.forEachIndexed { step, place ->
        val centerX = 36.dp + if (places.size == 1) plotWidth / 2f else plotWidth * step / (places.size - 1)
        val centerY = 12.dp + plotHeight * (place - 1) / 3f
        // The offset sits on a wrapper. The tooltip box must be the 24 dp box itself, or its anchor is not the dot.
        Box(Modifier.offset(centerX - 12.dp, centerY - 12.dp).size(24.dp)) {
            HoverTip(tips[step], Modifier.size(24.dp)) { Box(Modifier.size(24.dp)) }
        }
    }
    }
}
