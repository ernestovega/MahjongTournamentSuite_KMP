package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.TournamentUsageStatus
import com.etologic.mahjongtournamentsuite.domain.model.UsageReport
import kotlin.math.roundToInt

/** A conflict rate above this value is a warning. */
internal const val CONFLICT_WARNING_RATE = 0.1

/** A fall of the saves by this share or more, against the previous period, is a warning. */
private const val DROP_WARNING = 0.3

private const val MAX_LISTED_FEATURES = 4

internal enum class InsightLevel { INFO, WARNING }

internal data class Insight(val level: InsightLevel, val text: String)

internal fun Double.percentText(): String = "${(this * 100).roundToInt()}%"

private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The facts that an administrator should read first. Each fact is a full sentence. */
internal fun usageInsights(report: UsageReport): List<Insight> {
    val insights = mutableListOf<Insight>()
    val days = report.periodDays
    val current = report.current
    val previous = report.previous

    if (previous.saves > 0 && current.saves <= previous.saves * (1 - DROP_WARNING)) {
        val drop = 1 - current.saves.toDouble() / previous.saves
        insights += Insight(InsightLevel.WARNING, "Table saves fell by ${drop.percentText()} against the previous $days days.")
    }
    if (current.saves == 0) {
        insights += Insight(InsightLevel.WARNING, "Nobody saved a table in the last $days days.")
    }
    current.conflictRate?.takeIf { it > CONFLICT_WARNING_RATE }?.let {
        insights += Insight(
            InsightLevel.WARNING,
            "${it.percentText()} of the save attempts were refused. Editors and players may work on the same table.",
        )
    }

    val accounts = report.accounts
    if (accounts.neverSignedIn > 0) {
        insights += Insight(InsightLevel.WARNING, "${accounts.neverSignedIn} of ${accounts.total} accounts never signed in.")
    }
    if (accounts.dormant > 0) {
        insights += Insight(InsightLevel.INFO, "${accounts.dormant} accounts signed in before, but not in the last $days days.")
    }

    val sent = report.tournaments.sumOf { it.invitationsSent }
    val accepted = report.tournaments.sumOf { it.invitationsAccepted }
    if (sent > 0) {
        val rate = accepted.toDouble() / sent
        insights += Insight(
            if (rate < 0.5) InsightLevel.WARNING else InsightLevel.INFO,
            "${rate.percentText()} of the invited players created their account ($accepted of $sent).",
        )
    }
    current.playerShare?.let {
        insights += Insight(InsightLevel.INFO, "Players entered ${it.percentText()} of the table saves. Editors entered the rest.")
    }

    val empty = report.tournaments.count { it.status != TournamentUsageStatus.UPCOMING && it.saves == 0 }
    if (empty > 0) {
        insights += Insight(InsightLevel.WARNING, "$empty tournaments that already started have no match data.")
    }

    val unused = report.features.filter { it.isPeriod && it.uses == 0 }.map { it.label }
    if (unused.isNotEmpty()) {
        val shown = unused.take(MAX_LISTED_FEATURES).joinToString(", ")
        val more = if (unused.size > MAX_LISTED_FEATURES) " and ${unused.size - MAX_LISTED_FEATURES} more" else ""
        insights += Insight(InsightLevel.INFO, "Not used in the last $days days: $shown$more.")
    }

    val busiest = report.heatmap.withIndex()
        .flatMap { (day, hours) -> hours.mapIndexed { hour, value -> Triple(day, hour, value) } }
        .maxByOrNull { it.third }
        ?.takeIf { it.third > 0 }
    if (busiest != null) {
        val hour = busiest.second.toString().padStart(2, '0')
        insights += Insight(InsightLevel.INFO, "The busiest time is ${WEEKDAY_NAMES[busiest.first]} at $hour:00, in the time zone of the tournament.")
    }
    return insights
}

/** The change of [current] against [previous]. It is null when there is no base to compare. */
internal fun changeText(current: Int, previous: Int, days: Int): String {
    if (previous == 0) return if (current == 0) "No change against the previous $days days." else "New against the previous $days days."
    val change = ((current - previous).toDouble() / previous * 100).roundToInt()
    return when {
        change > 0 -> "Up $change% against the previous $days days."
        change < 0 -> "Down ${-change}% against the previous $days days."
        else -> "No change against the previous $days days."
    }
}

/** How long ago something happened, as a short phrase. */
internal fun agoText(daysAgo: Int?): String = when {
    daysAgo == null -> "never"
    daysAgo == 0 -> "today"
    daysAgo == 1 -> "yesterday"
    daysAgo < 60 -> "$daysAgo days ago"
    else -> "${daysAgo / 30} months ago"
}
