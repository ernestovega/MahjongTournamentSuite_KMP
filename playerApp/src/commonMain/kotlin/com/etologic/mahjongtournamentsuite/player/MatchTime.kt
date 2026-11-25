package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.of
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Where a match is in time. The Play of the Timer starts the match, and the match duration of the tournament ends it. */
enum class MatchPhase {
    /** Nobody pressed Play yet. The players wait for the starting gong. */
    NOT_STARTED,
    PLAYING,

    /** The time is over and the players finish the hand in play. They can record one more hand. */
    EXTRA_TIME,

    /** The time is over. The players cannot change the table. Only the organizers can. */
    OVER,

    /** An admin closed the hand entry by hand (the plan B of the Timer), whatever the clock says. */
    CLOSED,
}

/** Minutes before the planned start of a round in which the players can set the seats of their table, as on the server. */
const val SEATING_LEAD_MINUTES = 10

/** The local date and time, by the clock of the server ([ServerTime]), in the zone of the device. */
internal fun currentLocalDateTime(): LocalDateTime = ServerTime.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** The time zone of the venue. Without a valid one, the zone of the device. */
internal fun Tournament.venueZone(): TimeZone =
    timeZone?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() } ?: TimeZone.currentSystemDefault()

/**
 * The current local time at the venue of the tournament. The round times are local times of the venue,
 * so a player in another time zone still sees the right phase. Without a valid time zone, it is the time of the device.
 */
internal fun Tournament.currentVenueDateTime(): LocalDateTime = ServerTime.now().toLocalDateTime(venueZone())

/** A local time of the venue as milliseconds since the epoch. */
internal fun Tournament.epochMsAt(venueTime: LocalDateTime): Long = venueTime.toInstant(venueZone()).toEpochMilliseconds()

/**
 * Planned date and start time of a round. Null when the round has no valid date.
 * A date with no start time starts at midnight. The real start is the Play of the Timer.
 */
fun Tournament.roundStartTime(roundId: Int): LocalDateTime? {
    val schedule = roundSchedules.firstOrNull { it.roundId == roundId }
    val date = schedule?.date?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = schedule.startTime?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime(0, 0)
    return date.atTime(time)
}

fun Tournament.clockOf(roundId: Int): RoundClock? = roundClocks.firstOrNull { it.roundId == roundId }

/** The phase of the clock of a round at [now], a local time of the venue. A round without a clock has not started. */
fun Tournament.clockPhase(roundId: Int, now: LocalDateTime): MatchPhase {
    val clock = clockOf(roundId) ?: return MatchPhase.NOT_STARTED
    val nowMs = epochMsAt(now)
    val elapsed = clock.elapsedMs(nowMs)
    val duration = clock.durationMs(matchDurationMinutes)
    if (elapsed < duration) return MatchPhase.PLAYING
    val extra = if (matchEndMode == MatchEndMode.FINISH_HAND) matchExtraMinutes * 60_000L else 0L
    return if (elapsed < duration + extra) MatchPhase.EXTRA_TIME else MatchPhase.OVER
}

/** Milliseconds left of the match time of a round at [now]. Null before the Play. */
fun Tournament.remainingMs(roundId: Int, now: LocalDateTime): Long? =
    clockOf(roundId)?.remainingMs(matchDurationMinutes, epochMsAt(now))

/** Milliseconds of the extra time after the match time, with FINISH_HAND. */
private fun Tournament.extraMs(): Long = if (matchEndMode == MatchEndMode.FINISH_HAND) matchExtraMinutes * 60_000L else 0L

/** What [expectedStartMs] knows about the start of a round that has no clock. */
private sealed interface ExpectedStart {
    data class At(val epochMs: Long?) : ExpectedStart

    /** The round before is still played or paused, so the start is not known yet. */
    data object Later : ExpectedStart
}

/**
 * The instant at which a round that has no clock can start at the earliest, as on the server: its planned start, or the
 * end of the round before when that one ends later (a late round pushes the next ones).
 */
private fun Tournament.expectedStartMs(roundId: Int, nowMs: Long, depth: Int = 0): ExpectedStart {
    val planned = roundStartTime(roundId)?.let { epochMsAt(it) }
    if (roundId <= 1 || depth > 100) return ExpectedStart.At(planned)
    val before = clockOf(roundId - 1)
    val previousEnd: Long? = if (before != null) {
        val elapsed = before.elapsedMs(nowMs)
        val full = before.durationMs(matchDurationMinutes) + extraMs()
        if (elapsed < full) return ExpectedStart.Later
        nowMs - (elapsed - full)
    } else {
        when (val start = expectedStartMs(roundId - 1, nowMs, depth + 1)) {
            ExpectedStart.Later -> return ExpectedStart.Later
            is ExpectedStart.At -> start.epochMs?.let { it + matchDurationMinutes * 60_000L + extraMs() }
        }
    }
    return ExpectedStart.At(if (planned == null || previousEnd == null) planned ?: previousEnd else maxOf(planned, previousEnd))
}

/**
 * True when the time of a round that never got its starting gong has passed, as on the server: the tournament is
 * completed, a later round already started, or its match time (with the extra time of FINISH_HAND) is over, counted
 * from its planned start or from the end of the round before when that one ended later. Such a round was not played in
 * time, so its players cannot touch its tables any more. Only the organizers can.
 */
fun Tournament.unstartedRoundIsPast(roundId: Int, now: LocalDateTime): Boolean {
    if (isCompleted) return true
    if (roundClocks.any { it.roundId > roundId }) return true
    val nowMs = epochMsAt(now)
    val start = (expectedStartMs(roundId, nowMs) as? ExpectedStart.At)?.epochMs ?: return false
    return nowMs >= start + matchDurationMinutes * 60_000L + extraMs()
}

/** Whether the players of a round can set the seats of their tables now, will be able to soon, or no more. */
enum class SeatingState { OPEN, NOT_YET, OVER }

/**
 * When the players of a round can set the seats of their tables at [now]: from [SEATING_LEAD_MINUTES] before the
 * planned start, or as soon as the round started, until its time is over. A round without a gong closes when its planned
 * time is over ([unstartedRoundIsPast]). The first hand of a table closes its seats. A manual control of an admin wins.
 */
fun Tournament.seatingState(roundId: Int, now: LocalDateTime): SeatingState {
    when (roundControls.of(roundId).seats) {
        ControlMode.OPEN -> return SeatingState.OPEN
        ControlMode.CLOSED -> return SeatingState.OVER
        ControlMode.AUTO -> Unit
    }
    val phase = clockPhase(roundId, now)
    if (phase == MatchPhase.OVER) return SeatingState.OVER
    if (phase != MatchPhase.NOT_STARTED) return SeatingState.OPEN
    if (unstartedRoundIsPast(roundId, now)) return SeatingState.OVER
    val planned = roundStartTime(roundId) ?: return SeatingState.OPEN
    val opens = epochMsAt(planned) - SEATING_LEAD_MINUTES * 60_000L
    return if (epochMsAt(now) >= opens) SeatingState.OPEN else SeatingState.NOT_YET
}

/** True when the players of a round can set the seats of their tables at [now] ([seatingState]). */
fun Tournament.seatingIsOpen(roundId: Int, now: LocalDateTime): Boolean = seatingState(roundId, now) == SeatingState.OPEN

/**
 * Marks as completed the tables with played hands whose time is over. The table keeps its hands.
 * A table with no played hands stays as it is, because nobody played it.
 */
fun Tournament.closeExpiredTables(
    tables: List<Pair<TableState, List<TableHand>>>,
    now: LocalDateTime,
): List<Pair<TableState, List<TableHand>>> = tables.map { (table, hands) ->
    val expired = !table.isCompleted && hands.any { it.isDone } && matchPhase(table.roundId, now, table.tableId) == MatchPhase.OVER
    if (expired) table.copy(isCompleted = true) to hands else table to hands
}

/**
 * The phase of the matches of a round at [now], a local time of the venue, for the hand entry of the players.
 * A manual control of an admin (the plan B of the Timer) wins over the clock: open counts as playing, closed is
 * [MatchPhase.CLOSED]. With [tableId], the control of that table wins over the control of the round.
 */
fun Tournament.matchPhase(roundId: Int, now: LocalDateTime, tableId: Int? = null): MatchPhase {
    val control = roundControls.of(roundId)
    return when (if (tableId == null) control.hands else control.handsAt(tableId)) {
        ControlMode.OPEN -> MatchPhase.PLAYING
        ControlMode.CLOSED -> MatchPhase.CLOSED
        ControlMode.AUTO -> clockPhase(roundId, now)
    }
}
