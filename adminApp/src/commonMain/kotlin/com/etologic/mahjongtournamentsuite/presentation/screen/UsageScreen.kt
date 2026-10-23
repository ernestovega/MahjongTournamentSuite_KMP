package com.etologic.mahjongtournamentsuite.presentation.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.FilterChip
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
import com.etologic.mahjongtournamentsuite.domain.model.CountryUsage
import com.etologic.mahjongtournamentsuite.domain.model.DailyUsage
import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.FunnelStep
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsage
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsageStatus
import com.etologic.mahjongtournamentsuite.domain.model.UsageAccounts
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.domain.model.UserStatus
import com.etologic.mahjongtournamentsuite.domain.model.UserUsage
import com.etologic.mahjongtournamentsuite.presentation.components.AppEmptyState
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.AppScaffold
import com.etologic.mahjongtournamentsuite.presentation.components.ScreenColumn
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.components.StaggeredColumns
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiMessage
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val PERIODS = listOf(7, 30, 90)
private const val DEFAULT_PERIOD = 30
private const val TOP_USERS = 12
private const val TOP_TOURNAMENTS = 8
private const val TOP_COUNTRIES = 8
private val WEEKDAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun Double?.percent(): String = this?.percentText() ?: "–"
private fun Double?.decimal(): String = this?.let { ((it * 10).roundToInt() / 10.0).toString() } ?: "–"

/** Usage analytics for administrators: who uses the apps, what they use, when, and where. */
@Composable
fun UsageScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    var days by remember { mutableStateOf(DEFAULT_PERIOD) }
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(days) {
        isLoading = true
        when (val result = presenter.loadUsageReport(days)) {
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
            message?.let { AppErrorDialog(it, onDismiss = { message = null }) }
            PeriodChips(days) { days = it }
            val data = report ?: return@ScreenColumn
            KpiTiles(data)
            InsightsSection(data)
            StaggeredColumns(minColumnWidth = 480.dp, spacing = 24.dp) {
                TrendSection(data)
                HeatmapSection(data.heatmap)
            }
            UsersSection(data.accounts, data.users, data.periodDays)
            FeaturesSection(data.features, data.periodDays)
            StaggeredColumns(minColumnWidth = 480.dp, spacing = 24.dp) {
                FunnelSection(data.funnel)
                CountriesSection(data.countries)
            }
            TournamentsSection(data.tournaments, data.periodDays)
        }
    }
}

@Composable
private fun PeriodChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Period", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PERIODS.forEach { period ->
            FilterChip(selected = selected == period, onClick = { onSelect(period) }, label = { Text("Last $period days") })
        }
    }
}

@Composable
private fun KpiTiles(data: UsageReport) {
    val current = data.current
    val previous = data.previous
    val days = data.periodDays
    val conflictColor = if ((current.conflictRate ?: 0.0) > CONFLICT_WARNING_RATE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    StaggeredColumns(minColumnWidth = 240.dp, spacing = 12.dp) {
        KpiTile("Active users", current.activeUsers.toString(), changeText(current.activeUsers, previous.activeUsers, days))
        KpiTile("Table saves", current.saves.toString(), changeText(current.saves, previous.saves, days))
        KpiTile(
            "Tournaments with activity",
            current.activeTournaments.toString(),
            "${current.tournamentsCreated} created in this period. ${changeText(current.activeTournaments, previous.activeTournaments, days)}",
        )
        KpiTile("Saves from players", current.playerShare.percent(), "${current.playerSaves} by players, ${current.editorSaves} by editors.") {
            SplitBar(
                listOf(
                    current.playerSaves to MaterialTheme.colorScheme.primary,
                    current.editorSaves to MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        KpiTile(
            "Setup work",
            current.actions.toString(),
            "Changes to settings, players, teams and invitations. ${changeText(current.actions, previous.actions, days)}",
        )
        KpiTile(
            "Conflict rate",
            current.conflictRate.percent(),
            "${current.conflicts} refused saves and ${current.resets} table resets.",
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

@Composable
private fun InsightsSection(data: UsageReport) {
    val insights = remember(data) { usageInsights(data) }
    if (insights.isEmpty()) return
    StatSection("What stands out") {
        insights.forEach { insight ->
            val color = if (insight.level == InsightLevel.WARNING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Text(insight.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** One bar split in parts. The width of each part follows its share of the sum. */
@Composable
private fun SplitBar(parts: List<Pair<Int, Color>>, modifier: Modifier = Modifier) {
    val sum = parts.sumOf { it.first }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        parts.filter { it.first > 0 }.forEach { (amount, color) ->
            Box(Modifier.weight(amount.toFloat() / sum).height(8.dp).background(color))
        }
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
private fun TrendSection(data: UsageReport) {
    val total = data.daily.sumOf { it.saves }
    val activeDays = data.daily.count { it.saves > 0 }
    StatSection("Table saves by day") {
        Spacer(Modifier.height(12.dp))
        Text(
            if (total == 0) "No saves in the last ${data.periodDays} days." else "$total saves on $activeDays of ${data.periodDays} days.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            DayBars(data.daily)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(data.daily.first().date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.daily.last().date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One bar for each day. The bar of the busiest day has the strong color. */
@Composable
private fun DayBars(daily: List<DailyUsage>) {
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val high = daily.maxOf { it.saves }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / daily.size
        val barWidth = slot * 0.7f
        daily.forEachIndexed { index, day ->
            val barHeight = size.height * day.saves / high
            drawRoundRect(
                color = if (day.saves == high) strong else soft,
                topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - barHeight),
                size = Size(barWidth, barHeight.coerceAtLeast(if (day.saves > 0) 2.dp.toPx() else 0f)),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun HeatmapSection(heatmap: List<List<Int>>) {
    val high = heatmap.maxOfOrNull { row -> row.maxOrNull() ?: 0 } ?: 0
    StatSection("When tables are saved") {
        Spacer(Modifier.height(12.dp))
        Text(
            if (high == 0) "No saves in this period." else "Each cell is one hour of one weekday. A darker cell has more saves. The time is local to each tournament.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (high > 0) {
            Spacer(Modifier.height(12.dp))
            Heatmap(heatmap, high)
            Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0:00", "6:00", "12:00", "18:00", "23:00").forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Heatmap(heatmap: List<List<Int>>, high: Int) {
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.width(36.dp).height(147.dp), verticalArrangement = Arrangement.SpaceBetween) {
            WEEKDAY_LABELS.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Canvas(Modifier.fillMaxWidth().height(147.dp)) {
            val cellWidth = size.width / 24
            val cellHeight = size.height / 7
            heatmap.forEachIndexed { day, hours ->
                hours.forEachIndexed { hour, value ->
                    drawRoundRect(
                        color = if (value == 0) empty.copy(alpha = 0.5f) else color.copy(alpha = 0.2f + 0.8f * value / high),
                        topLeft = Offset(cellWidth * hour + 1.dp.toPx(), cellHeight * day + 1.dp.toPx()),
                        size = Size(cellWidth - 2.dp.toPx(), cellHeight - 2.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private enum class UserFilter(val label: String, val matches: (UserUsage) -> Boolean) {
    ALL("All", { true }),
    ACTIVE("Active", { it.status == UserStatus.ACTIVE }),
    VISITOR("Signed in only", { it.status == UserStatus.VISITOR }),
    DORMANT("Dormant", { it.status == UserStatus.DORMANT }),
    NEVER("Never signed in", { it.status == UserStatus.NEVER }),
}

private fun UserStatus.label(): String = when (this) {
    UserStatus.ACTIVE -> "Active"
    UserStatus.VISITOR -> "Signed in only"
    UserStatus.DORMANT -> "Dormant"
    UserStatus.NEVER -> "Never signed in"
    UserStatus.DISABLED -> "Disabled"
}

@Composable
private fun UserStatus.color(): Color = when (this) {
    UserStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    UserStatus.VISITOR -> MaterialTheme.colorScheme.tertiary
    UserStatus.DORMANT -> MaterialTheme.colorScheme.outline
    UserStatus.NEVER, UserStatus.DISABLED -> MaterialTheme.colorScheme.error
}

private fun String.roleLabel(): String = lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun UsersSection(accounts: UsageAccounts, users: List<UserUsage>, days: Int) {
    var filter by remember { mutableStateOf(UserFilter.ALL) }
    var showAll by remember { mutableStateOf(false) }
    val filtered = remember(users, filter) { users.filter(filter.matches) }
    val shown = if (showAll) filtered else filtered.take(TOP_USERS)
    StatSection("Who uses the apps") {
        Spacer(Modifier.height(12.dp))
        Text(
            "${accounts.total} accounts: ${accounts.admins} administrators, ${accounts.editors} editors and ${accounts.players} players.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        SplitBar(
            listOf(
                accounts.active to UserStatus.ACTIVE.color(),
                accounts.visitors to UserStatus.VISITOR.color(),
                accounts.dormant to UserStatus.DORMANT.color(),
                accounts.neverSignedIn to UserStatus.NEVER.color(),
            ),
        )
        Text(
            "${accounts.active} active, ${accounts.visitors} signed in only, ${accounts.dormant} dormant, " +
                "${accounts.neverSignedIn} never signed in and ${accounts.disabled} disabled. " +
                "Active means work in the last $days days.",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option; showAll = false },
                    label = { Text("${option.label} (${users.count(option.matches)})") },
                )
            }
        }
        if (filtered.isEmpty()) AppEmptyState(StatisticsIcon, "No users", "No account matches this filter.", compact = true)
        shown.forEach { UserRow(it) }
        if (filtered.size > TOP_USERS) ShowMoreToggle(showAll, "users", filtered.size) { showAll = !showAll }
    }
}

@Composable
private fun UserRow(user: UserUsage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(user.name ?: user.uid, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(user.role.roleLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(user.status.label(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = user.status.color())
        }
        val work = if (user.saves > 0 || user.actions > 0) {
            "${user.saves} saves and ${user.actions} setup changes in ${user.tournaments} tournaments on ${user.activeDays} days. "
        } else {
            ""
        }
        Text(
            work + "Last work ${agoText(user.lastActiveDaysAgo)}. Last seen ${agoText(user.lastSeenDaysAgo)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ShowMoreToggle(showAll: Boolean, noun: String, total: Int, onToggle: () -> Unit) {
    Text(
        if (showAll) "Show fewer $noun" else "Show all $total $noun",
        Modifier.clickable(onClick = onToggle).padding(vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FeaturesSection(features: List<FeatureUsage>, days: Int) {
    StatSection("What is used and what is not") {
        Text(
            "Counts of uses cover the last $days days. Setup and table counts cover all time.",
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        features.groupBy { it.group }.forEach { (group, items) ->
            Text(group, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            items.sortedByDescending { it.uses }.forEach { FeatureRow(it, days) }
        }
    }
}

@Composable
private fun FeatureRow(feature: FeatureUsage, days: Int) {
    val unused = feature.uses == 0
    val adoption = feature.adoption
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                feature.label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (unused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    unused && feature.lastUsedDaysAgo == null && feature.isPeriod -> "Never used"
                    unused && feature.isPeriod -> "Not used in $days days"
                    unused -> "Not used"
                    adoption != null -> "${feature.uses} · ${adoption.percentText()}"
                    else -> feature.uses.toString()
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (unused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (adoption != null) ProgressBar(adoption.toFloat(), MaterialTheme.colorScheme.primary)
        val details = buildList {
            feature.users?.takeIf { it > 0 }?.let { add("$it users") }
            if (feature.isPeriod && feature.lastUsedDaysAgo != null) add("last use ${agoText(feature.lastUsedDaysAgo)}")
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(", ").replaceFirstChar { it.uppercase() } + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FunnelSection(funnel: List<FunnelStep>) {
    val first = funnel.firstOrNull()?.count?.coerceAtLeast(1) ?: 1
    StatSection("How far tournaments go") {
        Text(
            "Each bar counts the tournaments that reached the step, out of all tournaments created.",
            Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        funnel.forEach { step ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(step.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${step.count} · ${(step.count.toDouble() / first).percentText()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                ProgressBar(step.count.toFloat() / first, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CountriesSection(countries: List<CountryUsage>) {
    val top = countries.take(TOP_COUNTRIES)
    val high = top.maxOfOrNull { it.tournaments }?.coerceAtLeast(1) ?: 1
    StatSection("Where tournaments are") {
        if (top.isEmpty()) AppEmptyState(Icons.Default.EmojiEvents, "No tournaments", "The countries show here after the first tournament.", compact = true)
        top.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(item.country, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${item.tournaments} tournaments", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                ProgressBar(item.tournaments.toFloat() / high, MaterialTheme.colorScheme.tertiary)
                Text(
                    "${item.players} players and ${item.saves} saves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (countries.size > top.size) {
            Text(
                "${countries.size - top.size} more countries have fewer tournaments.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TournamentsSection(tournaments: List<TournamentUsage>, days: Int) {
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) tournaments else tournaments.take(TOP_TOURNAMENTS)
    StatSection("Tournaments by recent saves") {
        if (tournaments.isEmpty()) AppEmptyState(Icons.Default.EmojiEvents, "No tournaments", "The tournaments show here after the first one is created.", compact = true)
        shown.forEach { TournamentRow(it, days) }
        if (tournaments.size > TOP_TOURNAMENTS) ShowMoreToggle(showAll, "tournaments", tournaments.size) { showAll = !showAll }
    }
}

private fun TournamentUsageStatus.label(): String = when (this) {
    TournamentUsageStatus.UPCOMING -> "Upcoming"
    TournamentUsageStatus.LIVE -> "Live"
    TournamentUsageStatus.DONE -> "Finished"
}

/** The summary of one tournament. A click shows or hides the details. */
@Composable
private fun TournamentRow(item: TournamentUsage, days: Int) {
    var expanded by remember { mutableStateOf(false) }
    val noData = item.status != TournamentUsageStatus.UPCOMING && item.saves == 0
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
                Text(
                    if (noData) "No match data" else "${item.savesInPeriod} saves in $days days",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (noData) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            ProgressBar(
                if (item.tables == 0) 0f else item.completedTables.toFloat() / item.tables,
                MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "${item.status.label()}${if (item.country.isNotBlank()) " in ${item.country}" else ""}. " +
                    "${item.completedTables} of ${item.tables} tables completed. " +
                    "${item.assignedSlots} of ${item.slots} players assigned. " +
                    "Last activity ${agoText(item.lastActivityDaysAgo)}.",
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
                StatRow("Dates", "${item.startDate} to ${item.endDate}")
                StatRow("Saves in total", item.saves.toString())
                StatRow("Saves by players", item.playerSaves.toString())
                StatRow("Saves by editors", item.editorSaves.toString())
                StatRow("Saves per table", item.savesPerTouchedTable.decimal())
                StatRow("Hours between first and last save", item.activeHours.decimal())
                StatRow("Tables with hands", item.handsTables.toString())
                StatRow("Tables with totals only", item.totalsOnlyTables.toString())
                StatRow("Conflicts", item.conflicts.toString())
                StatRow("Table resets", item.resets.toString())
                StatRow("Players with account", item.slotsWithAccount.toString())
                StatRow("Invitations sent", item.invitationsSent.toString())
                StatRow("Invitations accepted", item.invitationsAccepted.toString())
                StatRow("Invitations pending", item.invitationsPending.toString())
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
