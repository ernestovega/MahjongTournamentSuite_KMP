package com.etologic.mahjongtournamentsuite.domain.model

/**
 * The clock of one round. The Play of the Timer starts it, so the match time counts from the real start and not from
 * the planned time. A round without a clock has not started. The instants are milliseconds since the epoch, taken
 * from the clock of the server.
 */
data class RoundClock(
    val roundId: Int,
    val startedAtMs: Long,
    /** The instant of the pause while the clock is paused, otherwise null. */
    val pausedAtMs: Long? = null,
    /** Milliseconds of the pauses that are over. */
    val pausedMs: Long = 0,
    /** Milliseconds added to the match duration (or taken from it, when negative) with "Set time". */
    val adjustMs: Long = 0,
) {
    val isPaused: Boolean get() = pausedAtMs != null

    /** Milliseconds of play at [nowMs]. Pauses do not count. */
    fun elapsedMs(nowMs: Long): Long = ((pausedAtMs ?: nowMs) - startedAtMs - pausedMs).coerceAtLeast(0)

    /** Milliseconds that the match lasts with the adjustment of "Set time". */
    fun durationMs(durationMinutes: Int): Long = (durationMinutes * 60_000L + adjustMs).coerceAtLeast(0)

    /** Milliseconds left of the match time at [nowMs]. Zero after the end. */
    fun remainingMs(durationMinutes: Int, nowMs: Long): Long = (durationMs(durationMinutes) - elapsedMs(nowMs)).coerceAtLeast(0)
}

/** The clocks of a tournament with the time of the server when it answered. */
data class RoundClocks(
    val clocks: List<RoundClock>,
    val serverNowMs: Long,
) {
    fun of(roundId: Int): RoundClock? = clocks.firstOrNull { it.roundId == roundId }
}

/** What a Timer asks the server to do with the clock of a round. */
sealed interface RoundClockAction {
    /** Play. [agoSeconds] is how long ago the round really started: the press, or "started N minutes ago". */
    data class Start(val agoSeconds: Long = 0) : RoundClockAction

    /** [agoSeconds] is how long ago the button was pressed, for a press that could not reach the server at once. */
    data class Pause(val agoSeconds: Long = 0) : RoundClockAction

    data class Resume(val agoSeconds: Long = 0) : RoundClockAction

    /** Removes the clock. A round with saved hands needs [force]: the hands stay. */
    data class Reset(val force: Boolean = false) : RoundClockAction

    /** "Set time": the remaining time from now on. */
    data class SetRemaining(val remainingSeconds: Long) : RoundClockAction
}

/** The message of the [AppError.Conflict] of a reset of a round with saved hands. The Timer asks for a confirmation. */
const val ROUND_HAS_HANDS = "ROUND_HAS_HANDS"
