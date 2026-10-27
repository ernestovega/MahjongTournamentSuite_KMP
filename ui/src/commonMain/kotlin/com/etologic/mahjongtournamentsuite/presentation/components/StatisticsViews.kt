package com.etologic.mahjongtournamentsuite.presentation.components

import mahjongtournamentsuite.ui.generated.resources.logo_of
import com.etologic.mahjongtournamentsuite.presentation.util.PlaceholderNames
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.MIN_HANDS_FOR_LEADERBOARD
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TableStatistics
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.usecase.PlayerProfile
import com.etologic.mahjongtournamentsuite.presentation.util.formatLocalizedDateRange
import com.etologic.mahjongtournamentsuite.presentation.util.formatPoints
import kotlin.math.roundToInt
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.chart_no_data
import mahjongtournamentsuite.ui.generated.resources.column_deal_ins
import mahjongtournamentsuite.ui.generated.resources.column_place
import mahjongtournamentsuite.ui.generated.resources.column_player
import mahjongtournamentsuite.ui.generated.resources.column_points
import mahjongtournamentsuite.ui.generated.resources.column_score
import mahjongtournamentsuite.ui.generated.resources.column_wins
import mahjongtournamentsuite.ui.generated.resources.country_n
import mahjongtournamentsuite.ui.generated.resources.photo_of
import mahjongtournamentsuite.ui.generated.resources.player_photo_none
import mahjongtournamentsuite.ui.generated.resources.rival_ema
import mahjongtournamentsuite.ui.generated.resources.rival_player
import mahjongtournamentsuite.ui.generated.resources.rival_self_draws
import mahjongtournamentsuite.ui.generated.resources.scope_me
import mahjongtournamentsuite.ui.generated.resources.scope_my_tournaments
import mahjongtournamentsuite.ui.generated.resources.scope_player
import mahjongtournamentsuite.ui.generated.resources.scope_table
import mahjongtournamentsuite.ui.generated.resources.scope_tournament
import mahjongtournamentsuite.ui.generated.resources.stat_best_hand
import mahjongtournamentsuite.ui.generated.resources.stat_chicken_hands
import mahjongtournamentsuite.ui.generated.resources.stat_draws
import mahjongtournamentsuite.ui.generated.resources.stat_hands
import mahjongtournamentsuite.ui.generated.resources.stat_hands_played
import mahjongtournamentsuite.ui.generated.resources.table_no_hands
import mahjongtournamentsuite.ui.generated.resources.table_not_completed
import mahjongtournamentsuite.ui.generated.resources.table_result
import mahjongtournamentsuite.ui.generated.resources.table_score_after_hand
import org.jetbrains.compose.resources.stringResource

/** The icon of every statistics button and tab in both apps. Rankings keep their own icon. */
val StatisticsIcon: ImageVector get() = Icons.Default.Insights

/** Which part of the data a statistics screen shows. Both apps use the same scopes. */
enum class StatisticsScope {
    /** One player over all tournaments: the cards of the whole history. */
    PERSON,

    /** The same player, one card for each tournament. */
    PERSON_TOURNAMENTS,
    TOURNAMENT,
    TABLE,
    PLAYER,
}

@Composable
fun StatisticsScope.title(): String = stringResource(
    when (this) {
        StatisticsScope.PERSON -> Res.string.scope_me
        StatisticsScope.PERSON_TOURNAMENTS -> Res.string.scope_my_tournaments
        StatisticsScope.TOURNAMENT -> Res.string.scope_tournament
        StatisticsScope.TABLE -> Res.string.scope_table
        StatisticsScope.PLAYER -> Res.string.scope_player
    },
)

internal const val EMPTY = "-"

/**
 * The page or section that has nothing to show yet: a big icon, a title and a hint of what to do.
 * A [compact] state is smaller, for a part of a screen, for example one card or one table.
 */
@Composable
fun AppEmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        modifier.fillMaxWidth().padding(vertical = if (compact) 24.dp else 56.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon, contentDescription = null,
                modifier = Modifier.padding(if (compact) 16.dp else 28.dp).size(if (compact) 32.dp else 56.dp),
                // A light icon on the green circle, like the tab icons.
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            message,
            Modifier.widthIn(max = 420.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun Double?.percent(): String = this?.let { "${(it * 100).roundToInt()}%" } ?: EMPTY
internal fun Double?.decimal(): String = this?.let { formatPoints((it * 10).roundToInt() / 10.0) } ?: EMPTY
internal fun Int?.text(): String = this?.toString() ?: EMPTY

/** A group of rows under a plain title. The statistics screens use no cards, so the text stays easy to read. */
@Composable
fun StatSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** One label with its value. The value sits at the end of the row. */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A line chart. Every series has the same number of steps on the horizontal axis at most. */
@Composable
fun LineChart(series: List<Pair<String, List<Int>>>, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val axis = MaterialTheme.colorScheme.outlineVariant
    val steps = series.maxOfOrNull { it.second.size } ?: 0
    if (steps < 2) {
        Text(stringResource(Res.string.chart_no_data), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val all = series.flatMap { it.second } + 0
    val low = all.min()
    val high = all.max()
    val range = (high - low).coerceAtLeast(1).toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val zeroY = size.height * (1f - (0 - low) / range)
            drawLine(axis, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
            series.forEachIndexed { index, (_, values) ->
                val path = Path()
                values.forEachIndexed { step, value ->
                    val x = size.width * step / (steps - 1)
                    val y = size.height * (1f - (value - low) / range)
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colors[index % colors.size], style = Stroke(width = 2.dp.toPx()))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEachIndexed { index, (name, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(12.dp).height(12.dp)) { drawCircle(colors[index % colors.size]) }
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun TableStatisticsPanel(
    statistics: TableStatistics,
    names: Map<Int, String>,
    modifier: Modifier = Modifier,
) {
    @Suppress("NAME_SHADOWING")
    val names = remember(names, PlaceholderNames.playerTemplate) { PlaceholderNames.localizeAll(names) }
    StaggeredColumns(modifier.fillMaxWidth(), minColumnWidth = 380.dp, spacing = 20.dp) {
        if (!statistics.isCompleted) {
            Text(stringResource(Res.string.table_not_completed), style = MaterialTheme.typography.bodyMedium)
            return@StaggeredColumns
        }
        StatSection(stringResource(Res.string.table_result)) {
            TableHeaderRow()
            statistics.players
                .mapNotNull { player -> player.results.firstOrNull()?.let { player to it } }
                .sortedBy { it.second.position }
                .forEach { (player, result) ->
                    TableValuesRow(
                        name = names[player.playerId] ?: stringResource(Res.string.rival_player, player.playerId.toString()),
                        cells = listOf(
                            result.position.toString(),
                            result.score.toString(),
                            formatPoints(result.points),
                            if (statistics.hasHands) player.handsWon.toString() else EMPTY,
                            if (statistics.hasHands) player.dealIns.toString() else EMPTY,
                        ),
                    )
                }
        }
        if (!statistics.hasHands) {
            Text(
                stringResource(Res.string.table_no_hands),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StaggeredColumns
        }
        StatSection(stringResource(Res.string.stat_hands)) {
            StatRow(stringResource(Res.string.stat_hands_played), statistics.handsPlayed.toString())
            StatRow(stringResource(Res.string.stat_draws), statistics.drawnHands.toString())
            StatRow(stringResource(Res.string.rival_self_draws), statistics.selfDraws.toString())
            StatRow(stringResource(Res.string.stat_best_hand), statistics.bestHand.text())
            if (statistics.chickenHands > 0) StatRow(stringResource(Res.string.stat_chicken_hands), statistics.chickenHands.toString())
        }
        StatSection(stringResource(Res.string.table_score_after_hand)) {
            Spacer(Modifier.height(12.dp))
            LineChart(statistics.swing.map { (names[it.playerId] ?: stringResource(Res.string.rival_player, it.playerId.toString())) to listOf(0) + it.scoreAfterHand })
        }
    }
}

@Composable
private fun tableColumns(): List<String> = listOf(
    stringResource(Res.string.column_place), stringResource(Res.string.column_score), stringResource(Res.string.column_points), stringResource(Res.string.column_wins), stringResource(Res.string.column_deal_ins),
)

@Composable
private fun TableHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.column_player), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2.2f))
        tableColumns().forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TableValuesRow(name: String, cells: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(2.2f))
        cells.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private val HeaderImageSize = 64.dp

/** Photo, name, country and EMA number of one player. Shows a person icon when there is no photo. */
@Composable
fun PlayerStatisticsHeader(profile: PlayerProfile, modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    /** A button at the end of the row, for example to change the player. */
    action: (@Composable () -> Unit)? = null,
) {
    val photo: @Composable () -> Unit = {
        if (profile.photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(HeaderImageSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(Res.string.player_photo_none),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(HeaderImageSize * 0.62f),
                )
            }
        } else {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = stringResource(Res.string.photo_of, profile.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(HeaderImageSize).clip(CircleShape),
            )
        }
    }
    val texts: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile.country.isNotBlank()) {
                    CountryFlag(profile.country, width = 24.dp, contentDescription = stringResource(Res.string.country_n, profile.country))
                }
                if (profile.emaId.isNotBlank()) {
                    Text(stringResource(Res.string.rival_ema, profile.emaId), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    HeaderCard(modifier, fillWidth, action) {
        photo()
        texts(Alignment.Start)
    }
}

/** Logo, name and dates of one tournament. */
@Composable
fun TournamentStatisticsHeader(
    tournament: Tournament,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    /** A button at the end of the row, for example to change the tournament. */
    action: (@Composable () -> Unit)? = null,
) {
    HeaderCard(modifier, fillWidth, action) {
        val shape = MaterialTheme.shapes.small
        if (tournament.associationLogoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(HeaderImageSize)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            AsyncImage(
                model = tournament.associationLogoUrl,
                contentDescription = stringResource(Res.string.logo_of, tournament.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(HeaderImageSize).clip(shape),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tournament.name, style = MaterialTheme.typography.titleLarge)
            formatLocalizedDateRange(tournament.eventStartDate, tournament.eventEndDate)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The row of a header. With [fillWidth] false it is as wide as its content.
 * The [action] sits at the end of the row. It moves to a new line when the row is too narrow.
 */
@Composable
private fun HeaderCard(modifier: Modifier, fillWidth: Boolean, action: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    FlowRow(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { content() }
        action?.invoke()
    }
}

/** The player dropdown of one tournament. The names come sorted by name. */
@Composable
fun PlayerDropdownField(
    names: Map<Int, String>,
    selectedId: Int?,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(names) { names.entries.sortedBy { it.value }.map { it.key to it.value } }
    SelectField(stringResource(Res.string.column_player), options, selectedId, onChoose, modifier)
}
