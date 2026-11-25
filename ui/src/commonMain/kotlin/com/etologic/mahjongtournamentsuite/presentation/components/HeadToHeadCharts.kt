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
import androidx.compose.material3.HorizontalDivider
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
import mahjongtournamentsuite.ui.generated.resources.h2h_rank_of
import mahjongtournamentsuite.ui.generated.resources.h2h_ranks_info
import mahjongtournamentsuite.ui.generated.resources.h2h_ranks_title
import mahjongtournamentsuite.ui.generated.resources.h2h_skills_info
import mahjongtournamentsuite.ui.generated.resources.h2h_skills_title
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
) {
    val (myColors, theirColors) = headToHeadColors()
    StatCard(stringResource(Res.string.h2h_skills_title), modifier, info = stringResource(Res.string.h2h_skills_info)) {
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
