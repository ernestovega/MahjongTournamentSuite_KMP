package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlin.time.Clock
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
}

/** Minutes before the planned start of a round in which the players can set the seats of their table, as on the server. */
const val SEATING_LEAD_MINUTES = 10

/** The local date and time of the device. */
internal fun currentLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** The time zone of the venue. Without a valid one, the zone of the device. */
internal fun Tournament.venueZone(): TimeZone =
    timeZone?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() } ?: TimeZone.currentSystemDefault()

/**
 * The current local time at the venue of the tournament. The round times are local times of the venue,
 * so a player in another time zone still sees the right phase. Without a valid time zone, it is the time of the device.
 */
internal fun Tournament.currentVenueDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(venueZone())

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

/** The phase of the matches of a round at [now], a local time of the venue. A round without a clock has not started. */
fun Tournament.matchPhase(roundId: Int, now: LocalDateTime): MatchPhase {
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

/**
 * True when the players of a round can set the seats of their tables at [now]: from [SEATING_LEAD_MINUTES] before the
 * planned start, or as soon as the round started, until its time is over. The first hand of a table closes its seats.
 */
fun Tournament.seatingIsOpen(roundId: Int, now: LocalDateTime): Boolean {
    val phase = matchPhase(roundId, now)
    if (phase == MatchPhase.OVER) return false
    if (phase != MatchPhase.NOT_STARTED) return true
    val planned = roundStartTime(roundId) ?: return true
    return epochMsAt(now) >= epochMsAt(planned) - SEATING_LEAD_MINUTES * 60_000L
}

/**
 * Marks as completed the tables with played hands whose time is over. The table keeps its hands.
 * A table with no played hands stays as it is, because nobody played it.
 */
fun Tournament.closeExpiredTables(
    tables: List<Pair<TableState, List<TableHand>>>,
    now: LocalDateTime,
): List<Pair<TableState, List<TableHand>>> = tables.map { (table, hands) ->
    val expired = !table.isCompleted && hands.any { it.isDone } && matchPhase(table.roundId, now) == MatchPhase.OVER
    if (expired) table.copy(isCompleted = true) to hands else table to hands
}
