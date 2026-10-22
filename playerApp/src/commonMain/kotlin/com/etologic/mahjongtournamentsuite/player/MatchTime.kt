package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
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

/** Where a match is in time. The phase comes from the start of the round and the match duration of the tournament. */
enum class MatchPhase {
    NOT_STARTED,
    PLAYING,

    /** The time is over and the players finish the hand in play. They can record one more hand. */
    EXTRA_TIME,

    /** The time is over. The players cannot change the table. Only the organizers can. */
    OVER,
}

/** The local date and time of the device. */
internal fun currentLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/**
 * The current local time at the venue of the tournament. The round times are local times of the venue,
 * so a player in another time zone still sees the right phase. Without a valid time zone, it is the time of the device.
 */
internal fun Tournament.currentVenueDateTime(): LocalDateTime {
    val zone = timeZone?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    return Clock.System.now().toLocalDateTime(zone)
}

private fun LocalDateTime.epochSeconds(): Long = toInstant(TimeZone.UTC).epochSeconds

/**
 * Date and start time of a round. Null when the round has no valid date.
 * A date with no start time starts at midnight.
 */
fun Tournament.roundStartTime(roundId: Int): LocalDateTime? {
    val schedule = roundSchedules.firstOrNull { it.roundId == roundId }
    val date = schedule?.date?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = schedule.startTime?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime(0, 0)
    return date.atTime(time)
}

/** The phase of the matches of a round at [now]. A round with no date is always playing. */
fun Tournament.matchPhase(roundId: Int, now: LocalDateTime): MatchPhase {
    val start = roundStartTime(roundId) ?: return MatchPhase.PLAYING
    val nowSeconds = now.epochSeconds()
    val startSeconds = start.epochSeconds()
    if (nowSeconds < startSeconds) return MatchPhase.NOT_STARTED
    val endSeconds = startSeconds + matchDurationMinutes * 60L
    if (nowSeconds < endSeconds) return MatchPhase.PLAYING
    val extraSeconds = if (matchEndMode == MatchEndMode.FINISH_HAND) matchExtraMinutes * 60L else 0L
    return if (nowSeconds < endSeconds + extraSeconds) MatchPhase.EXTRA_TIME else MatchPhase.OVER
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
