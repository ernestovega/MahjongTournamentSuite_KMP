package com.etologic.mahjongtournamentsuite.player

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The loop of checks of the player screens: when they ask the server, and with which mode. */
class PollForChangesTest {
    private val foreground = MutableStateFlow(true)
    private val modes = mutableListOf<RefreshMode>()
    private val results = mutableListOf<AppResult<Int>>()
    private var nextWait: Duration? = PollSchedule.ONGOING
    private var answer: AppResult<Int> = AppResult.Success(1)

    private fun TestScope.start(forceFirst: Boolean = false) {
        backgroundScope.launch {
            pollForChanges(
                isStarted = foreground,
                forceFirst = forceFirst,
                load = { mode ->
                    modes += mode
                    answer
                },
                nextDelay = { nextWait },
                onResult = { results += it },
            )
        }
        runCurrent()
    }

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    @Test
    fun theFirstCheckRunsAtOnceAndAsksOnlyForChanges() = runTest {
        start()

        assertEquals(listOf(RefreshMode.IF_CHANGED), modes)
        assertEquals(listOf<AppResult<Int>>(AppResult.Success(1)), results)
    }

    @Test
    fun aManualReloadForcesOnlyTheFirstCheck() = runTest {
        start(forceFirst = true)
        pass(PollSchedule.ONGOING)
        pass(PollSchedule.ONGOING)

        assertEquals(listOf(RefreshMode.FORCE, RefreshMode.IF_CHANGED, RefreshMode.IF_CHANGED), modes)
    }

    @Test
    fun theNextCheckWaitsForTheDelayOfTheSchedule() = runTest {
        start()

        pass(PollSchedule.ONGOING - 1.seconds)
        assertEquals(1, modes.size)
        pass(1.seconds)
        assertEquals(2, modes.size)
    }

    @Test
    fun theDelayIsReadAgainAfterEveryAnswer() = runTest {
        nextWait = PollSchedule.NOT_STARTED
        start()
        // The tournament starts: the next wait is shorter.
        nextWait = PollSchedule.TABLE_IN_PLAY

        pass(PollSchedule.NOT_STARTED)
        assertEquals(2, modes.size)
        pass(PollSchedule.TABLE_IN_PLAY)
        assertEquals(3, modes.size)
    }

    @Test
    fun aFinishedTournamentIsNotCheckedAgain() = runTest {
        nextWait = null
        start()

        pass(10.hours)

        assertEquals(1, modes.size)
    }

    @Test
    fun nothingIsCheckedInTheBackgroundAndTheReturnChecksAtOnce() = runTest {
        start()
        foreground.value = false
        runCurrent()

        pass(5.minutes)
        assertEquals(1, modes.size)

        foreground.value = true
        runCurrent()
        assertEquals(2, modes.size)
        // The regular checks go on after the return.
        pass(PollSchedule.ONGOING)
        assertEquals(3, modes.size)
    }

    @Test
    fun aFinishedTournamentIsCheckedAgainWhenTheAppComesBack() = runTest {
        nextWait = null
        start()

        foreground.value = false
        runCurrent()
        foreground.value = true
        runCurrent()

        assertEquals(2, modes.size)
    }

    @Test
    fun aScreenOpenedInTheBackgroundWaitsForTheForeground() = runTest {
        foreground.value = false
        start()

        pass(1.minutes)
        assertEquals(0, modes.size)

        foreground.value = true
        runCurrent()
        assertEquals(1, modes.size)
    }

    @Test
    fun aFailedCheckIsReportedAndTriedAgainAtTheNextDelay() = runTest {
        answer = AppResult.Failure(AppError.Network)
        start()
        answer = AppResult.Success(2)

        pass(PollSchedule.ONGOING)

        assertEquals(listOf(AppResult.Failure(AppError.Network), AppResult.Success(2)), results)
    }
}
