package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundClockTest {
    private val minute = 60_000L

    @Test
    fun aRunningClockCountsFromItsStart() {
        val clock = RoundClock(roundId = 1, startedAtMs = 1_000)

        assertFalse(clock.isPaused)
        assertEquals(10 * minute, clock.elapsedMs(1_000 + 10 * minute))
        assertEquals(110 * minute, clock.remainingMs(durationMinutes = 120, nowMs = 1_000 + 10 * minute))
    }

    @Test
    fun aPausedClockStopsAtThePause() {
        val clock = RoundClock(roundId = 1, startedAtMs = 0, pausedAtMs = 30 * minute)

        assertTrue(clock.isPaused)
        assertEquals(30 * minute, clock.elapsedMs(30 * minute))
        assertEquals(30 * minute, clock.elapsedMs(90 * minute))
        assertEquals(90 * minute, clock.remainingMs(120, 500 * minute))
    }

    @Test
    fun pausesThatAreOverDoNotCount() {
        // 50 minutes since the start, 15 of them in pauses.
        val clock = RoundClock(roundId = 2, startedAtMs = 0, pausedMs = 15 * minute)

        assertEquals(35 * minute, clock.elapsedMs(50 * minute))
        assertEquals(85 * minute, clock.remainingMs(120, 50 * minute))
    }

    @Test
    fun aPauseAndThePausesBeforeItAddUp() {
        val clock = RoundClock(roundId = 1, startedAtMs = 0, pausedAtMs = 60 * minute, pausedMs = 20 * minute)

        assertEquals(40 * minute, clock.elapsedMs(100 * minute))
    }

    @Test
    fun setTimeMovesTheEndOfTheMatchBothWays() {
        val longer = RoundClock(roundId = 1, startedAtMs = 0, adjustMs = 10 * minute)
        val shorter = RoundClock(roundId = 1, startedAtMs = 0, adjustMs = -30 * minute)

        assertEquals(130 * minute, longer.durationMs(120))
        assertEquals(90 * minute, shorter.durationMs(120))
        assertEquals(60 * minute, shorter.remainingMs(120, 30 * minute))
    }

    @Test
    fun theDurationAndTheRemainingTimeNeverGoBelowZero() {
        val clock = RoundClock(roundId = 1, startedAtMs = 0, adjustMs = -200 * minute)

        assertEquals(0, clock.durationMs(120))
        assertEquals(0, clock.remainingMs(120, 10 * minute))
        assertEquals(0, RoundClock(roundId = 1, startedAtMs = 0).remainingMs(120, 121 * minute))
    }

    @Test
    fun aServerClockAheadOfTheDeviceGivesNoNegativeTime() {
        // The device time can be a little before the start that the server wrote.
        val clock = RoundClock(roundId = 1, startedAtMs = 10 * minute)

        assertEquals(0, clock.elapsedMs(9 * minute))
        assertEquals(120 * minute, clock.remainingMs(120, 9 * minute))
    }

    @Test
    fun roundClocksFindTheClockOfARound() {
        val clocks = RoundClocks(listOf(RoundClock(1, 0), RoundClock(3, 5)), serverNowMs = 10)

        assertEquals(5, clocks.of(3)?.startedAtMs)
        assertNull(clocks.of(2))
    }
}
