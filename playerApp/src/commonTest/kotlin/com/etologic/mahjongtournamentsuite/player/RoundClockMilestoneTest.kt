package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoundClockMilestoneTest {
    private fun at(minutes: Int, seconds: Int = 0) = (minutes * 60L + seconds) * 1_000L

    @Test
    fun theQuarterHourChangesOnlyWhenItStarts() {
        assertEquals(90, roundClockMilestone(at(90), paused = false))
        assertEquals(90, roundClockMilestone(at(75, 1), paused = false))
        assertEquals(75, roundClockMilestone(at(75), paused = false))
        assertEquals(30, roundClockMilestone(at(16), paused = false))
        assertEquals(15, roundClockMilestone(at(15), paused = false))
        assertEquals(15, roundClockMilestone(at(5, 1), paused = false))
    }

    @Test
    fun theLastFiveMinutesAndTheEnd() {
        assertEquals(5, roundClockMilestone(at(5), paused = false))
        assertEquals(5, roundClockMilestone(at(0, 1), paused = false))
        assertEquals(0, roundClockMilestone(0, paused = false))
        assertEquals(0, roundClockMilestone(-3_000, paused = false))
    }

    @Test
    fun aPausedClockAnnouncesNothing() {
        assertNull(roundClockMilestone(at(40), paused = true))
    }
}
