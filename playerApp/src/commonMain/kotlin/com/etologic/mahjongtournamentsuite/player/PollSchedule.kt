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

    /** The own table of a player while its round is played: the hand of the scorer shows on the other phones soon. */
    val OWN_TABLE_IN_PLAY: Duration = 2.seconds

    /** Before the first answer, a failed load is tried again at this interval. */
    val UNKNOWN: Duration = ONGOING

    /** The list of tournaments checks at a slow pace, also when nothing is being played, so a long open screen stays current. */
    val LIST_IDLE: Duration = 5.minutes

    /** The list checks more often while a tournament of it is being played, because its places and status change. */
    val LIST_ONGOING: Duration = 1.minutes

    /**
     * The time until the next check of the list of tournaments. The list never stops checking while it is on screen.
     * Only a tournament with dates counts as ongoing here, so a tournament without dates does not speed up the list.
     */
    fun listDelay(tournaments: List<Tournament>?, now: LocalDateTime): Duration = when {
        tournaments == null -> UNKNOWN
        tournaments.any { it.eventStartDate != null && it.pollPhase(now) == PollPhase.ONGOING } -> LIST_ONGOING
        else -> LIST_IDLE
    }

    /** A finished tournament still checks, slowly, so a late correction of the organizer shows. */
    val FINISHED: Duration = 5.minutes

    /**
     * The time until the next check of a screen of [tournament]. Every screen of an ongoing tournament checks every
     * [ONGOING]. A player of the tournament ([playsHere]) checks every [TABLE_IN_PLAY] on all its screens while a round
     * is being played, including the extra time to finish the hand and the wait for the starting gong.
     * [tableRoundId] is the round of the table on screen, null on the other screens: that table checks every
     * [TABLE_IN_PLAY] during its round for everyone, so the hand buttons appear soon after the Play. The player's own
     * table ([ownTable]) checks every [OWN_TABLE_IN_PLAY] during its round, so a hand reaches the other phones soon.
     * A completed table ([tableCompleted]: all its hands played, or closed by the organizer) has nothing more to play:
     * it checks every [ONGOING] also while its round goes on, so a late correction of the editor still shows.
     * A return from the background always checks at once, whatever this says.
     */
    fun nextDelay(
        tournament: Tournament?,
        tableRoundId: Int?,
        now: LocalDateTime,
        playsHere: Boolean = false,
        ownTable: Boolean = false,
        tableCompleted: Boolean = false,
    ): Duration {
        if (tournament == null) return UNKNOWN
        return when (tournament.pollPhase(now)) {
            PollPhase.FINISHED -> FINISHED
            PollPhase.NOT_STARTED -> NOT_STARTED
            PollPhase.ONGOING -> {
                val inPlay = when {
                    tableRoundId != null -> tournament.roundIsInPlay(tableRoundId, now)
                    playsHere -> (1..tournament.numRounds).any { tournament.roundIsInPlay(it, now) }
                    else -> false
                }
                when {
                    !inPlay -> ONGOING
                    tableCompleted && tableRoundId != null -> ONGOING
                    ownTable && tableRoundId != null -> OWN_TABLE_IN_PLAY
                    else -> TABLE_IN_PLAY
                }
            }
        }
    }

    /** True while the matches of the round are played, in their extra time, and while its tables wait for the starting gong. */
    private fun Tournament.roundIsInPlay(roundId: Int, now: LocalDateTime): Boolean {
        val phase = matchPhase(roundId, now)
        if (phase == MatchPhase.PLAYING || phase == MatchPhase.EXTRA_TIME) return true
        return phase == MatchPhase.NOT_STARTED && seatingIsOpen(roundId, now)
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
    onResult: suspend (AppResult<T>) -> Unit,
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
