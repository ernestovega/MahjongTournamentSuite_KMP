package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.usecase.HeadToHead
import com.etologic.mahjongtournamentsuite.domain.usecase.HeadToHeadSide
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.domain.usecase.SharedTable
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.inTextFont
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.h2h_average_place
import mahjongtournamentsuite.ui.generated.resources.h2h_hands_won
import mahjongtournamentsuite.ui.generated.resources.h2h_info
import mahjongtournamentsuite.ui.generated.resources.h2h_open_table
import mahjongtournamentsuite.ui.generated.resources.h2h_place
import mahjongtournamentsuite.ui.generated.resources.h2h_self_draws
import mahjongtournamentsuite.ui.generated.resources.h2h_shared_tables
import mahjongtournamentsuite.ui.generated.resources.h2h_shared_tables_info
import mahjongtournamentsuite.ui.generated.resources.h2h_table_line
import mahjongtournamentsuite.ui.generated.resources.h2h_table_wins
import mahjongtournamentsuite.ui.generated.resources.h2h_tables_above
import mahjongtournamentsuite.ui.generated.resources.h2h_title
import mahjongtournamentsuite.ui.generated.resources.h2h_wins_on_discard
import mahjongtournamentsuite.ui.generated.resources.points_short
import mahjongtournamentsuite.ui.generated.resources.rival_shared_tables
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The two players side by side at the tables that they shared: each row has the number of [me] on the left, the
 * number of the rival on the right, the better one in bold green and the worse one in red.
 */
@Composable
fun HeadToHeadCard(headToHead: HeadToHead, me: PlayerProfile?, rival: PlayerProfile?, modifier: Modifier = Modifier) {
    StatCard(stringResource(Res.string.h2h_title), modifier, info = stringResource(Res.string.h2h_info)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerName(me, Modifier.weight(1f))
            Text("vs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayerName(rival, Modifier.weight(1f))
        }
        // The number of shared tables in its own small card, like the numbers of the other cards.
        BigNumber(
            stringResource(Res.string.rival_shared_tables),
            headToHead.tables.size.toString(),
            Modifier.align(Alignment.CenterHorizontally).widthIn(min = 160.dp),
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

/**
 * The tables that the two players shared, the newest first: the place at the table (a trophy in the color of the
 * place) and the table points of [me] on the left and of the rival on the right. A tap on a table opens it.
 */
@Composable
fun SharedTablesCard(
    headToHead: HeadToHead,
    me: PlayerProfile?,
    rival: PlayerProfile?,
    modifier: Modifier = Modifier,
    onOpenTable: ((SharedTable) -> Unit)? = null,
) {
    StatCard(stringResource(Res.string.h2h_shared_tables), modifier, info = stringResource(Res.string.h2h_shared_tables_info)) {
        // Whose column is whose.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ColumnName(me, Modifier.weight(1f))
            Spacer(Modifier.weight(2f))
            ColumnName(rival, Modifier.weight(1f))
        }
        val openLabel = stringResource(Res.string.h2h_open_table)
        Column {
            headToHead.tables.forEach { table ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val mine = table.mine
                val theirs = table.theirs
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (onOpenTable != null) Modifier.clickable(onClickLabel = openLabel) { onOpenTable(table) } else Modifier)
                        .padding(vertical = MtsTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TablePlace(mine.position, mine.points, Modifier.weight(1f))
                    Row(
                        Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.sm, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CountryFlag(table.tournament.hostCountry, width = 24.dp)
                        Text(
                            stringResource(Res.string.h2h_table_line, table.tournament.shortName.ifBlank { table.tournament.name }, mine.roundId, mine.tableId),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (onOpenTable != null) MtsTheme.colors.link else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (onOpenTable != null) TextDecoration.Underline else null,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (theirs != null) {
                        TablePlace(theirs.position, theirs.points, Modifier.weight(1f))
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
    // 1 when the left value is the better one, -1 when the right one is, 0 when they are equal or one is not known.
    val compare = if (left != null && right != null) left.compareTo(right).coerceIn(-1, 1) else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CompareValue(left?.let(text) ?: EMPTY, compare, Modifier.weight(1f))
        Text(
            label,
            Modifier.weight(1.6f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CompareValue(right?.let(text) ?: EMPTY, -compare, Modifier.weight(1f))
    }
}

@Composable
/** A value of a row: in bold green when [compare] is 1, the better one, and in red when it is -1, the worse one. */
private fun CompareValue(value: String, compare: Int, modifier: Modifier) {
    Text(
        value,
        modifier,
        style = MaterialTheme.typography.titleLarge.inTextFont(),
        fontWeight = if (compare > 0) FontWeight.Bold else FontWeight.Normal,
        color = when {
            compare > 0 -> MtsTheme.colors.gain
            compare < 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.Center,
    )
}

/** The first name of a player over a column of numbers. */
@Composable
private fun ColumnName(profile: PlayerProfile?, modifier: Modifier) {
    Text(
        profile?.name?.substringBefore(' ') ?: EMPTY,
        modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The place at a table, with a trophy in the color of the place, and the table points under it. */
@Composable
private fun TablePlace(position: Int, points: Double, modifier: Modifier) {
    val description = stringResource(Res.string.h2h_place, position)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MtsTheme.spacing.xs)) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                Modifier.size(24.dp),
                tint = MtsTheme.colors.position(position, MaterialTheme.colorScheme.error),
            )
            Text(position.toString(), style = MaterialTheme.typography.titleLarge.inTextFont(), fontWeight = FontWeight.Bold)
        }
        Text(
            pluralStringResource(Res.plurals.points_short, if (points == 1.0) 1 else 2, formatPoints(points)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun whole(value: Double): String = value.roundToInt().toString()

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
