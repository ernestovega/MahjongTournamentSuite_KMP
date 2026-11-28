package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.ROUND_HAS_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.presentation.telemetry.RecordingTelemetry
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
    fun theQuestionsOfTheTimerCanBeClosed() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        controller.play()
        repository.roundsWithHands = setOf(1)
        controller.reset()
        controller.dismissResetConfirmation()
        assertFalse(controller.state.value.confirmReset)
        controller.dismissClosePrevious()
        controller.dismissRefusal()
        assertNull(controller.state.value.closePrevious)
        assertNull(controller.state.value.refusal)
    }

    @Test
    fun aSecondPressWithoutNetworkKeepsWhatTheScreenShows() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.clockError = AppError.Network
        controller.play()
        val shown = controller.state.value.clocks
        controller.pause()
        assertEquals(shown, controller.state.value.clocks)
        assertTrue(controller.state.value.isOffline)
    }

    @Test
    fun aPressThatTheServerRefusesForAnotherReasonIsDropped() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.clockError = AppError.Unexpected("boom")
        controller.play()
        assertFalse(controller.state.value.isOffline)
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

        assertEquals(1, base.timerRound(nowMs = now))
        assertEquals(2, base.copy(roundClocks = listOf(RoundClock(1, now - 200 * minute))).timerRound(nowMs = now))
        assertEquals(2, base.copy(roundClocks = listOf(RoundClock(1, now - 200 * minute), RoundClock(2, now - 10 * minute))).timerRound(nowMs = now))
        val allOver = (1..3).map { RoundClock(it, now - 1000 * minute) }
        assertEquals(3, base.copy(roundClocks = allOver).timerRound(nowMs = now))
    }

    @Test
    fun theTimerSkipsTheRoundsWithHandsThatHaveNoClock() {
        val base = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 8, numRounds = 4, matchDurationMinutes = 120)
        val now = 10 * 60 * minute
        val firstOver = base.copy(roundClocks = listOf(RoundClock(1, now - 1000 * minute)))

        // Rounds 2 and 3 have hands from the editor but no clock: the next round to play is 4.
        assertEquals(4, firstOver.timerRound(roundsWithHands = setOf(1, 2, 3), nowMs = now))
        assertEquals(3, base.timerRound(roundsWithHands = setOf(2), nowMs = now))
        assertEquals(4, firstOver.timerRound(roundsWithHands = setOf(4), nowMs = now))
    }

    @Test
    fun aRoundWhoseTimeIsOverOpensTheNextRoundEvenWithOpenTables() {
        val base = Tournament(id = "t", name = "T", isTeams = false, numPlayers = 8, numRounds = 4, matchDurationMinutes = 120)
        val now = 10 * 60 * minute
        // Round 2 started late and still runs: the Timer opens at it, not at round 3.
        val late = base.copy(roundClocks = listOf(RoundClock(1, now - 1000 * minute), RoundClock(2, now - 100 * minute)))
        assertEquals(2, late.timerRound(roundsWithHands = setOf(1, 2), nowMs = now))
        // Its time is over (or the Timer stopped it): the Timer waits for the gong of round 3.
        val over = base.copy(roundClocks = listOf(RoundClock(1, now - 1000 * minute), RoundClock(2, now - 125 * minute)))
        assertEquals(3, over.timerRound(roundsWithHands = setOf(1, 2), nowMs = now))
        val stopped = base.copy(roundClocks = listOf(RoundClock(2, now - 30 * minute).stoppedAt(now, 120, extraMs = 15 * minute)))
        assertEquals(3, stopped.timerRound(roundsWithHands = setOf(2), nowMs = now))
    }

    @Test
    fun theFirstClocksOfTheServerMoveTheTimerToTheRunningRound() = runTest {
        // The tournament screen knew old clocks and asked for round 3, but round 2 still runs on the server.
        val (repository, controller) = setup(numRounds = 4, initialRound = 3)
        repository.roundClocks = listOf(RoundClock(1, repository.serverNowMs - 300 * minute), RoundClock(2, repository.serverNowMs - 30 * minute))
        controller.refresh()

        assertEquals(2, controller.state.value.roundId)
        assertTrue(controller.state.value.isRunning)
    }

    @Test
    fun onlyTheFirstClocksMoveTheTimer() = runTest {
        val (repository, controller) = setup(numRounds = 4, initialRound = 3)
        controller.refresh()
        assertEquals(3, controller.state.value.roundId)

        // Another Timer starts round 2 later, and the administrator looks at round 4: the Timer stays there.
        controller.selectRound(4)
        repository.roundClocks = listOf(RoundClock(2, repository.serverNowMs - 30 * minute))
        controller.refresh()

        assertEquals(4, controller.state.value.roundId)
    }

    @Test
    fun stopEndsThePausedRoundOnTheServerAndOfflineAtOnce() = runTest {
        val (repository, controller) = setup()
        repository.tournaments = listOf(repository.tournaments.single().copy(matchEndMode = MatchEndMode.FINISH_HAND, matchExtraMinutes = 15))
        controller.refresh()
        controller.play()
        deviceNow += 20 * minute
        repository.serverNowMs += 20 * minute
        controller.pause()

        // Without network the Timer ends the round at once, with the extra time too.
        repository.clockError = AppError.Network
        controller.stop()
        val state = controller.state.value
        assertTrue(state.isOffline)
        assertTrue(state.isRunning)
        assertEquals(0L, state.remainingSeconds(deviceNow))
        assertTrue(state.clock!!.elapsedMs(state.serverNow(deviceNow)) >= state.clock!!.durationMs(120) + 15 * minute)

        repository.clockError = null
        controller.refresh()
        assertEquals(RoundClockAction.Stop, repository.clockActions.last().second)
        assertFalse(controller.state.value.isOffline)
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

    private fun RecordingTelemetry.clockWords(): List<Pair<Any?, Any?>> = events
        .filter { it.first == TelemetryEvent.ROUND_CLOCK }
        .map { it.second[TelemetryParam.ACTION] to it.second[TelemetryParam.KIND] }

    @Test
    fun everyButtonOfTheTimerCountsOneEventWithTheKindOfTimer() = runTest {
        val telemetry = RecordingTelemetry()
        val controller = RoundTimerController(FakeTournamentRepository(), null, 1, telemetry) { deviceNow }

        controller.play()
        controller.pause()
        controller.play()
        controller.setRemainingMinutes(10)
        controller.reset()
        controller.startedMinutesAgo(5)
        controller.stop()

        val kind = "independent"
        assertEquals(
            listOf("started", "paused", "resumed", "time_set", "reset", "started_ago", "stopped").map { it to kind },
            telemetry.clockWords(),
        )
    }

    @Test
    fun theConfirmationOfAResetIsNotASecondPress() = runTest {
        val (repository, _) = setup()
        val telemetry = RecordingTelemetry()
        val controller = RoundTimerController(repository, "t", 1, telemetry) { deviceNow }
        controller.refresh()
        controller.play()
        repository.clockError = AppError.Conflict(ROUND_HAS_HANDS)
        controller.reset()
        repository.clockError = null
        controller.reset(force = true)

        assertEquals(listOf("started" to "tournament", "reset" to "tournament"), telemetry.clockWords())
    }

    @Test
    fun theControlsOfTheServerArriveWithTheClocks() = runTest {
        val (repository, controller) = setup()
        repository.roundControls = listOf(RoundControl(roundId = 1, hands = ControlMode.CLOSED))
        controller.refresh()

        assertEquals(ControlMode.CLOSED, controller.state.value.control.hands)
        assertTrue(controller.state.value.control.isManual)
        controller.selectRound(2)
        assertFalse(controller.state.value.control.isManual)
    }

    @Test
    fun changeControlChangesTheSelectedRoundOnly() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        controller.selectRound(2)

        val result = controller.changeControl(RoundControlChange(seats = ControlMode.OPEN, table = 3 to ControlMode.CLOSED))

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(2 to RoundControlChange(seats = ControlMode.OPEN, table = 3 to ControlMode.CLOSED)), repository.controlChanges)
        val control = controller.state.value.control
        assertEquals(ControlMode.OPEN, control.seats)
        assertEquals(ControlMode.CLOSED, control.handsAt(3))
        assertEquals(ControlMode.AUTO, control.handsAt(4))
        // Back to automatic removes the control.
        controller.changeControl(RoundControlChange(seats = ControlMode.AUTO, table = 3 to ControlMode.AUTO))
        assertFalse(controller.state.value.control.isManual)
    }

    @Test
    fun aRefusedControlChangesNothing() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.clockError = AppError.Unexpected("Admins only")

        val result = controller.changeControl(RoundControlChange(hands = ControlMode.OPEN))

        assertTrue(result is AppResult.Failure)
        assertFalse(controller.state.value.control.isManual)
    }

    @Test
    fun aTimerWithoutTournamentHasNoControl() = runTest {
        val controller = RoundTimerController(FakeTournamentRepository(), null, 1) { deviceNow }

        assertTrue(controller.changeControl(RoundControlChange(hands = ControlMode.OPEN)) is AppResult.Failure)
    }

    @Test
    fun theTournamentIsReadOnceAndAgainOnlyWhenAsked() = runTest {
        val (repository, controller) = setup()
        controller.refresh()
        repository.tournaments = repository.tournaments.map { it.copy(resultsRevealed = true) }

        controller.refresh()
        assertFalse(controller.state.value.tournament!!.resultsRevealed)
        controller.refresh(reloadTournament = true)
        assertTrue(controller.state.value.tournament!!.resultsRevealed)

        // A failed reload keeps the tournament on screen.
        repository.tournamentsError = AppError.Network
        controller.refresh(reloadTournament = true)
        assertFalse(controller.state.value.loadFailed)
        assertTrue(controller.state.value.tournament!!.resultsRevealed)
    }

    @Test
    fun aFirstReadThatFailsIsTriedAgainOnTheNextRefresh() = runTest {
        val (repository, controller) = setup()
        repository.tournamentsError = AppError.Network
        controller.refresh()
        assertEquals(null, controller.state.value.tournament)

        repository.tournamentsError = null
        controller.refresh()
        assertEquals("t", controller.state.value.tournament?.id)
    }
}
