package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.Standing
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.compare_best_info
import mahjongtournamentsuite.ui.generated.resources.compare_best_place
import mahjongtournamentsuite.ui.generated.resources.compare_best_title
import mahjongtournamentsuite.ui.generated.resources.compare_last_tournament
import mahjongtournamentsuite.ui.generated.resources.compare_no_place
import mahjongtournamentsuite.ui.generated.resources.compare_places_info
import mahjongtournamentsuite.ui.generated.resources.compare_skills_info
import mahjongtournamentsuite.ui.generated.resources.h2h_rank_of
import mahjongtournamentsuite.ui.generated.resources.h2h_ranks_info
import mahjongtournamentsuite.ui.generated.resources.h2h_ranks_title
import mahjongtournamentsuite.ui.generated.resources.h2h_skills_info
import mahjongtournamentsuite.ui.generated.resources.h2h_skills_title
import mahjongtournamentsuite.ui.generated.resources.stats_places
import mahjongtournamentsuite.ui.generated.resources.stats_skills
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The color of each of the two players in the charts of the head to head: [fill] for shapes and lines, [text] for text. */
internal class HeadToHeadColors(val fill: Color, val text: Color)

/** The person on the left of the head to head is green, the brand color. The rival is blue. */
@Composable
internal fun headToHeadColors(): Pair<HeadToHeadColors, HeadToHeadColors> = HeadToHeadColors(MaterialTheme.colorScheme.primary, MtsTheme.colors.link) to
    HeadToHeadColors(MtsTheme.colors.info, MtsTheme.colors.info)

/** The skills of the two players at the tables that they shared, one radar shape over the other. */
@Composable
fun HeadToHeadSkillsCard(
    mine: PlayerStatistics,
    theirs: PlayerStatistics,
    me: PlayerProfile?,
    rival: PlayerProfile?,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.h2h_skills_title),
    info: String = stringResource(Res.string.h2h_skills_info),
) {
    val (myColors, theirColors) = headToHeadColors()
    StatCard(title, modifier, info = info) {
        Legend(me, rival)
        RadarChartOf(
            listOf(
                RadarSeries(skillsOf(mine), myColors.fill, myColors.text),
                RadarSeries(skillsOf(theirs), theirColors.fill, theirColors.text),
            ),
            Modifier.fillMaxWidth().height(300.dp),
        )
    }
}

/** The skills of the two players over all their tables, one radar shape over the other. */
@Composable
fun CompareSkillsCard(mine: PlayerStatistics, theirs: PlayerStatistics, me: PlayerProfile?, rival: PlayerProfile?, modifier: Modifier = Modifier) {
    HeadToHeadSkillsCard(
        mine,
        theirs,
        me,
        rival,
        modifier,
        title = stringResource(Res.string.stats_skills),
        info = stringResource(Res.string.compare_skills_info),
    )
}

/**
 * The final place of the two players in the tournaments where both played, the oldest first: a line for each one, with
 * first place at the top, and the list of the tournaments under it. [tournaments] are in play order.
 */
@Composable
fun HeadToHeadPlacesCard(
    tournaments: List<Tournament>,
    mine: Map<String, Standing>,
    theirs: Map<String, Standing>,
    me: PlayerProfile?,
    rival: PlayerProfile?,
    modifier: Modifier = Modifier,
) {
    val both = tournaments.filter { it.id in mine && it.id in theirs }
    if (both.isEmpty()) return
    val (myColors, theirColors) = headToHeadColors()
    var selected by remember(both) { mutableStateOf<Int?>(null) }
    val myName = firstName(me)
    val theirName = firstName(rival)
    StatCard(stringResource(Res.string.h2h_ranks_title), modifier, info = stringResource(Res.string.h2h_ranks_info)) {
        Legend(me, rival)
        val myRanks = both.map { mine.getValue(it.id) }
        val theirRanks = both.map { theirs.getValue(it.id) }
        val tooltip = selected?.let { index ->
            listOf(
                both[index].name,
                "$myName: ${stringResource(Res.string.h2h_rank_of, myRanks[index].rank.toString(), myRanks[index].of)}",
                "$theirName: ${stringResource(Res.string.h2h_rank_of, theirRanks[index].rank.toString(), theirRanks[index].of)}",
            ).joinToString("\n")
        }
        RoundLineChart(
            names = both.indices.map { (it + 1).toString() },
            series = listOf(
                LineSeries(myRanks.map { it.rank.toDouble() }, myColors.fill),
                LineSeries(theirRanks.map { it.rank.toDouble() }, theirColors.fill),
            ),
            minValue = 1.0,
            maxValue = (myRanks + theirRanks).maxOf { it.of }.coerceAtLeast(2).toDouble(),
            selected = selected,
            onSelect = { selected = it },
            inverted = true,
            format = { it.roundToInt().toString() },
            tooltip = tooltip,
            axisValues = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            both.forEachIndexed { index, tournament ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString(),
                        Modifier.width(20.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    CountryFlag(tournament.hostCountry, width = 24.dp)
                    Text(
                        tournament.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RankText(myRanks[index], theirRanks[index], myColors.text)
                    RankText(theirRanks[index], myRanks[index], theirColors.text)
                }
            }
        }
    }
}

/** A place in the color of its player, in bold when it is the better of the two. */
@Composable
private fun RankText(standing: Standing, other: Standing, color: Color) {
    Text(
        stringResource(Res.string.h2h_rank_of, standing.rank.toString(), standing.of),
        Modifier.width(64.dp),
        style = MaterialTheme.typography.bodyMedium.inTextFont(),
        fontWeight = if (standing.rank < other.rank) FontWeight.Bold else null,
        color = color,
        textAlign = TextAlign.End,
    )
}

/** A dot of the color of each player and the name of the player. */
@Composable
private fun Legend(me: PlayerProfile?, rival: PlayerProfile?) {
    val (myColors, theirColors) = headToHeadColors()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.lg, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs),
    ) {
        LegendName(me, myColors.fill)
        LegendName(rival, theirColors.fill)
    }
}

@Composable
private fun LegendName(profile: PlayerProfile?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(profile?.name ?: EMPTY, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun firstName(profile: PlayerProfile?): String = profile?.name?.substringBefore(' ')?.ifBlank { null } ?: profile?.name ?: EMPTY

/**
 * The share of the tables that each of the two players finished in each place: a line with a point for each place,
 * one line for each player, and the shares of each place under the chart.
 */
@Composable
fun ComparePlacesCard(mine: PlayerStatistics, theirs: PlayerStatistics, me: PlayerProfile?, rival: PlayerProfile?, modifier: Modifier = Modifier) {
    val (myColors, theirColors) = headToHeadColors()
    val names = placeNames()
    var selected by remember { mutableStateOf<Int?>(null) }
    StatCard(stringResource(Res.string.stats_places), modifier, info = stringResource(Res.string.compare_places_info)) {
        Legend(me, rival)
        val myShares = (0 until 4).map { share(mine, it) }
        val theirShares = (0 until 4).map { share(theirs, it) }
        // The top of the scale is the biggest share, rounded up to the next ten, so small differences show.
        val top = ((myShares + theirShares).maxOf { it ?: 0.0 } * 10).let { kotlin.math.ceil(it) / 10 }.coerceIn(0.1, 1.0)
        val tooltip = selected?.let { index ->
            listOf(
                names[index],
                "${firstName(me)}: ${myShares[index].percent()}",
                "${firstName(rival)}: ${theirShares[index].percent()}",
            ).joinToString("\n")
        }
        RoundLineChart(
            names = names,
            series = listOf(LineSeries(myShares, myColors.fill), LineSeries(theirShares, theirColors.fill)),
            minValue = 0.0,
            maxValue = top,
            selected = selected,
            onSelect = { selected = it },
            format = { "${(it * 100).roundToInt()}%" },
            tooltip = tooltip,
            axisValues = true,
        )
        // The shares of each place, in the color of each player, under its place in the chart.
        Row(Modifier.fillMaxWidth()) {
            (0 until 4).forEach { place ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xxs)) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, Modifier.size(20.dp), tint = MtsTheme.colors.position(place + 1, MaterialTheme.colorScheme.error))
                    Text(myShares[place].percent(), style = MaterialTheme.typography.bodyMedium.inTextFont(), fontWeight = FontWeight.Bold, color = myColors.text)
                    Text(theirShares[place].percent(), style = MaterialTheme.typography.bodyMedium.inTextFont(), fontWeight = FontWeight.Bold, color = theirColors.text)
                }
            }
        }
    }
}

private fun share(statistics: PlayerStatistics, place: Int): Double? = statistics.tablesPlayed.takeIf { it > 0 }?.let { statistics.placeCounts[place].toDouble() / it }

/**
 * The best final place of each of the two players in a tournament, and the place in the last tournament.
 * [mine] and [theirs] are the tournaments of each one in play order, with the final place, null when not known yet.
 */
@Composable
fun CompareTournamentsCard(
    mine: List<Pair<Tournament, Standing?>>,
    theirs: List<Pair<Tournament, Standing?>>,
    me: PlayerProfile?,
    rival: PlayerProfile?,
    modifier: Modifier = Modifier,
) {
    val (myColors, theirColors) = headToHeadColors()
    fun best(list: List<Pair<Tournament, Standing?>>) = list.filter { it.second != null }
        .minWithOrNull(compareBy<Pair<Tournament, Standing?>> { it.second!!.rank.toDouble() / it.second!!.of }.thenBy { it.second!!.rank })
    StatCard(stringResource(Res.string.compare_best_title), modifier, info = stringResource(Res.string.compare_best_info)) {
        Legend(me, rival)
        val rows = listOf(
            stringResource(Res.string.compare_best_place) to (best(mine) to best(theirs)),
            stringResource(Res.string.compare_last_tournament) to (mine.lastOrNull() to theirs.lastOrNull()),
        )
        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            rows.forEachIndexed { index, (label, pair) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    label,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.md)) {
                    TournamentPlace(pair.first, myColors, Modifier.weight(1f))
                    TournamentPlace(pair.second, theirColors, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TournamentPlace(entry: Pair<Tournament, Standing?>?, colors: HeadToHeadColors, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        val standing = entry?.second
        Text(
            when {
                entry == null -> EMPTY
                standing == null -> stringResource(Res.string.compare_no_place)
                else -> stringResource(Res.string.h2h_rank_of, standing.rank.toString(), standing.of)
            },
            style = if (standing != null) MaterialTheme.typography.titleLarge.inTextFont() else MaterialTheme.typography.bodyMedium,
            fontWeight = if (standing != null) FontWeight.Bold else null,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        entry?.first?.let { tournament ->
            Row(horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                CountryFlag(tournament.hostCountry, width = 20.dp)
                Text(tournament.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
