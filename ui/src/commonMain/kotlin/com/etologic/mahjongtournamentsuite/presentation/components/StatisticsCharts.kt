package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerLevel
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.STEADY_LEVEL_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.SkillScale
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsFixedColors
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.level_info
import mahjongtournamentsuite.ui.generated.resources.level_needs_hands
import mahjongtournamentsuite.ui.generated.resources.level_not_rated
import mahjongtournamentsuite.ui.generated.resources.level_provisional
import mahjongtournamentsuite.ui.generated.resources.level_range_between
import mahjongtournamentsuite.ui.generated.resources.level_range_or_more
import mahjongtournamentsuite.ui.generated.resources.level_range_under
import mahjongtournamentsuite.ui.generated.resources.level_skill_score_of
import mahjongtournamentsuite.ui.generated.resources.more_information
import mahjongtournamentsuite.ui.generated.resources.outcome_by_discard
import mahjongtournamentsuite.ui.generated.resources.outcome_by_discard_deal_in
import mahjongtournamentsuite.ui.generated.resources.outcome_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_drawn
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_lost
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_unit
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_won
import mahjongtournamentsuite.ui.generated.resources.outcome_lost_to_self_draw
import mahjongtournamentsuite.ui.generated.resources.outcome_neutral_hands
import mahjongtournamentsuite.ui.generated.resources.outcome_third_player_win
import mahjongtournamentsuite.ui.generated.resources.place_1st
import mahjongtournamentsuite.ui.generated.resources.place_2nd
import mahjongtournamentsuite.ui.generated.resources.place_3rd
import mahjongtournamentsuite.ui.generated.resources.place_4th
import mahjongtournamentsuite.ui.generated.resources.places_all
import mahjongtournamentsuite.ui.generated.resources.places_avg_place
import mahjongtournamentsuite.ui.generated.resources.places_last
import mahjongtournamentsuite.ui.generated.resources.skill_deal_in_rate
import mahjongtournamentsuite.ui.generated.resources.skill_deal_in_rate_info
import mahjongtournamentsuite.ui.generated.resources.skill_hand_value
import mahjongtournamentsuite.ui.generated.resources.skill_hand_value_info
import mahjongtournamentsuite.ui.generated.resources.skill_score_n
import mahjongtournamentsuite.ui.generated.resources.skill_self_draw
import mahjongtournamentsuite.ui.generated.resources.skill_self_draw_info
import mahjongtournamentsuite.ui.generated.resources.skill_sub_avg
import mahjongtournamentsuite.ui.generated.resources.skill_win_rate
import mahjongtournamentsuite.ui.generated.resources.skill_win_rate_info
import mahjongtournamentsuite.ui.generated.resources.skills_empty_message
import mahjongtournamentsuite.ui.generated.resources.skills_empty_title
import mahjongtournamentsuite.ui.generated.resources.stats_places
import mahjongtournamentsuite.ui.generated.resources.stats_skills
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The hands won, lost and neutral (drawn, or won by a third player with a discard) in the inner ring. The way of each win or loss in the outer ring. */
@Composable
internal fun HandOutcomes(s: PlayerStatistics, chartSize: Dp, modifier: Modifier = Modifier) {
    val won = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outline

    // [inOuter] parts are the sectors of the outer ring.
    class Part(val label: String, val value: Int, val color: Color, val sub: Boolean, val inOuter: Boolean = sub)
    val parts = listOf(
        Part(stringResource(Res.string.outcome_hands_won), s.handsWon, won, false),
        Part(stringResource(Res.string.outcome_by_discard), s.winsByDiscard, won.copy(alpha = 0.75f), true),
        Part(stringResource(Res.string.outcome_by_self_draw), s.selfDraws, won.copy(alpha = 0.45f), true),
        Part(stringResource(Res.string.outcome_hands_lost), s.dealIns + s.lostToSelfDraws, lost, false),
        Part(stringResource(Res.string.outcome_by_discard_deal_in), s.dealIns, lost.copy(alpha = 0.75f), true),
        Part(stringResource(Res.string.outcome_lost_to_self_draw), s.lostToSelfDraws, lost.copy(alpha = 0.5f), true),
        Part(stringResource(Res.string.outcome_neutral_hands), s.otherWins + s.drawnHands, neutral, false),
        Part(stringResource(Res.string.outcome_third_player_win), s.otherWins, neutral.copy(alpha = 0.75f), true),
        Part(stringResource(Res.string.outcome_hands_drawn), s.drawnHands, neutral.copy(alpha = 0.5f), true),
    )
    val inner = parts.filter { !it.sub }.map { Slice(it.value, it.color) }
    val outer = parts.filter { it.inOuter }.map { Slice(it.value, it.color) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val legend: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                parts.forEach { part ->
                    LegendRow(
                        part.color,
                        part.label,
                        count(part.value, s.handsPlayed),
                        Modifier.padding(start = if (part.sub) MtsTheme.spacing.lg else 0.dp),
                        bold = !part.sub,
                        // The ways of a win or a loss are details of the row above: normal weight and a smaller text.
                        detail = part.sub,
                    )
                }
            }
        }
        val chart: @Composable () -> Unit = {
            SectorChart(inner, outer, chartSize, 0.18f, s.handsPlayed.toString(), stringResource(Res.string.outcome_hands_unit))
        }
        if (maxWidth >= 520.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg, Alignment.CenterHorizontally),
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
internal fun LegendRow(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    /** A detail row has a smaller text and its value is not bold. */
    detail: Boolean = false,
) {
    val style = if (detail) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Box(Modifier.size(if (detail) 10.dp else 12.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = style, fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, style = style, fontWeight = if (detail) null else FontWeight.Bold)
    }
}

/** One sector of a [SectorChart]. */
internal class Slice(val value: Int, val color: Color)

/** Dark text on a light sector and white text on a dark sector. */
private fun textColorOn(background: Color): Color = if (background.luminance() > 0.5f) MtsFixedColors.inkOnLight else Color.White

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
                Text(centerValue, style = MaterialTheme.typography.titleLarge.inTextFont(), fontWeight = FontWeight.Bold)
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
internal class Skill(
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

@Composable
internal fun skillsOf(s: PlayerStatistics): List<Skill> = listOf(
    // Share of the played hands that the player won. A strong player wins about 4 hands in 10.
    Skill(
        stringResource(Res.string.skill_win_rate),
        s.winRate,
        SkillScale.WIN_RATE_FULL,
        s.winRate.percent(),
        stringResource(Res.string.skill_win_rate_info),
    ),
    // Share of the won hands that came from a self-draw. Half of the wins fills the axis.
    Skill(
        stringResource(Res.string.skill_self_draw),
        s.selfDrawRate,
        SkillScale.SELF_DRAW_RATE_FULL,
        s.selfDrawRate.percent(),
        stringResource(Res.string.skill_self_draw_info),
    ),
    // Share of the played hands that the player lost by discarding the winning tile. Lower is better.
    Skill(
        stringResource(Res.string.skill_deal_in_rate),
        s.dealInRate,
        SkillScale.DEAL_IN_RATE_FULL,
        s.dealInRate.percent(),
        stringResource(Res.string.skill_deal_in_rate_info),
    ),
    // Average points of the won hands, counted above the 8 point minimum.
    Skill(
        stringResource(Res.string.skill_hand_value),
        s.averageWinningScore,
        SkillScale.HAND_VALUE_FULL,
        s.averageWinningScore.decimal(),
        stringResource(Res.string.skill_hand_value_info),
        empty = SkillScale.HAND_VALUE_EMPTY,
        subLabel = stringResource(Res.string.skill_sub_avg),
    ),
)

private fun percentOf(value: Double): Int = (value * 100).roundToInt()

@Composable
private fun levelInfo(): String = stringResource(
    Res.string.level_info,
    percentOf(SkillScale.WIN_RATE_FULL),
    percentOf(SkillScale.SELF_DRAW_RATE_FULL),
    percentOf(SkillScale.DEAL_IN_RATE_FULL),
    SkillScale.HAND_VALUE_EMPTY.roundToInt(),
    SkillScale.HAND_VALUE_FULL.roundToInt(),
    MIN_HANDS_FOR_LEADERBOARD,
    STEADY_LEVEL_HANDS,
)

/** The score range of a level, for example "45 to 59". */
@Composable
private fun PlayerLevel.rangeText(): String {
    val next = PlayerLevel.entries.getOrNull(ordinal + 1)
    return when {
        ordinal == 0 && next != null -> stringResource(Res.string.level_range_under, next.minScore)
        next == null -> stringResource(Res.string.level_range_or_more, minScore)
        else -> stringResource(Res.string.level_range_between, minScore, next.minScore - 1)
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
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
            ) {
                LevelMedal(level, 28.dp)
                Text(
                    level.label(),
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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                if (level == null || score == null) {
                    Text(stringResource(Res.string.level_not_rated), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LevelRibbonBadge(level)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (score == null) {
                            stringResource(Res.string.level_needs_hands, MIN_HANDS_FOR_LEADERBOARD.toString())
                        } else {
                            stringResource(Res.string.level_skill_score_of, score.roundToInt())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoTip(levelInfo())
                }
                if (score != null && statistics.isLevelProvisional) {
                    Text(stringResource(Res.string.level_provisional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LevelLegend(level, Modifier.width(220.dp))
    }
}

/** The level badge above the skills chart. */
@Composable
internal fun SkillsBlock(statistics: PlayerStatistics, chartHeight: Dp, modifier: Modifier = Modifier, showLevel: Boolean = true) {
    if (statistics.tablesWithHands == 0) {
        AppEmptyState(
            icon = StatisticsIcon,
            title = stringResource(Res.string.skills_empty_title),
            message = stringResource(Res.string.skills_empty_message),
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
internal fun SkillsCard(statistics: PlayerStatistics, modifier: Modifier) {
    StatCard(stringResource(Res.string.stats_skills), modifier) { SkillsBlock(statistics, 300.dp) }
}

/**
 * The radar shapes are drawn on a canvas. The names and values are real views, so they can show a tooltip.
 * The skill at [highlight] stands out: its axis, its point and its name are in the main color.
 */
@Composable
internal fun RadarChart(skills: List<Skill>, modifier: Modifier = Modifier, highlight: Int? = null) {
    RadarChartOf(listOf(RadarSeries(skills, MaterialTheme.colorScheme.primary, Color.Unspecified)), modifier, highlight)
}

/**
 * One shape of the radar: the skills of one player. [fill] colors the shape and [text] the value of each skill.
 * All the series have the same skills in the same order.
 */
internal class RadarSeries(val skills: List<Skill>, val fill: Color, val text: Color)

/**
 * The radar with one shape for each of [series], one over the other. The names come from the first series. With more
 * than one series, every name has the value of each series under it, in the color of its series.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RadarChartOf(series: List<RadarSeries>, modifier: Modifier = Modifier, highlight: Int? = null) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val skills = series.first().skills
    val count = skills.size
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    // The layout makes the radar smaller when a name at a side does not fit next to it, and the canvas draws it that size.
    val laidOutRadius = remember { mutableFloatStateOf(0f) }
    Layout(
        modifier = modifier,
        content = {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = laidOutRadius.floatValue.takeIf { it > 0f } ?: radiusFor(size.width, size.height, density)
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
                repeat(count) { i ->
                    if (i == highlight) {
                        drawLine(primary, center, point(i, 1f), strokeWidth = 2.dp.toPx())
                    } else {
                        drawLine(grid, center, point(i, 1f), strokeWidth = 1.dp.toPx())
                    }
                }
                // With two shapes the fill is lighter, so the one under shows through the one over it.
                val fillAlpha = if (series.size > 1) 0.18f else 0.25f
                series.forEach { one ->
                    val shape = Path()
                    one.skills.forEachIndexed { i, skill ->
                        point(i, skill.fraction).let { if (i == 0) shape.moveTo(it.x, it.y) else shape.lineTo(it.x, it.y) }
                    }
                    shape.close()
                    drawPath(shape, one.fill.copy(alpha = fillAlpha))
                    drawPath(shape, one.fill, style = Stroke(width = 2.dp.toPx()))
                    one.skills.forEachIndexed { i, skill ->
                        drawCircle(one.fill, if (i == highlight) 6.dp.toPx() else 3.5.dp.toPx(), point(i, skill.fraction))
                    }
                }
            }
            skills.forEachIndexed { index, skill ->
                val highlighted = index == highlight
                val labelColor = if (highlighted) MtsTheme.colors.link else Color.Unspecified
                AppTooltipBox(skill.explanation) {
                    Column(
                        Modifier.padding(horizontal = MtsTheme.spacing.sm, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The info icon follows the last word of the name, so a name with a second line keeps the first line short.
                        val info: @Composable () -> Unit = {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = stringResource(Res.string.more_information),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val rowSpacing = Arrangement.spacedBy(4.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = rowSpacing) {
                            Text(
                                skill.label,
                                // The name gives room to the info icon, so a name in two lines keeps its icon.
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = labelColor,
                                fontWeight = if (highlighted) FontWeight.Bold else null,
                            )
                            if (skill.subLabel == null) info()
                        }
                        skill.subLabel?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = rowSpacing) {
                                Text(it, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = labelColor)
                                info()
                            }
                        }
                        if (series.size == 1) {
                            Text(skill.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = labelColor)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                                series.forEach { one ->
                                    Text(
                                        one.skills.getOrNull(index)?.text.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = one.text,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val canvas = measurables.first().measure(Constraints.fixed(width, height))
        val names = measurables.drop(1)
        // A name at a side (left or right) has only the room between the radar and the edge. Its longest word must fit
        // there, or it is cut in the middle or covers the radar, so the radar gets smaller for it. A name above or below
        // the radar may be wider.
        val atSide = names.indices.map { abs(cos(-PI / 2 + 2 * PI * it / count)) > 0.3 }
        val longestSideWord = names.indices.filter { atSide[it] }.maxOfOrNull { names[it].minIntrinsicWidth(height) } ?: 0
        val minRadius = 40f * density
        var radius = radiusFor(width.toFloat(), height.toFloat(), density)
        if (width / 2f - radius - gap < longestSideWord) radius = max(minRadius, width / 2f - gap - longestSideWord)
        laidOutRadius.floatValue = radius
        val sideRoom = max(0, (width / 2f - radius - gap).toInt())
        val labels = names.mapIndexed { index, name ->
            name.measure(Constraints(maxWidth = if (atSide[index]) max(sideRoom, longestSideWord).coerceAtMost(width / 2) else width * 3 / 5))
        }
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
private fun radiusFor(width: Float, height: Float, density: Float): Float = min(width / 2f - 90f * density, height / 2f - 44f * density).coerceAtLeast(40f * density)

@Composable
internal fun placeNames(): List<String> = listOf(
    stringResource(Res.string.place_1st),
    stringResource(Res.string.place_2nd),
    stringResource(Res.string.place_3rd),
    stringResource(Res.string.place_4th),
)
private val ChartSizes = listOf(10, 20, 50)

@Composable
internal fun PlacesCard(statistics: PlayerStatistics, tournaments: List<Tournament>, modifier: Modifier) {
    StatCard(stringResource(Res.string.stats_places), modifier) {
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
                        label = { Text(if (size == all) stringResource(Res.string.places_all, all) else stringResource(Res.string.places_last, size)) },
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
internal fun PlaceSectors(statistics: PlayerStatistics, size: Dp, modifier: Modifier = Modifier) {
    val counts = statistics.placeCounts
    val tables = statistics.tablesPlayed
    val placeNames = placeNames()
    val chart: @Composable () -> Unit = {
        SectorChart(
            counts.mapIndexed { index, value -> Slice(value, positionColor(index + 1)) },
            null,
            size,
            0.26f,
            centerValue = statistics.averagePosition.decimal(),
            centerLabel = stringResource(Res.string.places_avg_place),
        )
    }
    val legend: @Composable () -> Unit = {
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            counts.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(positionColor(index + 1), CircleShape))
                    if (index < 3) PositionTrophy(index + 1, size = 22.dp) else LastPlaceIcon(size = 22.dp)
                    Text(placeNames[index], Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium)
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
internal fun LevelMedalWithScore(statistics: PlayerStatistics, modifier: Modifier = Modifier) {
    if (!LocalFeatureFlags.current.levelsEnabled) return
    val level = statistics.level ?: return
    val score = statistics.skillScore ?: return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LevelRibbonBadge(level, medalSize = 64.dp)
        Text(
            stringResource(Res.string.skill_score_n, score.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
