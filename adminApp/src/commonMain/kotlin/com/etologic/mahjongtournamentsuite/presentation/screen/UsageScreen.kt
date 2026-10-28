package com.etologic.mahjongtournamentsuite.presentation.screen

import org.jetbrains.compose.resources.StringResource
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
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
import com.etologic.mahjongtournamentsuite.presentation.components.StaggeredColumns
import com.etologic.mahjongtournamentsuite.presentation.components.StatRow
import com.etologic.mahjongtournamentsuite.presentation.components.StatSection
import com.etologic.mahjongtournamentsuite.presentation.components.StatisticsIcon
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlin.math.roundToInt
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.app_usage
import mahjongtournamentsuite.adminapp.generated.resources.period
import mahjongtournamentsuite.adminapp.generated.resources.role_admin
import mahjongtournamentsuite.adminapp.generated.resources.role_editor
import mahjongtournamentsuite.adminapp.generated.resources.role_player
import mahjongtournamentsuite.adminapp.generated.resources.status_finished
import mahjongtournamentsuite.adminapp.generated.resources.status_live
import mahjongtournamentsuite.adminapp.generated.resources.status_upcoming
import mahjongtournamentsuite.adminapp.generated.resources.usage_accounts_status
import mahjongtournamentsuite.adminapp.generated.resources.usage_accounts_summary
import mahjongtournamentsuite.adminapp.generated.resources.usage_active_users
import mahjongtournamentsuite.adminapp.generated.resources.usage_by_saves
import mahjongtournamentsuite.adminapp.generated.resources.usage_conflict_caption
import mahjongtournamentsuite.adminapp.generated.resources.usage_conflict_rate
import mahjongtournamentsuite.adminapp.generated.resources.usage_counts_note
import mahjongtournamentsuite.adminapp.generated.resources.usage_dates_range
import mahjongtournamentsuite.adminapp.generated.resources.usage_filter_all
import mahjongtournamentsuite.adminapp.generated.resources.usage_filter_chip
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_note
import mahjongtournamentsuite.adminapp.generated.resources.usage_funnel_title
import mahjongtournamentsuite.adminapp.generated.resources.usage_generated
import mahjongtournamentsuite.adminapp.generated.resources.usage_heatmap_explanation
import mahjongtournamentsuite.adminapp.generated.resources.usage_last_days
import mahjongtournamentsuite.adminapp.generated.resources.usage_last_use
import mahjongtournamentsuite.adminapp.generated.resources.usage_more_countries
import mahjongtournamentsuite.adminapp.generated.resources.usage_never_used
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_countries
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_match_data
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_saves_days
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_saves_period
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_tournaments_message
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_users
import mahjongtournamentsuite.adminapp.generated.resources.usage_no_users_filter
import mahjongtournamentsuite.adminapp.generated.resources.usage_not_used
import mahjongtournamentsuite.adminapp.generated.resources.usage_not_used_days
import mahjongtournamentsuite.adminapp.generated.resources.usage_players_saves
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_conflicts
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_dates
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_hours
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_with_password
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_resets
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_saves_editors
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_saves_per_table
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_saves_players
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_saves_total
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_tables_hands
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_tables_totals
import mahjongtournamentsuite.adminapp.generated.resources.usage_row_with_account
import mahjongtournamentsuite.adminapp.generated.resources.usage_saves_by_day
import mahjongtournamentsuite.adminapp.generated.resources.usage_saves_in_days
import mahjongtournamentsuite.adminapp.generated.resources.usage_saves_players
import mahjongtournamentsuite.adminapp.generated.resources.usage_saves_split
import mahjongtournamentsuite.adminapp.generated.resources.usage_saves_summary
import mahjongtournamentsuite.adminapp.generated.resources.usage_setup_caption
import mahjongtournamentsuite.adminapp.generated.resources.usage_setup_work
import mahjongtournamentsuite.adminapp.generated.resources.usage_show_all_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.usage_show_all_users
import mahjongtournamentsuite.adminapp.generated.resources.usage_show_fewer_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.usage_show_fewer_users
import mahjongtournamentsuite.adminapp.generated.resources.usage_status_active
import mahjongtournamentsuite.adminapp.generated.resources.usage_status_disabled
import mahjongtournamentsuite.adminapp.generated.resources.usage_status_dormant
import mahjongtournamentsuite.adminapp.generated.resources.usage_status_never
import mahjongtournamentsuite.adminapp.generated.resources.usage_status_visitor
import mahjongtournamentsuite.adminapp.generated.resources.usage_table_saves
import mahjongtournamentsuite.adminapp.generated.resources.usage_tournament_summary
import mahjongtournamentsuite.adminapp.generated.resources.usage_tournament_summary_country
import mahjongtournamentsuite.adminapp.generated.resources.usage_tournaments_activity
import mahjongtournamentsuite.adminapp.generated.resources.usage_tournaments_count
import mahjongtournamentsuite.adminapp.generated.resources.usage_tournaments_created
import mahjongtournamentsuite.adminapp.generated.resources.usage_user_last
import mahjongtournamentsuite.adminapp.generated.resources.usage_user_work
import mahjongtournamentsuite.adminapp.generated.resources.usage_users_count
import mahjongtournamentsuite.adminapp.generated.resources.usage_what_stands_out
import mahjongtournamentsuite.adminapp.generated.resources.usage_what_used
import mahjongtournamentsuite.adminapp.generated.resources.usage_when_saved
import mahjongtournamentsuite.adminapp.generated.resources.usage_where
import mahjongtournamentsuite.adminapp.generated.resources.usage_who_uses
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_fri
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_mon
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_sat
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_sun
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_thu
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_tue
import mahjongtournamentsuite.adminapp.generated.resources.weekday_short_wed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val PERIODS = listOf(7, 30, 90)
private const val DEFAULT_PERIOD = 30
private const val TOP_USERS = 12
private const val TOP_TOURNAMENTS = 8
private const val TOP_COUNTRIES = 8
@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(Res.string.weekday_short_mon), stringResource(Res.string.weekday_short_tue), stringResource(Res.string.weekday_short_wed), stringResource(Res.string.weekday_short_thu), stringResource(Res.string.weekday_short_fri), stringResource(Res.string.weekday_short_sat), stringResource(Res.string.weekday_short_sun),
)

private fun Double?.percent(): String = this?.percentText() ?: "–"
private fun Double?.decimal(): String = this?.let { ((it * 10).roundToInt() / 10.0).toString() } ?: "–"

/** Usage analytics for administrators: who uses the apps, what they use, when, and where. */
@Composable
fun UsageScreen(navController: NavHostController) {
    val presenter = koinInject<UsersPresenter>()
    var days by remember { mutableStateOf(DEFAULT_PERIOD) }
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(days) {
        isLoading = true
        when (val result = presenter.loadUsageReport(days)) {
            is AppResult.Success -> {
                report = result.value
                message = null
            }
            is AppResult.Failure -> message = result.error.toUiText()
        }
        isLoading = false
    }

    AppScaffold(
        title = stringResource(Res.string.app_usage),
        subtitle = report?.generatedAt?.let { stringResource(Res.string.usage_generated, it) },
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
        Text(stringResource(Res.string.period), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PERIODS.forEach { period ->
            FilterChip(selected = selected == period, onClick = { onSelect(period) }, label = { Text(stringResource(Res.string.usage_last_days, period)) })
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
        KpiTile(stringResource(Res.string.usage_active_users), current.activeUsers.toString(), changeText(current.activeUsers, previous.activeUsers, days))
        KpiTile(stringResource(Res.string.usage_table_saves), current.saves.toString(), changeText(current.saves, previous.saves, days))
        KpiTile(
            stringResource(Res.string.usage_tournaments_activity),
            current.activeTournaments.toString(),
            UiText.of(Res.string.usage_tournaments_created, current.tournamentsCreated, changeText(current.activeTournaments, previous.activeTournaments, days)),
        )
        KpiTile(stringResource(Res.string.usage_saves_players), current.playerShare.percent(), UiText.of(Res.string.usage_saves_split, current.playerSaves, current.editorSaves)) {
            SplitBar(
                listOf(
                    current.playerSaves to MaterialTheme.colorScheme.primary,
                    current.editorSaves to MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        KpiTile(
            stringResource(Res.string.usage_setup_work),
            current.actions.toString(),
            UiText.of(Res.string.usage_setup_caption, changeText(current.actions, previous.actions, days)),
        )
        KpiTile(
            stringResource(Res.string.usage_conflict_rate),
            current.conflictRate.percent(),
            UiText.of(Res.string.usage_conflict_caption, current.conflicts, current.resets),
            valueColor = conflictColor,
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    caption: UiText,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    chart: (@Composable () -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
            chart?.invoke()
            Text(caption.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightsSection(data: UsageReport) {
    val insights = remember(data) { usageInsights(data) }
    if (insights.isEmpty()) return
    StatSection(stringResource(Res.string.usage_what_stands_out)) {
        insights.forEach { insight ->
            val color = if (insight.level == InsightLevel.WARNING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Text(insight.text.asString(), style = MaterialTheme.typography.bodyLarge)
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
    StatSection(stringResource(Res.string.usage_saves_by_day)) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (total == 0) stringResource(Res.string.usage_no_saves_days, data.periodDays) else stringResource(Res.string.usage_saves_summary, total, activeDays, data.periodDays),
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
    StatSection(stringResource(Res.string.usage_when_saved)) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (high == 0) stringResource(Res.string.usage_no_saves_period) else stringResource(Res.string.usage_heatmap_explanation),
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
    val weekdayLabels = weekdayLabels()
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.width(36.dp).height(147.dp), verticalArrangement = Arrangement.SpaceBetween) {
            weekdayLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

private enum class UserFilter(val label: StringResource, val matches: (UserUsage) -> Boolean) {
    ALL(Res.string.usage_filter_all, { true }),
    ACTIVE(Res.string.usage_status_active, { it.status == UserStatus.ACTIVE }),
    VISITOR(Res.string.usage_status_visitor, { it.status == UserStatus.VISITOR }),
    DORMANT(Res.string.usage_status_dormant, { it.status == UserStatus.DORMANT }),
    NEVER(Res.string.usage_status_never, { it.status == UserStatus.NEVER }),
}

@Composable
private fun UserStatus.label(): String = stringResource(
    when (this) {
        UserStatus.ACTIVE -> Res.string.usage_status_active
        UserStatus.VISITOR -> Res.string.usage_status_visitor
        UserStatus.DORMANT -> Res.string.usage_status_dormant
        UserStatus.NEVER -> Res.string.usage_status_never
        UserStatus.DISABLED -> Res.string.usage_status_disabled
    },
)

@Composable
private fun UserStatus.color(): Color = when (this) {
    UserStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    UserStatus.VISITOR -> MaterialTheme.colorScheme.tertiary
    UserStatus.DORMANT -> MaterialTheme.colorScheme.outline
    UserStatus.NEVER, UserStatus.DISABLED -> MaterialTheme.colorScheme.error
}

@Composable
private fun String.roleLabel(): String = when (uppercase()) {
    "ADMIN" -> stringResource(Res.string.role_admin)
    "EDITOR" -> stringResource(Res.string.role_editor)
    "PLAYER" -> stringResource(Res.string.role_player)
    else -> lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun UsersSection(accounts: UsageAccounts, users: List<UserUsage>, days: Int) {
    var filter by remember { mutableStateOf(UserFilter.ALL) }
    var showAll by remember { mutableStateOf(false) }
    val filtered = remember(users, filter) { users.filter(filter.matches) }
    val shown = if (showAll) filtered else filtered.take(TOP_USERS)
    StatSection(stringResource(Res.string.usage_who_uses)) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.usage_accounts_summary, accounts.total, accounts.admins, accounts.editors, accounts.players),
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
            stringResource(Res.string.usage_accounts_status, accounts.active, accounts.visitors, accounts.dormant, accounts.neverSignedIn, accounts.disabled, days),
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option; showAll = false },
                    label = { Text(stringResource(Res.string.usage_filter_chip, stringResource(option.label), users.count(option.matches))) },
                )
            }
        }
        if (filtered.isEmpty()) AppEmptyState(StatisticsIcon, stringResource(Res.string.usage_no_users), stringResource(Res.string.usage_no_users_filter), compact = true)
        shown.forEach { UserRow(it) }
        if (filtered.size > TOP_USERS) ShowMoreToggle(showAll, Res.string.usage_show_fewer_users, Res.string.usage_show_all_users, filtered.size) { showAll = !showAll }
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
            stringResource(Res.string.usage_user_work, user.saves, user.actions, user.tournaments, user.activeDays) + " "
        } else {
            ""
        }
        Text(
            work + stringResource(Res.string.usage_user_last, agoText(user.lastActiveDaysAgo).asString(), agoText(user.lastSeenDaysAgo).asString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ShowMoreToggle(showAll: Boolean, fewer: StringResource, all: StringResource, total: Int, onToggle: () -> Unit) {
    Text(
        if (showAll) stringResource(fewer) else stringResource(all, total),
        Modifier.clickable(onClick = onToggle).padding(vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FeaturesSection(features: List<FeatureUsage>, days: Int) {
    StatSection(stringResource(Res.string.usage_what_used)) {
        Text(
            stringResource(Res.string.usage_counts_note, days),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        features.groupBy { it.group }.forEach { (group, items) ->
            Text(groupLabelText(group).asString(), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                featureLabelText(feature).asString(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (unused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    unused && feature.lastUsedDaysAgo == null && feature.isPeriod -> stringResource(Res.string.usage_never_used)
                    unused && feature.isPeriod -> stringResource(Res.string.usage_not_used_days, days)
                    unused -> stringResource(Res.string.usage_not_used)
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
            feature.users?.takeIf { it > 0 }?.let { add(stringResource(Res.string.usage_users_count, it)) }
            if (feature.isPeriod && feature.lastUsedDaysAgo != null) add(stringResource(Res.string.usage_last_use, agoText(feature.lastUsedDaysAgo).asString()))
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
    StatSection(stringResource(Res.string.usage_funnel_title)) {
        Text(
            stringResource(Res.string.usage_funnel_note),
            Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        funnel.forEach { step ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(funnelLabelText(step).asString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
    StatSection(stringResource(Res.string.usage_where)) {
        if (top.isEmpty()) AppEmptyState(Icons.Default.EmojiEvents, stringResource(Res.string.usage_no_tournaments), stringResource(Res.string.usage_no_countries), compact = true)
        top.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(item.country, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(Res.string.usage_tournaments_count, item.tournaments), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                ProgressBar(item.tournaments.toFloat() / high, MaterialTheme.colorScheme.tertiary)
                Text(
                    stringResource(Res.string.usage_players_saves, item.players, item.saves),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (countries.size > top.size) {
            Text(
                stringResource(Res.string.usage_more_countries, countries.size - top.size),
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
    StatSection(stringResource(Res.string.usage_by_saves)) {
        if (tournaments.isEmpty()) AppEmptyState(Icons.Default.EmojiEvents, stringResource(Res.string.usage_no_tournaments), stringResource(Res.string.usage_no_tournaments_message), compact = true)
        shown.forEach { TournamentRow(it, days) }
        if (tournaments.size > TOP_TOURNAMENTS) ShowMoreToggle(showAll, Res.string.usage_show_fewer_tournaments, Res.string.usage_show_all_tournaments, tournaments.size) { showAll = !showAll }
    }
}

@Composable
private fun TournamentUsageStatus.label(): String = stringResource(
    when (this) {
        TournamentUsageStatus.UPCOMING -> Res.string.status_upcoming
        TournamentUsageStatus.LIVE -> Res.string.status_live
        TournamentUsageStatus.DONE -> Res.string.status_finished
    },
)

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
                    if (noData) stringResource(Res.string.usage_no_match_data) else stringResource(Res.string.usage_saves_in_days, item.savesInPeriod, days),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (noData) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            ProgressBar(
                if (item.tables == 0) 0f else item.completedTables.toFloat() / item.tables,
                MaterialTheme.colorScheme.tertiary,
            )
            Text(
                if (item.country.isNotBlank()) {
                    stringResource(
                        Res.string.usage_tournament_summary_country, item.status.label(), item.country, item.completedTables, item.tables,
                        item.assignedSlots, item.slots, agoText(item.lastActivityDaysAgo).asString(),
                    )
                } else {
                    stringResource(
                        Res.string.usage_tournament_summary, item.status.label(), item.completedTables, item.tables,
                        item.assignedSlots, item.slots, agoText(item.lastActivityDaysAgo).asString(),
                    )
                },
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
                StatRow(stringResource(Res.string.usage_row_dates), stringResource(Res.string.usage_dates_range, item.startDate, item.endDate))
                StatRow(stringResource(Res.string.usage_row_saves_total), item.saves.toString())
                StatRow(stringResource(Res.string.usage_row_saves_players), item.playerSaves.toString())
                StatRow(stringResource(Res.string.usage_row_saves_editors), item.editorSaves.toString())
                StatRow(stringResource(Res.string.usage_row_saves_per_table), item.savesPerTouchedTable.decimal())
                StatRow(stringResource(Res.string.usage_row_hours), item.activeHours.decimal())
                StatRow(stringResource(Res.string.usage_row_tables_hands), item.handsTables.toString())
                StatRow(stringResource(Res.string.usage_row_tables_totals), item.totalsOnlyTables.toString())
                StatRow(stringResource(Res.string.usage_row_conflicts), item.conflicts.toString())
                StatRow(stringResource(Res.string.usage_row_resets), item.resets.toString())
                StatRow(stringResource(Res.string.usage_row_with_account), item.slotsWithAccount.toString())
                StatRow(stringResource(Res.string.usage_row_with_password), item.slotsWithPassword.toString())
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
