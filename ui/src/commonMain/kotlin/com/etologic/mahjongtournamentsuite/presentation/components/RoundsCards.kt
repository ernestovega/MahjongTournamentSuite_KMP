package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRound
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSlice
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundTotals
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.outcome_by_discard
import mahjongtournamentsuite.ui.generated.resources.outcome_by_discard_deal_in
import mahjongtournamentsuite.ui.generated.resources.outcome_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_drawn
import mahjongtournamentsuite.ui.generated.resources.outcome_lost_to_self_draw
import mahjongtournamentsuite.ui.generated.resources.outcome_third_player_win
import mahjongtournamentsuite.ui.generated.resources.round_short
import mahjongtournamentsuite.ui.generated.resources.rounds_avg_hand_value
import mahjongtournamentsuite.ui.generated.resources.rounds_cumulative_note
import mahjongtournamentsuite.ui.generated.resources.rounds_empty_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_hands_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_hands_of_round
import mahjongtournamentsuite.ui.generated.resources.rounds_place_points_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_points_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_round_n
import mahjongtournamentsuite.ui.generated.resources.rounds_team_points_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_title
import mahjongtournamentsuite.ui.generated.resources.rounds_view_place
import mahjongtournamentsuite.ui.generated.resources.skill_deal_in_rate
import mahjongtournamentsuite.ui.generated.resources.skill_self_draw
import mahjongtournamentsuite.ui.generated.resources.skill_win_rate
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_highest_hand_score
import mahjongtournamentsuite.ui.generated.resources.stat_highest_table_score
import mahjongtournamentsuite.ui.generated.resources.stats_hand_stats_missing
import mahjongtournamentsuite.ui.generated.resources.stats_records
import mahjongtournamentsuite.ui.generated.resources.stats_skills
import mahjongtournamentsuite.ui.generated.resources.stats_totals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/** The views of the "By round" cards. Each card shows only the views that it has. */
enum class RoundView(val title: StringResource) {
    PLACE(Res.string.rounds_view_place),
    POINTS(Res.string.column_score),
    HANDS(Res.string.stat_hands),
    SKILLS(Res.string.stats_skills),
    TOTALS(Res.string.stats_totals),
    RECORDS(Res.string.stats_records),
}

/** The test tag of the section of a view of the "By round" cards. */
fun roundViewTag(view: RoundView): String = "round-view-" + view.name.lowercase()

/**
 * The frame of every "By round" card: the title, then every view one under the other, each one with its name. All the
 * views share the selected round, so a tap on a round in one chart selects it in all of them.
 */
@Composable
private fun RoundsCardFrame(
    views: List<RoundView>,
    modifier: Modifier,
    content: @Composable (RoundView) -> Unit,
) {
    StatCard(stringResource(Res.string.rounds_title), modifier) {
        views.forEachIndexed { index, view ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = MtsTheme.spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().testTag(roundViewTag(view)), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
                Text(stringResource(view.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                content(view)
            }
        }
    }
}

/** The round names under the charts, such as "R1". */
@Composable
private fun roundNames(roundIds: List<Int>): List<String> = roundIds.map { stringResource(Res.string.round_short, it) }

/** The last round with results, the one that a card selects first. */
private fun lastPlayed(rounds: List<PlayerRound>): Int = rounds.indexOfLast { !it.isEmpty }.coerceAtLeast(0)

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

/** One skill of the small charts: how to read it from the statistics, its scale and how to write it. */
private class SkillLine(
    val title: StringResource,
    val read: (PlayerStatistics) -> Double?,
    val minValue: Double,
    val format: (Double) -> String,
)

private val playerSkills = listOf(
    SkillLine(Res.string.skill_win_rate, { it.winRate }, 0.0, ::percent),
    SkillLine(Res.string.skill_self_draw, { it.selfDrawRate }, 0.0, ::percent),
    SkillLine(Res.string.skill_deal_in_rate, { it.dealInRate }, 0.0, ::percent),
    SkillLine(Res.string.rounds_avg_hand_value, { it.averageWinningScore }, 8.0, { it.roundToInt().toString() }),
)

/**
 * One small chart for each skill. The line is the value with every table up to each round, so it moves less. The
 * dots are the value of each round alone. The note under the charts says so.
 */
@Composable
private fun SkillCharts(
    names: List<String>,
    lines: List<Triple<StringResource, (Int) -> Double?, (Int) -> Double?>>,
    minValues: List<Double>,
    formats: List<(Double) -> String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    /** True writes under the charts that the lines add up the rounds and the dots are each round alone. */
    note: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
        lines.forEachIndexed { index, (title, cumulative, single) ->
            val total = names.indices.map(cumulative)
            val alone = names.indices.map(single)
            val top = (total + alone).filterNotNull().maxOrNull() ?: 1.0
            val shown = total.getOrNull(selected)
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown?.let(formats[index]) ?: "—", style = MaterialTheme.typography.labelLarge.inTextFont(), fontWeight = FontWeight.Bold)
            }
            RoundLineChart(
                names = names,
                series = listOf(LineSeries(total, color), LineSeries(alone, color.copy(alpha = 0.4f), dotsOnly = true)),
                minValue = minValues[index],
                maxValue = max(top * 1.15, minValues[index] + 0.01),
                selected = selected,
                onSelect = onSelect,
                format = formats[index],
                height = 110.dp,
                allValues = true,
            )
        }
        if (note) {
            Text(
                stringResource(Res.string.rounds_cumulative_note),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayerSkillCharts(names: List<String>, rounds: List<PlayerRound>, selected: Int, onSelect: (Int) -> Unit) = SkillCharts(
    names = names,
    lines = playerSkills.map { skill ->
        Triple(skill.title, { i: Int -> rounds[i].takeUnless { it.isEmpty }?.cumulative?.let(skill.read) }, { i: Int -> skill.read(rounds[i].round) })
    },
    minValues = playerSkills.map { it.minValue },
    formats = playerSkills.map { it.format },
    selected = selected,
    onSelect = onSelect,
)

/** The place in the ranking at the end of each round. The first place is at the top. */
@Composable
private fun PlaceView(
    names: List<String>,
    roundIds: List<Int>,
    places: Map<Int, Int>,
    of: Int,
    /** The table points of each round, or null for a round without tables. */
    points: List<Double?>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val values = roundIds.mapIndexed { index, id -> if (points[index] != null) places[id]?.toDouble() else null }
    val place = values.getOrNull(selected)
    val tip = if (place == null) {
        stringResource(Res.string.rounds_empty_detail, roundIds[selected])
    } else {
        stringResource(Res.string.rounds_place_points_detail, roundIds[selected], place.roundToInt(), of, formatPoints(points[selected] ?: 0.0))
    }
    RoundLineChart(
        names = names,
        series = listOf(LineSeries(values, MaterialTheme.colorScheme.primary)),
        minValue = 1.0,
        maxValue = of.coerceAtLeast(2).toDouble(),
        selected = selected,
        onSelect = onSelect,
        inverted = true,
        format = { "#${it.roundToInt()}" },
        tooltip = tip,
    )
}

/**
 * The "By round" card of a player in one tournament. [places] gives the place in the ranking at the end of each round,
 * out of [playerCount]. Without it the card has no ranking view.
 */
@Composable
fun PlayerRoundsCard(
    rounds: List<PlayerRound>,
    places: Map<Int, Int>?,
    playerCount: Int,
    modifier: Modifier = Modifier,
) {
    if (rounds.isEmpty()) return
    val views = listOfNotNull(RoundView.PLACE.takeIf { !places.isNullOrEmpty() }, RoundView.POINTS, RoundView.SKILLS, RoundView.HANDS)
    var selected by remember(rounds.size) { mutableStateOf(lastPlayed(rounds)) }
    val names = roundNames(rounds.map { it.roundId })
    RoundsCardFrame(views, modifier) { view ->
        when (view) {
            RoundView.PLACE -> PlaceView(names, rounds.map { it.roundId }, places.orEmpty(), playerCount, rounds.map { round -> round.round.totalPoints.takeIf { !round.isEmpty } }, selected) { selected = it }
            RoundView.POINTS -> {
                val round = rounds[selected]
                val result = round.round.results.firstOrNull()
                val tip = if (result == null) {
                    stringResource(Res.string.rounds_empty_detail, round.roundId)
                } else {
                    stringResource(Res.string.rounds_points_detail, round.roundId, result.tableId, result.position, formatPoints(result.points), result.score)
                }
                RoundBarChart(
                    names = names,
                    bars = rounds.map { round -> round.round.results.map { BarPart(it.points, positionColor(it.position)) } },
                    labels = rounds.map { round -> round.round.results.takeIf { it.isNotEmpty() }?.sumOf { it.score }?.toString() },
                    selected = selected,
                    onSelect = { selected = it },
                    maxValue = max(4.0, rounds.maxOf { it.round.totalPoints }),
                    tooltip = tip,
                )
            }
            RoundView.SKILLS -> PlayerSkillCharts(names, rounds, selected) { selected = it }
            else -> PlayerHandsView(names, rounds, selected) { selected = it }
        }
    }
}

/**
 * The hands of the player, or of all the players of a team, in every round, one small ring for each round, all in a row that scrolls sideways when it
 * does not fit: won by discard or by self-draw, deal-ins, lost to a self-draw, won by a third player, and drawn.
 * A tap on a ring selects its round, and the legend under the row gives the counts and the shares of that round.
 */
@Composable
private fun PlayerHandsView(names: List<String>, rounds: List<PlayerRound>, selected: Int, onSelect: (Int) -> Unit) {
    val won = MaterialTheme.colorScheme.primary
    val lost = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outline
    fun parts(s: PlayerStatistics) = listOf(
        Triple(Res.string.outcome_by_discard, s.winsByDiscard, won.copy(alpha = 0.85f)),
        Triple(Res.string.outcome_by_self_draw, s.selfDraws, won.copy(alpha = 0.5f)),
        Triple(Res.string.outcome_by_discard_deal_in, s.dealIns, lost.copy(alpha = 0.85f)),
        Triple(Res.string.outcome_lost_to_self_draw, s.lostToSelfDraws, lost.copy(alpha = 0.5f)),
        Triple(Res.string.outcome_third_player_win, s.otherWins, neutral.copy(alpha = 0.75f)),
        Triple(Res.string.outcome_hands_drawn, s.drawnHands, neutral.copy(alpha = 0.4f)),
    )
    val scroll = rememberScrollState()
    // Like the charts, the row starts at the end, where the last rounds are.
    LaunchedEffect(rounds.size, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        rounds.forEachIndexed { index, round ->
            val highlight = if (index == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
            Column(
                Modifier.clip(MaterialTheme.shapes.medium).background(highlight).clickable { onSelect(index) }.padding(MtsTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
            ) {
                val s = round.round
                SectorChart(
                    inner = parts(s).map { Slice(it.second, it.third) },
                    outer = null,
                    size = 56.dp,
                    thickness = 0.3f,
                )
                Text(
                    names[index] + (s.handsPlayed.takeIf { it > 0 }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == selected) FontWeight.Bold else null,
                )
            }
        }
    }
    val round = rounds[selected]
    val s = round.round
    if (round.isEmpty || s.handsPlayed == 0) {
        Text(
            if (round.isEmpty) stringResource(Res.string.rounds_empty_detail, round.roundId) else stringResource(Res.string.stats_hand_stats_missing),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    Text(
        stringResource(Res.string.rounds_hands_of_round, round.roundId, s.handsPlayed),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
    parts(s).forEach { (label, value, color) ->
        LegendRow(color, stringResource(label), "$value · ${(100.0 * value / s.handsPlayed).roundToInt()}%")
    }
}

/** The colors of the players of a team in the stacked bars. */
@Composable
private fun memberColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MtsTheme.colors.info,
    MtsTheme.colors.warning,
    MaterialTheme.colorScheme.tertiary,
)

/**
 * The "By round" card of a team. [rounds] has the tables of all its players. [members] gives each player with its own
 * rounds. [places] gives the place of the team at the end of each round, out of [teamCount].
 */
@Composable
fun TeamRoundsCard(
    rounds: List<PlayerRound>,
    members: List<Pair<String, List<PlayerRound>>>,
    places: Map<Int, Int>?,
    teamCount: Int,
    modifier: Modifier = Modifier,
) {
    if (rounds.isEmpty()) return
    val views = listOfNotNull(RoundView.PLACE.takeIf { !places.isNullOrEmpty() }, RoundView.POINTS, RoundView.SKILLS, RoundView.HANDS)
    var selected by remember(rounds.size) { mutableStateOf(lastPlayed(rounds)) }
    val names = roundNames(rounds.map { it.roundId })
    val colors = memberColors()
    RoundsCardFrame(views, modifier) { view ->
        when (view) {
            RoundView.PLACE -> PlaceView(names, rounds.map { it.roundId }, places.orEmpty(), teamCount, rounds.map { round -> round.round.totalPoints.takeIf { !round.isEmpty } }, selected) { selected = it }
            RoundView.POINTS -> {
                val round = rounds[selected]
                val tip = if (round.isEmpty) {
                    stringResource(Res.string.rounds_empty_detail, round.roundId)
                } else {
                    stringResource(Res.string.rounds_team_points_detail, round.roundId, formatPoints(round.round.totalPoints))
                }
                RoundBarChart(
                    names = names,
                    bars = rounds.indices.map { index ->
                        members.mapIndexed { m, (_, memberRounds) -> BarPart(memberRounds.getOrNull(index)?.round?.totalPoints ?: 0.0, colors[m % colors.size]) }
                    },
                    labels = rounds.map { round -> round.round.results.takeIf { it.isNotEmpty() }?.let { formatPoints(round.round.totalPoints) } },
                    selected = selected,
                    onSelect = { selected = it },
                    tooltip = tip,
                )
                members.forEachIndexed { m, (name, memberRounds) ->
                    LegendRow(colors[m % colors.size], name, memberRounds.getOrNull(selected)?.round?.takeIf { it.results.isNotEmpty() }?.let { formatPoints(it.totalPoints) } ?: "—")
                }
            }
            RoundView.SKILLS -> PlayerSkillCharts(names, rounds, selected) { selected = it }
            else -> PlayerHandsView(names, rounds, selected) { selected = it }
        }
    }
}

/**
 * The "By round" card of a tournament: the hands of each round, the skills of the tournament round by round, and the
 * records of each round. [names] gives the name of each slot. A tap on a name of a record opens that player.
 */
@Composable
fun TournamentRoundsCard(
    rounds: List<TournamentRoundSlice>,
    names: Map<Int, String>,
    modifier: Modifier = Modifier,
    onOpenPlayer: ((Int) -> Unit)? = null,
) {
    if (rounds.isEmpty()) return
    var selected by remember(rounds.size) { mutableStateOf(rounds.indexOfLast { it.round.tablesCompleted > 0 }.coerceAtLeast(0)) }
    val roundNames = roundNames(rounds.map { it.round.roundId })
    RoundsCardFrame(listOf(RoundView.HANDS, RoundView.SKILLS, RoundView.RECORDS), modifier) { view ->
        when (view) {
            RoundView.HANDS -> {
                val won = MaterialTheme.colorScheme.primary
                val neutral = MaterialTheme.colorScheme.outline
                val parts = listOf(
                    Res.string.outcome_by_discard to won.copy(alpha = 0.75f),
                    Res.string.outcome_by_self_draw to won.copy(alpha = 0.45f),
                    Res.string.outcome_hands_drawn to neutral.copy(alpha = 0.5f),
                )
                fun values(r: TournamentRoundTotals) = listOf(r.winsByDiscard, r.selfDraws, r.drawnHands)
                val round = rounds[selected].round
                val tip = stringResource(
                    Res.string.rounds_hands_detail,
                    round.roundId,
                    round.tablesCompleted,
                    round.tablesTotal,
                    round.handsPlayed,
                    round.drawnHands,
                    round.penaltyCount,
                )
                RoundBarChart(
                    names = roundNames,
                    bars = rounds.map { slice -> values(slice.round).zip(parts) { value, part -> BarPart(value.toDouble(), part.second) } },
                    labels = rounds.map { it.round.handsPlayed.takeIf { hands -> hands > 0 }?.toString() },
                    selected = selected,
                    onSelect = { selected = it },
                    tooltip = tip,
                )
                parts.zip(values(round)).forEach { (part, value) -> LegendRow(part.second, stringResource(part.first), value.toString()) }
            }
            RoundView.SKILLS -> {
                val lines = listOf<Triple<StringResource, (TournamentRoundTotals) -> Double?, (Double) -> String>>(
                    Triple(Res.string.outcome_hands_drawn, { it.drawRate }, ::percent),
                    Triple(Res.string.skill_self_draw, { it.selfDrawRate }, ::percent),
                    Triple(Res.string.rounds_avg_hand_value, { it.averageHandScore }, { v -> v.roundToInt().toString() }),
                )
                SkillCharts(
                    names = roundNames,
                    lines = lines.map { (title, read, _) ->
                        Triple(title, { i: Int -> rounds[i].takeIf { it.round.handsPlayed > 0 }?.cumulative?.let(read) }, { i: Int -> read(rounds[i].round) })
                    },
                    minValues = listOf(0.0, 0.0, 8.0),
                    formats = lines.map { it.third },
                    selected = selected,
                    onSelect = { selected = it },
                    note = false,
                )
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                rounds.forEach { slice ->
                    val round = slice.round
                    Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs)) {
                        Text(stringResource(Res.string.rounds_round_n, round.roundId), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        RecordLine(stringResource(Res.string.stat_highest_hand_score), round.highestHand?.value, round.highestHand?.playerId, names, onOpenPlayer)
                        RecordLine(stringResource(Res.string.stat_highest_table_score), round.highestTableScore?.value, round.highestTableScore?.playerId, names, onOpenPlayer)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordLine(label: String, value: Int?, playerId: Int?, names: Map<Int, String>, onOpenPlayer: ((Int) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        val name = playerId?.let { names[it] }
        if (name != null) {
            Text(
                name,
                Modifier.width(140.dp).then(if (onOpenPlayer != null) Modifier.clickable { onOpenPlayer(playerId) } else Modifier),
                style = MaterialTheme.typography.bodyMedium,
                color = if (onOpenPlayer != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
