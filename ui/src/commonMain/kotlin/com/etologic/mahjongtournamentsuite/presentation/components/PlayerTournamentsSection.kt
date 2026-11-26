package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.places_history_title
import mahjongtournamentsuite.ui.generated.resources.rank_of
import mahjongtournamentsuite.ui.generated.resources.round_n
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand
import mahjongtournamentsuite.ui.generated.resources.stat_best_table_score
import mahjongtournamentsuite.ui.generated.resources.stat_chicken_hands
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_penalties
import mahjongtournamentsuite.ui.generated.resources.stat_penalties_info
import mahjongtournamentsuite.ui.generated.resources.stat_rank
import mahjongtournamentsuite.ui.generated.resources.stats_places
import mahjongtournamentsuite.ui.generated.resources.stats_skills
import mahjongtournamentsuite.ui.generated.resources.table_n
import mahjongtournamentsuite.ui.generated.resources.years_all
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException

/**
 * The tournaments of one person, the newest first. Each tournament is a card. Folded, the card already says the dates,
 * the place, the points and the score of the player, so the list works as a summary of the whole career.
 * Unfolded, it shows the numbers, skills, places and hands of that tournament. Every card starts folded, however many
 * there are, and opens only when it is tapped.
 * With tournaments in more than one year, a row of year chips on top filters the list.
 * With [tournamentId], chosen in the bar of [ChosenTournamentBar], the section shows only that tournament, folded too.
 */
@Composable
fun PlayerTournamentsSection(
    items: List<Pair<Tournament, PlayerStatistics>>,
    modifier: Modifier = Modifier,
    /** Gives the final places in some tournaments. The section asks only for the tournaments that it shows. */
    loadStandings: (suspend (List<String>) -> Map<String, Standing>)? = null,
    /** The one tournament to show. Null shows all of them. */
    tournamentId: String? = null,
) {
    val sorted = remember(items) {
        items.sortedWith(
            compareByDescending<Pair<Tournament, PlayerStatistics>> { it.first.eventStartDate ?: "" }.thenByDescending { it.first.id },
        )
    }
    val ids = sorted.map { it.first.id }
    // Every card starts folded. Only a tap opens one.
    val expanded = remember(ids) { mutableStateMapOf<String, Boolean>() }
    val standings = remember(ids) { mutableStateMapOf<String, Standing>() }
    // The round that each tournament card shows. A card without a round shows the whole tournament.
    val rounds = remember(ids) { mutableStateMapOf<String, Int>() }
    val requested = remember(ids) { mutableSetOf<String>() }
    // The years with a tournament, the newest first. Null means all the years.
    val years = remember(sorted) { sorted.mapNotNull { it.first.tournamentYear() }.distinct() }
    var year by remember(ids) { mutableStateOf<Int?>(null) }
    val single = sorted.firstOrNull { it.first.id == tournamentId }
    val visible = remember(sorted, year, single) {
        when {
            single != null -> listOf(single)
            year == null -> sorted
            else -> sorted.filter { it.first.tournamentYear() == year }
        }
    }
    // The places load for the tournaments on screen. A new year asks only for its own tournaments.
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (single == null && years.size > 1) {
            RoundChipsRow(Modifier.fillMaxWidth(), 0.dp) {
                FilterChip(selected = year == null, onClick = { year = null }, label = { Text(stringResource(Res.string.years_all)) })
                years.forEach { y ->
                    FilterChip(
                        selected = y == year,
                        onClick = { year = if (y == year) null else y },
                        label = { Text(y.toString()) },
                    )
                }
            }
        }
        visible.forEach { (tournament, wholeStats) ->
            val open = expanded[tournament.id] ?: false
            val round = rounds[tournament.id]
            val standing = standings[tournament.id]
            // With a round chosen, every number and chart of the card counts only the tables of that round.
            val stats = remember(wholeStats, round) {
                round?.let { r -> PlayerStatistics(wholeStats.playerId, wholeStats.results.filter { it.roundId == r }) } ?: wholeStats
            }
            StatCard(
                tournament.name,
                Modifier.fillMaxWidth(),
                expanded = open,
                onToggle = { expanded[tournament.id] = !open },
                // In the dark theme the line is darker than the card, not lighter.
                border = BorderStroke(
                    1.dp,
                    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                ),
                summary = { TournamentSummary(tournament, standing) },
            ) {
                val hasHands = stats.tablesWithHands > 0
                // The rounds of the tournament: Totals or one round. The row reaches the sides of the card.
                PlayerRoundChips(
                    wholeStats.results,
                    round,
                    { chosen -> if (chosen == null) rounds.remove(tournament.id) else rounds[tournament.id] = chosen },
                    edge = MtsTheme.spacing.lg,
                )
                StatGrid(Modifier.fillMaxWidth()) {
                    LevelMedalWithScore(stats)
                    // The place, the points and the score of the whole tournament are in the summary under the name.
                    // One round shows its own points and score here.
                    if (round != null) {
                        BigNumber(stringResource(Res.string.column_points), formatPoints(stats.results.sumOf { it.points }))
                        BigNumber(stringResource(Res.string.column_score), stats.results.sumOf { it.score }.toString())
                    }
                    BigNumber("Tables", stats.tablesPlayed.toString())
                    BigNumber("Hands", if (hasHands) stats.handsPlayed.toString() else EMPTY)
                    BigNumber(stringResource(Res.string.stat_best_table_score), stats.bestTableScore.text())
                    BigNumber(stringResource(Res.string.stat_best_hand), stats.bestHand.text())
                    if (tournament.countChickenHands && hasHands) BigNumber(stringResource(Res.string.stat_chicken_hands), stats.chickenHands.toString())
                    BigNumber(stringResource(Res.string.stat_penalties), penaltiesText(stats), info = stringResource(Res.string.stat_penalties_info))
                }
                // The skills and the hands go in the first row. The places and the place history go in the second row. With less room each block takes one row.
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = MtsTheme.spacing.xl)) {
                    val blocks = buildList<@Composable (Modifier) -> Unit> {
                        add { m -> TitledBlock(stringResource(Res.string.stats_skills), m) { SkillsBlock(stats, 240.dp, Modifier.fillMaxWidth(), showLevel = false) } }
                        if (hasHands) add { m -> TitledBlock(stringResource(Res.string.stat_hands), m) { HandOutcomes(stats, 220.dp, Modifier.fillMaxWidth()) } }
                        add { m -> TitledBlock(stringResource(Res.string.stats_places), m) { PlaceSectors(stats, 170.dp, Modifier.fillMaxWidth()) } }
                        // The place history goes after the places, so it sits at the right of the places in the second row.
                        // It has no title: the title of the places block gives the context.
                        if (stats.results.size > 1) {
                            add { m ->
                                Column(m) { PlaceHistory(stats.results, listOf(tournament), showTitle = false) }
                            }
                        }
                    }
                    val gap = 24.dp
                    val columns = ((maxWidth + gap) / (TOURNAMENT_BLOCK_MIN_WIDTH + gap)).toInt().coerceIn(1, minOf(2, blocks.size))
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
    }
}

private fun Tournament.tournamentYear(): Int? = eventStartDate?.take(4)?.toIntOrNull()

/**
 * The line under the name of a folded tournament card: the dates, the place in the ranking with its medal color, the
 * points and the score. The place, the points and the score show once the places have loaded.
 */
@Composable
private fun TournamentSummary(tournament: Tournament, standing: Standing?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)?.let { dates ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                Icon(Icons.Default.Event, contentDescription = null, Modifier.size(18.dp), tint = muted)
                Text(dates, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        standing?.let { place ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
                Icon(
                    Icons.Default.Leaderboard,
                    contentDescription = stringResource(Res.string.stat_rank),
                    Modifier.size(18.dp),
                    tint = MtsTheme.colors.position(place.rank, muted),
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(place.rank.toString()) }
                        append(" " + stringResource(Res.string.rank_of, place.of))
                    },
                    style = MaterialTheme.typography.bodyMedium.inTextFont(),
                    color = muted,
                )
            }
            SummaryValue(stringResource(Res.string.column_points), formatPoints(place.points))
            SummaryValue(stringResource(Res.string.column_score), place.score.toString())
        }
    }
}

/** A label and its value in bold, like "Points 12.5". */
@Composable
private fun SummaryValue(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium.inTextFont(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A chart block of a tournament card with its title on top. The titles of a row line up at the top. */
@Composable
private fun TitledBlock(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A little smaller than the titles of the cards of "Me", and in the text font: it is a title inside a card (Ernesto, 2026-11-25).
        CardTitle(title, style = MaterialTheme.typography.headlineSmall.inTextFont())
        content()
    }
}

/** The least width of a chart block in a tournament card. A narrower space moves the block to the next row. */
private val TOURNAMENT_BLOCK_MIN_WIDTH = 340.dp

/** Date and time of the table, in two lines. The date comes from the schedule of the round. */
private fun tableTime(result: TableResult, tournaments: List<Tournament>): String {
    val tournament = tournaments.firstOrNull { it.id == result.tournamentId } ?: tournaments.singleOrNull()
    val schedule = tournament?.roundSchedules?.firstOrNull { it.roundId == result.roundId }
    val date = (schedule?.date ?: tournament?.eventStartDate)?.split("-")
        ?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0].takeLast(2)}" } ?: EMPTY
    return "$date\n${schedule?.startTime ?: EMPTY}"
}

/** The short name of the tournament, the round and the table, for example "EMA Open - Round 2 - Table 5". */
@Composable
private fun tableLabel(result: TableResult, tournaments: List<Tournament>): String {
    val tournament = tournaments.firstOrNull { it.id == result.tournamentId } ?: tournaments.singleOrNull()
    val name = tournament?.let { it.shortName.ifBlank { it.name } }.orEmpty()
    return listOf(name, stringResource(Res.string.round_n, result.roundId), stringResource(Res.string.table_n, result.tableId)).filter { it.isNotBlank() }.joinToString(" - ")
}

/** The space above the first place line of the place history. With several tournaments it holds their names. */
private val PlaceHistoryTop = 12.dp
private val PlaceHistoryGroupedTop = 28.dp

/** A run of tables of one tournament in the place history, from the index [first] to the index [last]. */
internal data class TournamentRun(val tournamentId: String, val first: Int, val last: Int)

/** The runs of tables that follow each other in the same tournament. */
internal fun tournamentRuns(results: List<TableResult>): List<TournamentRun> {
    val runs = mutableListOf<TournamentRun>()
    results.forEachIndexed { index, result ->
        val previous = runs.lastOrNull()
        if (previous != null && previous.tournamentId == result.tournamentId) {
            runs[runs.lastIndex] = previous.copy(last = index)
        } else {
            runs += TournamentRun(result.tournamentId, index, index)
        }
    }
    return runs
}

@Composable
internal fun PlaceHistory(results: List<TableResult>, tournaments: List<Tournament>, showTitle: Boolean = true) {
    val places = results.map { it.position }
    val placeNames = placeNames()
    val times = results.map { tableTime(it, tournaments) }
    val tips = results.mapIndexed { index, result -> tableLabel(result, tournaments) + "\n" + times[index].replace('\n', ' ') }
    if (showTitle) Text(pluralStringResource(Res.plurals.places_history_title, places.size, places.size), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
    val pointColors = (1..4).map { positionColor(it) }
    // The tables of one tournament follow each other. Each run of tables gets a band of its own color behind it,
    // with the short name of the tournament on top, so the chart shows where each tournament starts and ends.
    val groups = tournamentRuns(results)
    val grouped = groups.size > 1
    // Three clearly different hues (green, pink, blue). Bands take them in turn, so two tournaments next to each other
    // never share a color. The secondary green of the theme is not used: it looked the same as the primary one.
    val bandColors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MtsTheme.colors.info)
    val groupNames = groups.map { run ->
        tournaments.firstOrNull { it.id == run.tournamentId }?.let { it.shortName.ifBlank { it.name } }.orEmpty()
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        textAlign = TextAlign.Center,
    )
    val topPad = if (grouped) PlaceHistoryGroupedTop else PlaceHistoryTop
    BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp + topPad - PlaceHistoryTop)) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 36.dp.toPx()
            val pad = 12.dp.toPx()
            val top = topPad.toPx()
            val bottom = 44.dp.toPx()
            val width = size.width - left - pad * 2
            val height = size.height - top - bottom
            fun y(place: Int) = top + height * (place - 1) / 3f
            fun x(step: Int) = left + if (places.size == 1) width / 2f else width * step / (places.size - 1)
            if (grouped) {
                val half = if (places.size > 1) width / (places.size - 1) / 2f else 0f
                groups.forEachIndexed { index, run ->
                    val color = bandColors[index % bandColors.size]
                    val start = (x(run.first) - half).coerceAtLeast(left - pad)
                    val end = (x(run.last) + half).coerceAtMost(size.width - pad)
                    // The band covers the plot. A stronger strip under it marks the dates of the same tournament.
                    drawRect(color.copy(alpha = 0.08f), Offset(start, top - pad), androidx.compose.ui.geometry.Size(end - start, height + pad * 2))
                    drawRect(color.copy(alpha = 0.55f), Offset(start, size.height - bottom + 4.dp.toPx()), androidx.compose.ui.geometry.Size(end - start, 3.dp.toPx()))
                    val name = groupNames[index]
                    if (name.isNotBlank()) {
                        val layout = measurer.measure(
                            name,
                            labelStyle.copy(color = color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = (end - start).toInt().coerceAtLeast(1)),
                        )
                        if (layout.size.width <= end - start) {
                            drawText(layout, topLeft = Offset(start + (end - start - layout.size.width) / 2f, 2.dp.toPx()))
                        }
                    }
                }
            }
            (1..4).forEach { place ->
                drawLine(grid, Offset(left, y(place)), Offset(size.width - pad, y(place)), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(placeNames[place - 1], labelStyle)
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
        val plotHeight = maxHeight - topPad - 44.dp
        places.forEachIndexed { step, place ->
            val centerX = 36.dp + if (places.size == 1) plotWidth / 2f else plotWidth * step / (places.size - 1)
            val centerY = topPad + plotHeight * (place - 1) / 3f
            // The offset sits on a wrapper. The tooltip box must be the 24 dp box itself, or its anchor is not the dot.
            Box(Modifier.offset(centerX - 12.dp, centerY - 12.dp).size(24.dp)) {
                HoverTip(tips[step], Modifier.size(24.dp)) { Box(Modifier.size(24.dp)) }
            }
        }
    }
}

/** The shadow of a [StatCard]. */
internal val CardShadow = Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.25f), offset = DpOffset(0.dp, 1.dp))
