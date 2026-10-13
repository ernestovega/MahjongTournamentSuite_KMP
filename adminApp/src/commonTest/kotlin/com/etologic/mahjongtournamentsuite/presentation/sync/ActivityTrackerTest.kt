package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ActivityTrackerTest {
    @Test
    fun idleTimeGrowsWithTheClock() {
        val clock = TestTimeSource()
        val tracker = ActivityTracker(clock)

        clock += 42.seconds

        assertEquals(42.seconds, tracker.idleFor())
    }

    @Test
    fun activityResetsIdleTime() {
        val clock = TestTimeSource()
        val tracker = ActivityTracker(clock)
        clock += 90.seconds

        tracker.markActive()

        assertEquals(Duration.ZERO, tracker.idleFor())
        clock += 3.seconds
        assertEquals(3.seconds, tracker.idleFor())
    }

    @Test
    fun startsAsActive() {
        assertEquals(Duration.ZERO, ActivityTracker(TestTimeSource()).idleFor())
    }
}
