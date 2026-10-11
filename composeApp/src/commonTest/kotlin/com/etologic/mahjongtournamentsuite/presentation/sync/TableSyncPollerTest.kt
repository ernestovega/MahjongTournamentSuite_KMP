package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A clock that follows the virtual time of the test, so idle time and delays always agree. */
private class VirtualClock(private val now: () -> Long) : TimeSource {
    override fun markNow(): TimeMark = object : TimeMark {
        private val startedAt = now()
        override fun elapsedNow(): Duration = (now() - startedAt).milliseconds
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TableSyncPollerTest {
    private class Harness(
        private val scope: TestScope,
        schedule: PollSchedule = PollSchedule(),
    ) {
        val activity = ActivityTracker(VirtualClock { scope.currentTime })
        var hasUnsavedEdits = false
        val checkTimes = mutableListOf<Long>()
        val idleCalls = mutableListOf<Long>()
        var checkAction: suspend () -> Unit = {}
        var idleAction: suspend (suspend () -> Unit) -> Unit = { check -> check() }
        private val poller = TableSyncPoller(schedule, activity)

        fun start() {
            scope.backgroundScope.launch {
                poller.run(
                    hasUnsavedEdits = { hasUnsavedEdits },
                    check = {
                        checkTimes += scope.currentTime
                        checkAction()
                    },
                    onIdle = { check ->
                        idleCalls += scope.currentTime
                        idleAction(check)
                    },
                )
            }
        }

        /** A user who touches the screen every second. */
        fun keepWorking() {
            scope.backgroundScope.launch { while (true) { delay(1_000); activity.markActive() } }
        }
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun checksEveryTenSecondsForAnActiveUser() = runTest {
        val h = Harness(this).also { it.start(); it.keepWorking() }

        advance(35.seconds)

        assertEquals(listOf(10_000L, 20_000L, 30_000L), h.checkTimes)
    }

    @Test
    fun checksEveryFiveSecondsWhileThereAreUnsavedEdits() = runTest {
        val h = Harness(this).also { it.start(); it.keepWorking() }
        h.hasUnsavedEdits = true

        advance(16.seconds)

        assertEquals(listOf(5_000L, 10_000L, 15_000L), h.checkTimes)
    }

    @Test
    fun paceChangesWhenTheUserStartsOrStopsEditingBetweenChecks() = runTest {
        val h = Harness(this).also { it.start(); it.keepWorking() }
        advance(10.seconds)
        h.hasUnsavedEdits = true
        advance(10.seconds)
        h.hasUnsavedEdits = false
        advance(15.seconds)

        // 10 s pace, then the 10 s wait that began before the edit, then 5 s pace, then 10 s pace again.
        assertEquals(listOf(10_000L, 20_000L, 25_000L), h.checkTimes.take(3))
    }

    @Test
    fun slowsDownAfterTwoMinutesWithoutInput() = runTest {
        val h = Harness(this).also { it.start() }

        advance(181.seconds)

        assertEquals((1..12).map { it * 10_000L } + listOf(150_000L, 180_000L), h.checkTimes)
    }

    @Test
    fun activityBringsTheFastPaceBack() = runTest {
        val h = Harness(this).also { it.start() }
        advance(180.seconds)

        h.activity.markActive()
        advance(31.seconds) // the 30 s wait that was running ends at 210 s
        assertEquals(210_000L, h.checkTimes.last())
        advance(25.seconds)

        assertEquals(listOf(210_000L, 220_000L, 230_000L), h.checkTimes.takeLast(3))
    }

    @Test
    fun asksTheUserAfterFiveIdleMinutesAndStopsChecking() = runTest {
        val h = Harness(this)
        h.idleAction = { CompletableDeferred<Unit>().await() }
        h.start()

        advance(10.minutes)

        assertEquals(listOf(300_000L), h.idleCalls)
        assertEquals(270_000L, h.checkTimes.last())
    }

    @Test
    fun checkRunsWhenTheUserContinuesAfterThePause() = runTest {
        val gate = CompletableDeferred<Unit>()
        val h = Harness(this)
        h.idleAction = { check -> gate.await(); check() }
        h.start()
        advance(6.minutes)
        val checksBefore = h.checkTimes.size

        gate.complete(Unit)
        runCurrent()

        assertEquals(checksBefore + 1, h.checkTimes.size)
        assertEquals(360_000L, h.checkTimes.last())
    }

    @Test
    fun afterThePauseTheUserCountsAsActiveAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        val h = Harness(this)
        h.idleAction = { check -> gate.await(); check() }
        h.start()
        advance(6.minutes)

        gate.complete(Unit)
        advance(11.seconds)

        assertEquals(listOf(360_000L, 370_000L), h.checkTimes.takeLast(2))
        assertEquals(1, h.idleCalls.size)
    }

    @Test
    fun asksAgainAfterAnotherIdlePeriod() = runTest {
        val h = Harness(this)
        h.start()

        advance(11.minutes)

        assertEquals(listOf(300_000L, 600_000L), h.idleCalls)
    }

    @Test
    fun aFailingCheckDoesNotEndPolling() = runTest {
        val h = Harness(this).also { it.start(); it.keepWorking() }
        h.checkAction = { error("network down") }

        advance(35.seconds)

        assertEquals(3, h.checkTimes.size)
    }

    @Test
    fun checksNeverOverlapAndWaitForTheSlowCheck() = runTest {
        var running = 0
        var maxRunning = 0
        val h = Harness(this)
        h.checkAction = {
            running++
            maxRunning = maxOf(maxRunning, running)
            delay(25_000)
            running--
        }
        h.start()
        h.keepWorking()

        advance(90.seconds)

        assertEquals(1, maxRunning)
        assertEquals(listOf(10_000L, 45_000L, 80_000L), h.checkTimes)
    }

    @Test
    fun userBecomingIdleDuringAWaitSkipsTheCheck() = runTest {
        val h = Harness(
            this,
            PollSchedule(slowInterval = 3.minutes, slowAfter = 1.minutes, stopAfter = 2.minutes),
        )
        h.idleAction = { CompletableDeferred<Unit>().await() }
        h.start()

        advance(10.minutes)

        // The long wait starts at 60 s and ends at 240 s. The user is idle for too long by then.
        assertEquals(60_000L, h.checkTimes.last())
        assertEquals(listOf(240_000L), h.idleCalls)
    }
}
