package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.Icons
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsage
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.domain.model.UserUsage
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.StaggeredColumns
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private const val TOP_USERS = 10
private const val TOP_TOURNAMENTS = 8

/** A conflict rate above this value gets the warning color. */
private const val CONFLICT_WARNING_RATE = 0.1

private fun Double?.percent(): String = this?.let { "${(it * 100).roundToInt()}%" } ?: "–"
private fun Double?.decimal(): String = this?.let { ((it * 10).roundToInt() / 10.0).toString() } ?: "–"

/** Usage analytics for administrators. It is separate from the game statistics. */
@Composable
fun UsageScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        when (val result = presenter.loadUsageReport()) {
            is AppResult.Success -> {
                report = result.value
                message = null
            }
            is AppResult.Failure -> message = result.error.toUiMessage()
        }
        isLoading = false
    }

    AppScaffold(
        title = "App usage",
        subtitle = report?.generatedAt?.let { "Generated $it" },
        isLoading = isLoading,
        autoFocusFirst = false,
        onBack = { navController.popBackStack() },
    ) {
        ScreenColumn(
            maxWidth = 1100.dp,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            scrollable = true,
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val data = report ?: return@ScreenColumn
            KpiTiles(data)
            StaggeredColumns(minColumnWidth = 440.dp, spacing = 24.dp) {
                HourSection(data.savesByHour)
                UsersSection(data.users)
            }
            TournamentsSection(data.tournaments)
        }
    }
}

@Composable
private fun KpiTiles(data: UsageReport) {
    val totals = data.totals
    val conflictColor = if ((totals.conflictRate ?: 0.0) > CONFLICT_WARNING_RATE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    StaggeredColumns(minColumnWidth = 240.dp, spacing = 12.dp) {
        KpiTile("Saves", totals.saves.toString(), "In ${totals.tournaments} tournaments.")
        KpiTile("Active users", data.users.size.toString(), "Accounts with at least one save.")
        KpiTile("Saves from players", totals.playerShare.percent(), "${totals.playerSaves} by players, ${totals.editorSaves} by editors.") {
            SplitBar(
                listOf(
                    totals.playerSaves to MaterialTheme.colorScheme.primary,
                    totals.editorSaves to MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        KpiTile(
            "Conflict rate",
            totals.conflictRate.percent(),
            "${totals.conflicts} refused saves and ${totals.resets} table resets.",
            valueColor = conflictColor,
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    caption: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    chart: (@Composable () -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
            chart?.invoke()
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One bar split in parts. The width of each part follows its share of the sum. */
@Composable
private fun SplitBar(parts: List<Pair<Int, Color>>, modifier: Modifier = Modifier) {
    val sum = parts.sumOf { it.first }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        parts.filter { it.first > 0 }.forEach { (amount, color) ->
            Box(Modifier.weight(amount.toFloat() / sum).height(8.dp).background(color))
        }
    }
}

@Composable
private fun HourSection(savesByHour: List<Int>) {
    val peak = savesByHour.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
    StatSection("When people save") {
        Spacer(Modifier.height(12.dp))
        Text(
            peak?.let { "The busiest hour is ${it.index}:00 UTC with ${it.value} saves." } ?: "No saves yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (peak != null) {
            Spacer(Modifier.height(12.dp))
            HourBars(savesByHour, peak.index)
        }
    }
}

/** One bar for each hour of the day. The busiest hour has the strong color. */
@Composable
private fun HourBars(values: List<Int>, peakIndex: Int) {
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val high = values.max().coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / values.size
        val barWidth = slot * 0.7f
        values.forEachIndexed { hour, value ->
            val barHeight = size.height * value / high
            drawRoundRect(
                color = if (hour == peakIndex) strong else soft,
                topLeft = Offset(slot * hour + (slot - barWidth) / 2, size.height - barHeight),
                size = Size(barWidth, barHeight.coerceAtLeast(if (value > 0) 2.dp.toPx() else 0f)),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("0:00", "6:00", "12:00", "18:00", "23:00").forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsersSection(users: List<UserUsage>) {
    StatSection("Most active users") {
        if (users.isEmpty()) AppEmptyState(StatisticsIcon, "No saves yet", "The most active users show here after the first save.", compact = true)
        val top = users.take(TOP_USERS)
        val high = top.maxOfOrNull { it.saves }?.coerceAtLeast(1) ?: 1
        top.forEach { user ->
            RankedBarRow(
                label = user.name ?: user.uid,
                tag = if (user.source == "editor") "Editor" else "Player",
                value = user.saves,
                fraction = user.saves.toFloat() / high,
            )
        }
        if (users.size > top.size) {
            Text(
                "${users.size - top.size} more users have fewer saves.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankedBarRow(label: String, tag: String, value: Int, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(value.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        ProgressBar(fraction, MaterialTheme.colorScheme.primary)
    }
}

/** A thin bar that fills [fraction] of its width. */
@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

@Composable
private fun TournamentsSection(tournaments: List<TournamentUsage>) {
    var showAll by remember { mutableStateOf(false) }
    val sorted = remember(tournaments) { tournaments.sortedByDescending { it.saves } }
    val shown = if (showAll) sorted else sorted.take(TOP_TOURNAMENTS)
    StatSection("Tournaments by saves") {
        if (sorted.isEmpty()) AppEmptyState(Icons.Default.EmojiEvents, "No saves yet", "The tournaments show here after the first save.", compact = true)
        shown.forEach { TournamentRow(it) }
        if (sorted.size > TOP_TOURNAMENTS) {
            Text(
                if (showAll) "Show fewer tournaments" else "Show all ${sorted.size} tournaments",
                Modifier.clickable { showAll = !showAll }.padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The summary of one tournament. A click shows or hides the details. */
@Composable
private fun TournamentRow(item: TournamentUsage) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name.ifBlank { item.tournamentId },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${item.saves} saves", style = MaterialTheme.typography.bodyLarge)
            }
            ProgressBar(
                if (item.tables == 0) 0f else item.completedTables.toFloat() / item.tables,
                MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "${item.completedTables} of ${item.tables} tables completed. " +
                    "${item.assignedSlots} of ${item.slots} players assigned. " +
                    "${item.conflicts} conflicts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.saves > 0) {
                SplitBar(
                    listOf(
                        item.playerSaves to MaterialTheme.colorScheme.primary,
                        item.editorSaves to MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                StatRow("Saves by players", item.playerSaves.toString())
                StatRow("Saves by editors", item.editorSaves.toString())
                StatRow("Saves per table", item.savesPerTouchedTable.decimal())
                StatRow("Tables with hands", item.handsTables.toString())
                StatRow("Tables with totals only", item.totalsOnlyTables.toString())
                StatRow("Table resets", item.resets.toString())
                StatRow("Players with account", item.slotsWithAccount.toString())
                StatRow("Invitations accepted", item.invitationsAccepted.toString())
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
