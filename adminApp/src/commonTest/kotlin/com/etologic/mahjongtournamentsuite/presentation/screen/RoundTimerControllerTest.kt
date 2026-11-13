package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundTimerControllerTest {
    private val minute = 60_000L
    private var deviceNow = 1_000_000L

    private fun setup(numRounds: Int = 3, initialRound: Int = 1): Pair<FakeTournamentRepository, RoundTimerController> {
        val repository = FakeTournamentRepository()
        repository.tournaments = listOf(
            Tournament(id = "t", name = "T", isTeams = false, numPlayers = 8, numRounds = numRounds, matchDurationMinutes = 120),
        )
        // The server is 5 seconds ahead of the device.
        repository.serverNowMs = deviceNow + 5_000
        return repository to RoundTimerController(repository, "t", initialRound) { deviceNow }
    }

    @Test
    fun aRoundThatHasNotStartedShowsTheWholeMatch() = runTest {
        val (_, controller) = setup()
        controller.refresh()

        val state = controller.state.value
        assertFalse(state.isStarted)
        assertEquals(120 * 60L, state.remainingSeconds(deviceNow))
        assertEquals(5_000L, state.serverOffsetMs)
    }

    @Test
    fun playStartsTheRoundOnTheServerAndTheTimeCountsFromThere() = runTest {
        val (repository, controller) = setup()
        controller.refresh()

        controller.play()
        deviceNow += 10 * minute
        repository.serverNowMs += 10 * minute

        assertEquals(listOf(1 to (RoundClockAction.Start() as RoundClockAction)), repository.clockActions)
        assertTrue(controller.state.value.isRunning)
        assertEquals(110 * 60L, controller.state.value.remainingSeconds(deviceNow))
    }

    @Test
    fun pauseAndPlayAgainResumeTheSameRound() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        controller.play()
        repository.serverNowMs += 30 * minute
        deviceNow += 30 * minute

        controller.pause()
        assertFalse(controller.state.value.isRunning)
        repository.serverNowMs += 5 * minute
        deviceNow += 5 * minute
        assertEquals(90 * 60L, controller.state.value.remainingSeconds(deviceNow))
        controller.play()

        assertEquals(listOf("Start", "Pause", "Resume"), repository.clockActions.map { it.second::class.simpleName })
        assertTrue(controller.state.value.isRunning)
    }

    @Test
    fun anotherTimerPressedPlayAndThisOneFollows() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.roundClocks = listOf(RoundClock(1, repository.serverNowMs - 2 * minute))

        controller.refresh()

        assertTrue(controller.state.value.isRunning)
        assertEquals(118 * 60L, controller.state.value.remainingSeconds(deviceNow))
    }

    @Test
    fun theRoundsGoFromOneToTheLastRound() = runTest {
        val (_, controller) = setup(numRounds = 2, initialRound = 5)
        controller.refresh()
        assertEquals(2, controller.state.value.roundId)

        controller.selectRound(0)
        assertEquals(1, controller.state.value.roundId)
    }

    @Test
    fun resettingARoundWithHandsAsksFirst() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        controller.play()
        repository.roundsWithHands = setOf(1)

        controller.reset()
        assertTrue(controller.state.value.confirmReset)
        assertTrue(controller.state.value.isStarted)

        controller.reset(force = true)
        assertFalse(controller.state.value.confirmReset)
        assertNull(controller.state.value.clock)
    }

    @Test
    fun setTimeAndStartedAgoSendTheirMinutes() = runTest {
        val (repository, controller) = setup()
        controller.refresh()

        controller.startedMinutesAgo(7)
        assertEquals(113 * 60L, controller.state.value.remainingSeconds(deviceNow))
        controller.setRemainingMinutes(20)

        assertEquals(20 * 60L, controller.state.value.remainingSeconds(deviceNow))
        assertEquals(RoundClockAction.Start(agoSeconds = 420), repository.clockActions.first().second)
    }

    @Test
    fun withoutNetworkThePlayShowsAtOnceAndIsSentLaterWithItsRealTime() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.clockError = AppError.Network

        controller.play()
        assertTrue(controller.state.value.isRunning)
        assertTrue(controller.state.value.isOffline)
        assertTrue(repository.clockActions.isEmpty())

        // The network comes back 90 seconds later. The server starts the round 90 seconds ago.
        deviceNow += 90_000
        repository.serverNowMs += 90_000
        repository.clockError = null
        controller.refresh()

        assertFalse(controller.state.value.isOffline)
        assertEquals(RoundClockAction.Start(agoSeconds = 90), repository.clockActions.single().second)
        assertEquals(repository.serverNowMs - 90_000, repository.roundClocks.single().startedAtMs)
    }

    @Test
    fun theTimerOpensAtTheRunningRoundOrTheNextRoundToStart() {
        val base = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 8, numRounds = 3, matchDurationMinutes = 120)
        val now = 10 * 60 * minute

        assertEquals(1, base.timerRound(now))
        assertEquals(2, base.copy(roundClocks = listOf(RoundClock(1, now - 200 * minute))).timerRound(now))
        assertEquals(2, base.copy(roundClocks = listOf(RoundClock(1, now - 200 * minute), RoundClock(2, now - 10 * minute))).timerRound(now))
        val allOver = (1..3).map { RoundClock(it, now - 1000 * minute) }
        assertEquals(3, base.copy(roundClocks = allOver).timerRound(now))
    }

    @Test
    fun aTimerWithoutTournamentKeepsItsClockOnTheDeviceAndNeverCallsTheServer() = runTest {
        val repository = FakeTournamentRepository()
        val controller = RoundTimerController(repository, null, 1) { deviceNow }
        controller.refresh()

        val initial = controller.state.value
        assertTrue(initial.canControl)
        assertFalse(initial.isStarted)
        assertEquals(120 * 60L, initial.remainingSeconds(deviceNow))

        controller.play()
        deviceNow += 30 * minute
        controller.pause()
        deviceNow += 5 * minute
        assertEquals(90 * 60L, controller.state.value.remainingSeconds(deviceNow))
        controller.play()
        controller.setRemainingMinutes(10)
        assertEquals(10 * 60L, controller.state.value.remainingSeconds(deviceNow))
        controller.reset()
        assertFalse(controller.state.value.isStarted)
        assertFalse(controller.state.value.confirmReset)

        assertTrue(repository.clockActions.isEmpty())
    }

    @Test
    fun aTimerWithoutTournamentPicksAnyRound() = runTest {
        val controller = RoundTimerController(FakeTournamentRepository(), null, 1) { deviceNow }

        controller.selectRound(12)
        assertEquals(12, controller.state.value.roundId)
        controller.selectRound(MAX_INDEPENDENT_ROUNDS + 1)
        assertEquals(MAX_INDEPENDENT_ROUNDS, controller.state.value.roundId)
        controller.selectRound(0)
        assertEquals(1, controller.state.value.roundId)
    }
}
