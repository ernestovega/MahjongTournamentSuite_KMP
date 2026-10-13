package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PollScheduleTest {
    private val schedule = PollSchedule()

    @Test
    fun activeUserWithoutEditsIsCheckedEveryTenSeconds() {
        assertEquals(PollStep.Wait(10.seconds), schedule.nextStep(Duration.ZERO, hasUnsavedEdits = false))
    }

    @Test
    fun userWithUnsavedEditsIsCheckedEveryFiveSeconds() {
        assertEquals(PollStep.Wait(5.seconds), schedule.nextStep(Duration.ZERO, hasUnsavedEdits = true))
    }

    @Test
    fun checkingSlowsDownAfterTwoMinutesWithoutInput() {
        assertEquals(PollStep.Wait(10.seconds), schedule.nextStep(2.minutes - 1.milliseconds, false))
        assertEquals(PollStep.Wait(30.seconds), schedule.nextStep(2.minutes, false))
    }

    @Test
    fun slowIntervalAlsoAppliesToUnsavedEdits() {
        assertEquals(PollStep.Wait(5.seconds), schedule.nextStep(2.minutes - 1.milliseconds, true))
        assertEquals(PollStep.Wait(30.seconds), schedule.nextStep(2.minutes, true))
    }

    @Test
    fun checkingStopsAfterFiveMinutesWithoutInput() {
        assertEquals(PollStep.Wait(30.seconds), schedule.nextStep(5.minutes - 1.milliseconds, false))
        assertEquals(PollStep.Stop, schedule.nextStep(5.minutes, false))
        assertEquals(PollStep.Stop, schedule.nextStep(1000.minutes, true))
    }

    @Test
    fun customLimitsAreUsed() {
        val custom = PollSchedule(
            activeInterval = 1.seconds,
            editingInterval = 500.milliseconds,
            slowInterval = 4.seconds,
            slowAfter = 10.seconds,
            stopAfter = 20.seconds,
        )

        assertEquals(PollStep.Wait(1.seconds), custom.nextStep(Duration.ZERO, false))
        assertEquals(PollStep.Wait(4.seconds), custom.nextStep(10.seconds, false))
        assertEquals(PollStep.Stop, custom.nextStep(20.seconds, false))
    }

    @Test
    fun invalidLimitsAreRejected() {
        assertFailsWith<IllegalArgumentException> { PollSchedule(slowAfter = 6.minutes, stopAfter = 5.minutes) }
        assertFailsWith<IllegalArgumentException> { PollSchedule(editingInterval = Duration.ZERO) }
    }
}
