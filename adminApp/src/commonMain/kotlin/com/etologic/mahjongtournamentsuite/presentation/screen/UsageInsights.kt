package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.FeatureUsage
import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsageStatus
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import kotlin.math.roundToInt
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.insight_busiest_time
import mahjongtournamentsuite.adminapp.generated.resources.insight_conflicts
import mahjongtournamentsuite.adminapp.generated.resources.insight_dormant
import mahjongtournamentsuite.adminapp.generated.resources.insight_empty_tournaments
import mahjongtournamentsuite.adminapp.generated.resources.insight_accounts_with_password
import mahjongtournamentsuite.adminapp.generated.resources.insight_never_signed_in
import mahjongtournamentsuite.adminapp.generated.resources.insight_no_saves
import mahjongtournamentsuite.adminapp.generated.resources.insight_player_share
import mahjongtournamentsuite.adminapp.generated.resources.insight_saves_fell
import mahjongtournamentsuite.adminapp.generated.resources.insight_unused_features
import mahjongtournamentsuite.adminapp.generated.resources.insight_unused_features_more
import mahjongtournamentsuite.adminapp.generated.resources.usage_ago_days
import mahjongtournamentsuite.adminapp.generated.resources.usage_ago_months
import mahjongtournamentsuite.adminapp.generated.resources.usage_ago_never
import mahjongtournamentsuite.adminapp.generated.resources.usage_ago_today
import mahjongtournamentsuite.adminapp.generated.resources.usage_ago_yesterday
import mahjongtournamentsuite.adminapp.generated.resources.usage_change_down
import mahjongtournamentsuite.adminapp.generated.resources.usage_change_new
import mahjongtournamentsuite.adminapp.generated.resources.usage_change_none
import mahjongtournamentsuite.adminapp.generated.resources.usage_change_up
import mahjongtournamentsuite.adminapp.generated.resources.weekday_friday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_monday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_saturday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_sunday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_thursday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_tuesday
import mahjongtournamentsuite.adminapp.generated.resources.weekday_wednesday

/** A conflict rate above this value is a warning. */
internal const val CONFLICT_WARNING_RATE = 0.1

/** A fall of the saves by this share or more, against the previous period, is a warning. */
private const val DROP_WARNING = 0.3

private const val MAX_LISTED_FEATURES = 4

internal enum class InsightLevel { INFO, WARNING }

internal data class Insight(val level: InsightLevel, val text: UiText)

internal fun Double.percentText(): String = "${(this * 100).roundToInt()}%"

private val WEEKDAY_NAMES = listOf(
    Res.string.weekday_monday,
    Res.string.weekday_tuesday,
    Res.string.weekday_wednesday,
    Res.string.weekday_thursday,
    Res.string.weekday_friday,
    Res.string.weekday_saturday,
    Res.string.weekday_sunday,
)

/** The facts that an administrator should read first. Each fact is a full sentence. */
internal fun usageInsights(report: UsageReport): List<Insight> {
    val insights = mutableListOf<Insight>()
    val days = report.periodDays
    val current = report.current
    val previous = report.previous

    if (previous.saves > 0 && current.saves <= previous.saves * (1 - DROP_WARNING)) {
        val drop = 1 - current.saves.toDouble() / previous.saves
        insights += Insight(InsightLevel.WARNING, UiText.of(Res.string.insight_saves_fell, drop.percentText(), days))
    }
    if (current.saves == 0) {
        insights += Insight(InsightLevel.WARNING, UiText.of(Res.string.insight_no_saves, days))
    }
    current.conflictRate?.takeIf { it > CONFLICT_WARNING_RATE }?.let {
        insights += Insight(InsightLevel.WARNING, UiText.of(Res.string.insight_conflicts, it.percentText()))
    }

    val accounts = report.accounts
    if (accounts.neverSignedIn > 0) {
        insights += Insight(InsightLevel.WARNING, UiText.of(Res.string.insight_never_signed_in, accounts.neverSignedIn, accounts.total))
    }
    if (accounts.dormant > 0) {
        insights += Insight(InsightLevel.INFO, UiText.of(Res.string.insight_dormant, accounts.dormant, days))
    }

    val withAccount = report.tournaments.sumOf { it.slotsWithAccount }
    val withPassword = report.tournaments.sumOf { it.slotsWithPassword }
    if (withAccount > 0) {
        val rate = withPassword.toDouble() / withAccount
        insights += Insight(
            if (rate < 0.5) InsightLevel.WARNING else InsightLevel.INFO,
            UiText.of(Res.string.insight_accounts_with_password, rate.percentText(), withPassword, withAccount),
        )
    }
    current.playerShare?.let {
        insights += Insight(InsightLevel.INFO, UiText.of(Res.string.insight_player_share, it.percentText()))
    }

    val empty = report.tournaments.count { it.status != TournamentUsageStatus.UPCOMING && it.saves == 0 }
    if (empty > 0) {
        insights += Insight(InsightLevel.WARNING, UiText.of(Res.string.insight_empty_tournaments, empty))
    }

    val unused = report.features.filter { it.isPeriod && it.uses == 0 }
    if (unused.isNotEmpty()) {
        val shown = UiText.Joined(unused.take(MAX_LISTED_FEATURES).map { featureLabelText(it) }, separator = ", ")
        insights += Insight(
            InsightLevel.INFO,
            if (unused.size > MAX_LISTED_FEATURES) {
                UiText.of(Res.string.insight_unused_features_more, days, shown, unused.size - MAX_LISTED_FEATURES)
            } else {
                UiText.of(Res.string.insight_unused_features, days, shown)
            },
        )
    }

    val busiest = report.heatmap.withIndex()
        .flatMap { (day, hours) -> hours.mapIndexed { hour, value -> Triple(day, hour, value) } }
        .maxByOrNull { it.third }
        ?.takeIf { it.third > 0 }
    if (busiest != null) {
        val hour = busiest.second.toString().padStart(2, '0')
        insights += Insight(InsightLevel.INFO, UiText.of(Res.string.insight_busiest_time, UiText.of(WEEKDAY_NAMES[busiest.first]), hour))
    }
    return insights
}

/** The change of [current] against [previous]. */
internal fun changeText(current: Int, previous: Int, days: Int): UiText {
    if (previous == 0) {
        return if (current == 0) UiText.of(Res.string.usage_change_none, days) else UiText.of(Res.string.usage_change_new, days)
    }
    val change = ((current - previous).toDouble() / previous * 100).roundToInt()
    return when {
        change > 0 -> UiText.of(Res.string.usage_change_up, change, days)
        change < 0 -> UiText.of(Res.string.usage_change_down, -change, days)
        else -> UiText.of(Res.string.usage_change_none, days)
    }
}

/** How long ago something happened, as a short phrase. */
internal fun agoText(daysAgo: Int?): UiText = when {
    daysAgo == null -> UiText.of(Res.string.usage_ago_never)
    daysAgo == 0 -> UiText.of(Res.string.usage_ago_today)
    daysAgo == 1 -> UiText.of(Res.string.usage_ago_yesterday)
    daysAgo < 60 -> UiText.of(Res.string.usage_ago_days, daysAgo)
    else -> UiText.of(Res.string.usage_ago_months, daysAgo / 30)
}

/** The name of a feature in the language of the app. The server gives an English name that serves as the fallback. */
internal fun featureLabelText(feature: FeatureUsage): UiText =
    FeatureLabels[feature.key]?.let { UiText.of(it) } ?: UiText.Plain(feature.label)
