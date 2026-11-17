package com.etologic.mahjongtournamentsuite.presentation.sync

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How often the list of tournaments checks the server for changes while it is on screen.
 * It never stops, so a screen left open for a long time stays current. A tournament being played makes it check more often.
 */
object TournamentListPoll {
    val IDLE: Duration = 5.minutes
    val ONGOING: Duration = 1.minutes

    /** [today] is the date on this device. */
    fun nextDelay(tournaments: List<Tournament>, today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): Duration = if (tournaments.any { it.isOngoingOn(today) }) ONGOING else IDLE

    /** A tournament is ongoing from its start date to its end date, unless it is completed. Without a valid start date it is not. */
    private fun Tournament.isOngoingOn(today: LocalDate): Boolean {
        if (isCompleted) return false
        val start = eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val end = eventEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
        return today in start..end
    }
}
