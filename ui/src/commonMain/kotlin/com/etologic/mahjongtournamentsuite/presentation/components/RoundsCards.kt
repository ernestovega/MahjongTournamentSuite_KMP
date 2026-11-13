package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRound
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSlice
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundTotals
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.outcome_by_discard
import mahjongtournamentsuite.ui.generated.resources.outcome_by_self_draw
import mahjongtournamentsuite.ui.generated.resources.outcome_hands_drawn
import mahjongtournamentsuite.ui.generated.resources.round_short
import mahjongtournamentsuite.ui.generated.resources.rounds_column_deal_ins
import mahjongtournamentsuite.ui.generated.resources.rounds_column_place
import mahjongtournamentsuite.ui.generated.resources.rounds_column_table
import mahjongtournamentsuite.ui.generated.resources.rounds_column_won
import mahjongtournamentsuite.ui.generated.resources.rounds_cumulative_note
import mahjongtournamentsuite.ui.generated.resources.rounds_empty_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_hands_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_place_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_points_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_round_n
import mahjongtournamentsuite.ui.generated.resources.rounds_team_points_detail
import mahjongtournamentsuite.ui.generated.resources.rounds_title
import mahjongtournamentsuite.ui.generated.resources.rounds_view_place
import mahjongtournamentsuite.ui.generated.resources.skill_deal_in_rate
import mahjongtournamentsuite.ui.generated.resources.skill_hand_value
import mahjongtournamentsuite.ui.generated.resources.skill_self_draw
import mahjongtournamentsuite.ui.generated.resources.skill_win_rate
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_highest_hand_score
import mahjongtournamentsuite.ui.generated.resources.stat_highest_table_score
import mahjongtournamentsuite.ui.generated.resources.stat_penalties
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
    POINTS(Res.string.column_points),
    HANDS(Res.string.stat_hands),
    SKILLS(Res.string.stats_skills),
    TOTALS(Res.string.stats_totals),
    RECORDS(Res.string.stats_records),
}

/** The test tag of the chip of a view of the "By round" cards. */
fun roundViewTag(view: RoundView): String = "round-view-" + view.name.lowercase()

/** The frame of every "By round" card: the title, the chips of the views, and the view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundsCardFrame(
    views: List<RoundView>,
    modifier: Modifier,
    content: @Composable (RoundView) -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(views.first().name) }
    val view = views.firstOrNull { it.name == chosen } ?: views.first()
    StatCard(stringResource(Res.string.rounds_title), modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            views.forEach { item ->
                FilterChip(
                    selected = item == view,
                    onClick = { chosen = item.name },
                    label = { Text(stringResource(item.title)) },
                    modifier = Modifier.testTag(roundViewTag(item)),
                )
            }
        }
        content(view)
    }
}

/** The round names under the charts, such as "R1". */
@Composable
private fun roundNames(roundIds: List<Int>): List<String> = roundIds.map { stringResource(Res.string.round_short, it) }

/** The text under a chart that tells what the selected round has. */
@Composable
private fun DetailLine(text: String) = Text(
    text,
    Modifier.fillMaxWidth(),
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
)

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
    SkillLine(Res.string.skill_hand_value, { it.averageWinningScore }, 8.0, { it.roundToInt().toString() }),
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
                Text(shown?.let(formats[index]) ?: "—", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
            )
        }
        Text(
            stringResource(Res.string.rounds_cumulative_note),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
private fun PlaceView(names: List<String>, roundIds: List<Int>, places: Map<Int, Int>, of: Int, played: List<Boolean>, selected: Int, onSelect: (Int) -> Unit) {
    val values = roundIds.mapIndexed { index, id -> if (played[index]) places[id]?.toDouble() else null }
    RoundLineChart(
        names = names,
        series = listOf(LineSeries(values, MaterialTheme.colorScheme.primary)),
        minValue = 1.0,
        maxValue = of.coerceAtLeast(2).toDouble(),
        selected = selected,
        onSelect = onSelect,
        inverted = true,
        format = { "#${it.roundToInt()}" },
    )
    val place = values.getOrNull(selected)
    DetailLine(
        if (place == null) {
            stringResource(Res.string.rounds_empty_detail, roundIds[selected])
        } else {
            stringResource(Res.string.rounds_place_detail, roundIds[selected], place.roundToInt(), of)
        },
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
    val views = listOfNotNull(RoundView.PLACE.takeIf { !places.isNullOrEmpty() }, RoundView.POINTS, RoundView.SKILLS, RoundView.TOTALS)
    var selected by remember(rounds.size) { mutableStateOf(lastPlayed(rounds)) }
    val names = roundNames(rounds.map { it.roundId })
    RoundsCardFrame(views, modifier) { view ->
        when (view) {
            RoundView.PLACE -> PlaceView(names, rounds.map { it.roundId }, places.orEmpty(), playerCount, rounds.map { !it.isEmpty }, selected) { selected = it }
            RoundView.POINTS -> {
                RoundBarChart(
                    names = names,
                    bars = rounds.map { round -> round.round.results.map { BarPart(it.points, positionColor(it.position)) } },
                    labels = rounds.map { round -> round.round.results.takeIf { it.isNotEmpty() }?.sumOf { it.score }?.toString() },
                    selected = selected,
                    onSelect = { selected = it },
                    maxValue = max(4.0, rounds.maxOf { it.round.totalPoints }),
                )
                val round = rounds[selected]
                val result = round.round.results.firstOrNull()
                DetailLine(
                    if (result == null) {
                        stringResource(Res.string.rounds_empty_detail, round.roundId)
                    } else {
                        stringResource(Res.string.rounds_points_detail, round.roundId, result.tableId, result.position, formatPoints(result.points), result.score)
                    },
                )
            }
            RoundView.SKILLS -> PlayerSkillCharts(names, rounds, selected) { selected = it }
            else -> PlayerTotalsTable(rounds)
        }
    }
}

/** One row for each round: the table, the place, the points, the score, the hands won, the deal-ins and the penalties. */
@Composable
private fun PlayerTotalsTable(rounds: List<PlayerRound>) {
    val headers = listOf(
        stringResource(Res.string.rounds_column_table),
        stringResource(Res.string.rounds_column_place),
        stringResource(Res.string.column_points),
        stringResource(Res.string.column_score),
        stringResource(Res.string.rounds_column_won),
        stringResource(Res.string.rounds_column_deal_ins),
        stringResource(Res.string.stat_penalties),
    )
    val rows = rounds.map { round ->
        val r = round.round
        val result = r.results.firstOrNull()
        stringResource(Res.string.rounds_round_n, round.roundId) to if (result == null) {
            List(headers.size) { "—" }
        } else {
            listOf(
                result.tableId.toString(),
                result.position.toString(),
                formatPoints(r.totalPoints),
                r.totalScore.toString(),
                if (r.tablesWithHands > 0) "${r.handsWon}/${r.handsPlayed}" else "—",
                if (r.tablesWithHands > 0) r.dealIns.toString() else "—",
                if (r.tablesWithHands > 0) r.penaltyCount.toString() else "—",
            )
        }
    }
    RoundsTable(headers, rows)
}

/** A table with the round fixed on the left and the other columns that scroll sideways on a narrow screen. */
@Composable
private fun RoundsTable(headers: List<String>, rows: List<Pair<String, List<String>>>, cellWidth: Dp = 64.dp) {
    val scroll = rememberScrollState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            Text("", style = MaterialTheme.typography.labelSmall)
            rows.forEach { (round, _) -> Text(round, Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll), verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            Row {
                headers.forEach { Text(it, Modifier.width(cellWidth), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.End, maxLines = 1) }
            }
            rows.forEach { (_, cells) ->
                Row {
                    cells.forEach { Text(it, Modifier.width(cellWidth), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1) }
                }
            }
        }
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
    val views = listOfNotNull(RoundView.PLACE.takeIf { !places.isNullOrEmpty() }, RoundView.POINTS, RoundView.SKILLS)
    var selected by remember(rounds.size) { mutableStateOf(lastPlayed(rounds)) }
    val names = roundNames(rounds.map { it.roundId })
    val colors = memberColors()
    RoundsCardFrame(views, modifier) { view ->
        when (view) {
            RoundView.PLACE -> PlaceView(names, rounds.map { it.roundId }, places.orEmpty(), teamCount, rounds.map { !it.isEmpty }, selected) { selected = it }
            RoundView.POINTS -> {
                RoundBarChart(
                    names = names,
                    bars = rounds.indices.map { index ->
                        members.mapIndexed { m, (_, memberRounds) -> BarPart(memberRounds.getOrNull(index)?.round?.totalPoints ?: 0.0, colors[m % colors.size]) }
                    },
                    labels = rounds.map { round -> round.round.results.takeIf { it.isNotEmpty() }?.let { formatPoints(round.round.totalPoints) } },
                    selected = selected,
                    onSelect = { selected = it },
                )
                members.forEachIndexed { m, (name, memberRounds) ->
                    LegendRow(colors[m % colors.size], name, memberRounds.getOrNull(selected)?.round?.takeIf { it.results.isNotEmpty() }?.let { formatPoints(it.totalPoints) } ?: "—")
                }
                val round = rounds[selected]
                DetailLine(
                    if (round.isEmpty) {
                        stringResource(Res.string.rounds_empty_detail, round.roundId)
                    } else {
                        stringResource(Res.string.rounds_team_points_detail, round.roundId, formatPoints(round.round.totalPoints))
                    },
                )
            }
            else -> PlayerSkillCharts(names, rounds, selected) { selected = it }
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
                RoundBarChart(
                    names = roundNames,
                    bars = rounds.map { slice -> values(slice.round).zip(parts) { value, part -> BarPart(value.toDouble(), part.second) } },
                    labels = rounds.map { it.round.handsPlayed.takeIf { hands -> hands > 0 }?.toString() },
                    selected = selected,
                    onSelect = { selected = it },
                )
                val round = rounds[selected].round
                parts.zip(values(round)).forEach { (part, value) -> LegendRow(part.second, stringResource(part.first), value.toString()) }
                DetailLine(
                    stringResource(
                        Res.string.rounds_hands_detail,
                        round.roundId,
                        round.tablesCompleted,
                        round.tablesTotal,
                        round.handsPlayed,
                        round.drawnHands,
                        round.penaltyCount,
                    ),
                )
            }
            RoundView.SKILLS -> {
                val lines = listOf<Triple<StringResource, (TournamentRoundTotals) -> Double?, (Double) -> String>>(
                    Triple(Res.string.outcome_hands_drawn, { it.drawRate }, ::percent),
                    Triple(Res.string.skill_self_draw, { it.selfDrawRate }, ::percent),
                    Triple(Res.string.skill_hand_value, { it.averageHandScore }, { v -> v.roundToInt().toString() }),
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
