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

    /**
     * The clock after a Stop at [nowMs], as the server writes it: running, with the match time and the [extraMs] both
     * over at [nowMs]. A paused clock runs again. A round stopped within its first minutes starts earlier.
     */
    fun stoppedAt(nowMs: Long, durationMinutes: Int, extraMs: Long): RoundClock {
        if (pausedAtMs == null && elapsedMs(nowMs) >= durationMs(durationMinutes) + extraMs) return this
        val pauses = pausedAtMs?.let { pausedMs + (nowMs - it).coerceAtLeast(0) } ?: pausedMs
        val played = (nowMs - startedAtMs - pauses).coerceAtLeast(0)
        val elapsed = maxOf(played, extraMs)
        return RoundClock(
            roundId = roundId,
            startedAtMs = startedAtMs - (extraMs - played).coerceAtLeast(0),
            pausedAtMs = null,
            pausedMs = pauses,
            adjustMs = elapsed - extraMs - durationMinutes * 60_000L,
        )
    }

    /** Milliseconds left of the match time at [nowMs]. Zero after the end. */
    fun remainingMs(durationMinutes: Int, nowMs: Long): Long = (durationMs(durationMinutes) - elapsedMs(nowMs)).coerceAtLeast(0)
}

/** The clocks of a tournament with the time of the server when it answered. */
data class RoundClocks(
    val clocks: List<RoundClock>,
    val serverNowMs: Long,
    /** The manual controls of the rounds (the plan B of the Timer). */
    val controls: List<RoundControl> = emptyList(),
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

    /**
     * Stop: the round ends now, before its time is over, with no extra time. The server closes the tables with hands.
     * The clock stays, over, so the round counts as played.
     */
    data object Stop : RoundClockAction
}

/** The message of the [AppError.Conflict] of a reset of a round with saved hands. The Timer asks for a confirmation. */
const val ROUND_HAS_HANDS = "ROUND_HAS_HANDS"

/** The answer of the server when Round control asks to open a table that has every hand done. */
const val TABLE_HAS_ALL_HANDS = "TABLE_HAS_ALL_HANDS"

/** A manual control of the plan B of the Timer. Automatic follows the clock of the round, as always. */
enum class ControlMode { AUTO, OPEN, CLOSED }

/**
 * The manual control of a round. An admin opens or closes the hand entry and the seats of the players, whatever the
 * clock says, for the whole round or for one table. It does not change the settings of the tournament.
 */
data class RoundControl(
    val roundId: Int,
    /** Hand entry at every table of the round. */
    val hands: ControlMode = ControlMode.AUTO,
    /** The seats of the tables, before their first hand. */
    val seats: ControlMode = ControlMode.AUTO,
    /** Hand entry of one table, by table id. It wins over [hands]. Only OPEN or CLOSED. */
    val tables: Map<Int, ControlMode> = emptyMap(),
) {
    /** The hand entry of a table: its own control, else the control of the round. */
    fun handsAt(tableId: Int): ControlMode = tables[tableId]?.takeIf { it != ControlMode.AUTO } ?: hands

    /** True when something is manual, so the screens warn. */
    val isManual: Boolean get() = hands != ControlMode.AUTO || seats != ControlMode.AUTO || tables.values.any { it != ControlMode.AUTO }
}

/** The control of a round in a list, or an automatic one. */
fun List<RoundControl>.of(roundId: Int): RoundControl = firstOrNull { it.roundId == roundId } ?: RoundControl(roundId)

/** What an admin changes in the control of a round. Null keeps the value. */
data class RoundControlChange(
    val hands: ControlMode? = null,
    val seats: ControlMode? = null,
    /** The hand entry of one table: its id and its mode. AUTO removes the control of the table. */
    val table: Pair<Int, ControlMode>? = null,
    /** Tables that the end of the time closed, to open again so their players go on. */
    val reopenTables: List<Int> = emptyList(),
    /** Tables to mark as completed, like the Completed switch of the table editor. */
    val completeTables: List<Int> = emptyList(),
)
