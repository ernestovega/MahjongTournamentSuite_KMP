package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Where a tournament is in time, as the checks for changes see it. */
internal enum class PollPhase { NOT_STARTED, ONGOING, FINISHED }

/**
 * The phase of the tournament at [now], the local time of the venue.
 * A completed tournament is finished. Without valid dates, the tournament counts as ongoing, so no change is missed.
 */
internal fun Tournament.pollPhase(now: LocalDateTime): PollPhase {
    if (isCompleted) return PollPhase.FINISHED
    val start = eventStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return PollPhase.ONGOING
    val end = eventEndDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
    return when {
        now.date < start -> PollPhase.NOT_STARTED
        now.date > end -> PollPhase.FINISHED
        else -> PollPhase.ONGOING
    }
}

/** How often the player app asks the server for changes. Fewer checks when nothing can change, more during play. */
internal object PollSchedule {
    val NOT_STARTED: Duration = 1.minutes
    val ONGOING: Duration = 15.seconds
    val TABLE_IN_PLAY: Duration = 5.seconds

    /** Before the first answer, a failed load is tried again at this interval. */
    val UNKNOWN: Duration = ONGOING

    /**
     * The time until the next check, or null when the screen does not check again (a finished tournament).
     * [tableRoundId] is the round of the table on screen, null on the other screens. While its matches are played,
     * including the extra time to finish the hand, the table checks every [TABLE_IN_PLAY].
     * A return from the background always checks at once, whatever this says.
     */
    fun nextDelay(tournament: Tournament?, tableRoundId: Int?, now: LocalDateTime): Duration? {
        if (tournament == null) return UNKNOWN
        return when (tournament.pollPhase(now)) {
            PollPhase.FINISHED -> null
            PollPhase.NOT_STARTED -> NOT_STARTED
            PollPhase.ONGOING -> {
                val inPlay = tableRoundId != null &&
                    tournament.matchPhase(tableRoundId, now).let { it == MatchPhase.PLAYING || it == MatchPhase.EXTRA_TIME }
                if (inPlay) TABLE_IN_PLAY else ONGOING
            }
        }
    }
}

/**
 * The checks of a screen, until the screen closes. [isStarted] is true while the app is in the foreground.
 * - Nothing is checked in the background. The return to the foreground checks at once.
 * - Only the first check uses [RefreshMode.FORCE], and only when [forceFirst] is true (a manual reload).
 * - [nextDelay] is read after each answer. Null waits until the app goes to the background and comes back.
 */
internal suspend fun <T> pollForChanges(
    isStarted: Flow<Boolean>,
    forceFirst: Boolean,
    load: suspend (RefreshMode) -> AppResult<T>,
    nextDelay: () -> Duration?,
    onResult: (AppResult<T>) -> Unit,
): Nothing {
    var forceNext = forceFirst
    while (true) {
        isStarted.first { it }
        val mode = if (forceNext) RefreshMode.FORCE else RefreshMode.IF_CHANGED
        forceNext = false
        onResult(load(mode))
        // Going to the background ends the wait, so the return checks at once.
        withTimeoutOrNull(nextDelay() ?: Duration.INFINITE) {
            isStarted.first { !it }
        }
    }
}
