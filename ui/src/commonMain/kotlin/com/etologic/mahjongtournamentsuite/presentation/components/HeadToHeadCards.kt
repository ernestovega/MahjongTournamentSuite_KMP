package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.usecase.HeadToHead
import com.etologic.mahjongtournamentsuite.domain.usecase.HeadToHeadSide
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.h2h_average_place
import mahjongtournamentsuite.ui.generated.resources.h2h_hands_won
import mahjongtournamentsuite.ui.generated.resources.h2h_info
import mahjongtournamentsuite.ui.generated.resources.h2h_self_draws
import mahjongtournamentsuite.ui.generated.resources.h2h_shared_tables
import mahjongtournamentsuite.ui.generated.resources.h2h_shared_tables_info
import mahjongtournamentsuite.ui.generated.resources.h2h_table_line
import mahjongtournamentsuite.ui.generated.resources.h2h_table_wins
import mahjongtournamentsuite.ui.generated.resources.h2h_tables_above
import mahjongtournamentsuite.ui.generated.resources.h2h_title
import mahjongtournamentsuite.ui.generated.resources.h2h_wins_on_discard
import mahjongtournamentsuite.ui.generated.resources.rival_shared_tables
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The two players side by side at the tables that they shared: each row has the number of [me] on the left, the
 * number of the rival on the right, and the better one in bold.
 */
@Composable
fun HeadToHeadCard(headToHead: HeadToHead, me: PlayerProfile?, rival: PlayerProfile?, modifier: Modifier = Modifier) {
    StatCard(stringResource(Res.string.h2h_title), modifier, info = stringResource(Res.string.h2h_info)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerName(me, Modifier.weight(1f))
            Text("vs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayerName(rival, Modifier.weight(1f))
        }
        Text(
            "${stringResource(Res.string.rival_shared_tables)}: ${headToHead.tables.size}",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val a = headToHead.me
        val b = headToHead.rival
        val rows: List<Triple<String, (HeadToHeadSide) -> Double?, (Double) -> String>> = listOf(
            Triple(stringResource(Res.string.h2h_tables_above), { s -> s.tablesAbove?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.h2h_table_wins), { s -> s.tableWins?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.h2h_average_place), { s -> s.averagePlace?.let { -it } }, { v -> oneDecimal(-v) }),
            Triple(stringResource(Res.string.column_points), { s -> s.points }, ::formatPoints),
            Triple(stringResource(Res.string.column_score), { s -> s.score?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.h2h_hands_won), { s -> s.handsWon?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.h2h_wins_on_discard), { s -> s.winsOnTheirDiscard?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.h2h_self_draws), { s -> s.selfDraws?.toDouble() }, ::whole),
            Triple(stringResource(Res.string.stat_best_hand), { s -> s.bestHand?.toDouble() }, ::whole),
        )
        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            rows.forEachIndexed { index, (label, value, text) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // The values are compared with "higher is better". The average place is negated for that.
                CompareRow(label, value(a), value(b), text)
            }
        }
    }
}

/** The tables that the two players shared, the newest first, with the place and the points of each one. */
@Composable
fun SharedTablesCard(headToHead: HeadToHead, modifier: Modifier = Modifier) {
    StatCard(stringResource(Res.string.h2h_shared_tables), modifier, info = stringResource(Res.string.h2h_shared_tables_info)) {
        Column(verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm)) {
            headToHead.tables.forEachIndexed { index, table ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val mine = table.mine
                val theirs = table.theirs
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TablePlace(mine.position, mine.points, better = theirs != null && mine.position < theirs.position, Modifier.weight(1f))
                    Row(
                        Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CountryFlag(table.tournament.hostCountry, width = 24.dp)
                        Text(
                            stringResource(Res.string.h2h_table_line, table.tournament.name, mine.roundId, mine.tableId),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (theirs != null) {
                        TablePlace(theirs.position, theirs.points, better = theirs.position < mine.position, Modifier.weight(1f))
                    } else {
                        Text(EMPTY, Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerName(profile: PlayerProfile?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
        CountryFlag(profile?.country.orEmpty(), width = 32.dp)
        Text(
            profile?.name ?: EMPTY,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompareRow(label: String, left: Double?, right: Double?, text: (Double) -> String) {
    val leftBetter = left != null && right != null && left > right
    val rightBetter = left != null && right != null && right > left
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CompareValue(left?.let(text) ?: EMPTY, leftBetter, Modifier.weight(1f))
        Text(
            label,
            Modifier.weight(1.6f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CompareValue(right?.let(text) ?: EMPTY, rightBetter, Modifier.weight(1f))
    }
}

@Composable
private fun CompareValue(value: String, better: Boolean, modifier: Modifier) {
    Text(
        value,
        modifier,
        style = MaterialTheme.typography.titleLarge.inTextFont(),
        fontWeight = if (better) FontWeight.Bold else FontWeight.Normal,
        color = if (better) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TablePlace(position: Int, points: Double, better: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CompareValue("$position.", better, Modifier)
        Text(formatPoints(points), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun whole(value: Double): String = value.roundToInt().toString()

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
