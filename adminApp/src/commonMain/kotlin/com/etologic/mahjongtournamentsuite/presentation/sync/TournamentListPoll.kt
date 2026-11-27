package com.etologic.mahjongtournamentsuite.presentation.sync

import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How often the list of tournaments and the ranking screen check the server for changes while they are on screen.
 * They never stop, so a screen left open for a long time stays current. A tournament being played makes them check more often.
 */
object TournamentListPoll {
    val IDLE: Duration = 5.minutes
    val ONGOING: Duration = 1.minutes

    /** [today] is the date on this device. */
    fun nextDelay(tournaments: List<Tournament>, today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): Duration = if (tournaments.any { it.isOngoingOn(today) }) ONGOING else IDLE

    /** The ranking of an ongoing tournament checks every 15 seconds, the same pace as the screens of the player app. */
    val RANKING_ONGOING: Duration = 15.seconds

    /** Before the first answer the ranking checks at the ongoing pace, so it shows soon. */
    fun rankingDelay(tournament: Tournament?, today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): Duration = if (tournament == null || tournament.isOngoingOn(today)) RANKING_ONGOING else IDLE

    /** A tournament is ongoing from its start date to its end date, unless it is completed. Without a valid start date it is not. */
    private fun Tournament.isOngoingOn(today: LocalDate): Boolean {
        if (isCompleted) return false
        val start = eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val end = eventEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
        return today in start..end
    }
}

/**
 * Waits [wait], or less if the screen gets hidden first ([isStarted] turns false).
 * It counts the time with delay and not with withTimeout: in the Compose UI tests the test clock moves delay, while
 * withTimeout keeps the real time, so a test could not see the checks.
 */
suspend fun waitOrUntilHidden(wait: Duration, isStarted: Flow<Boolean>) {
    merge(flow { delay(wait); emit(Unit) }, isStarted.filter { !it }.map { }).first()
}
