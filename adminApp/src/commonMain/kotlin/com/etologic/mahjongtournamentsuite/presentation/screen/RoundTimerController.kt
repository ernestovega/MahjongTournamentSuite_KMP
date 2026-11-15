package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.MatchEndMode
import com.etologic.mahjongtournamentsuite.domain.model.ROUND_HAS_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.RoundControl
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.of
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.telemetry.NoTelemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryClockAction
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryTimerKind
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the Timer shows for the selected round. */
data class RoundTimerState(
    val tournament: Tournament? = null,
    val roundId: Int = 1,
    /** The clocks of the rounds, as the server sent them, or as the Timer applied an action that waits for the network. */
    val clocks: List<RoundClock> = emptyList(),
    /** The manual controls of the rounds (the plan B of the Timer), as the server sent them. */
    val controls: List<RoundControl> = emptyList(),
    /** The clock of the server minus the clock of this device. */
    val serverOffsetMs: Long = 0,
    /** An action that could not reach the server yet. The Timer shows it at once and sends it again. */
    val pending: PendingClockAction? = null,
    /** The server refused a reset because the round has hands. The screen asks for a confirmation. */
    val confirmReset: Boolean = false,
    val loadFailed: Boolean = false,
    /** A Timer opened without a tournament. Its clock lives only on this device and touches no data. */
    val independent: Boolean = false,
) {
    val numRounds: Int get() = if (independent) MAX_INDEPENDENT_ROUNDS else tournament?.numRounds ?: 1

    /** Play, Pause and the other buttons work once the tournament loaded, or at once without a tournament. */
    val canControl: Boolean get() = independent || (tournament != null && !tournament.isCompleted)

    /** A completed tournament takes no change to its clocks. */
    val tournamentCompleted: Boolean get() = !independent && tournament?.isCompleted == true
    val durationMinutes: Int get() = tournament?.matchDurationMinutes ?: DEFAULT_MATCH_DURATION_MINUTES

    /** The extra time to finish the hand after the match time, or zero when the match stops at the end. */
    val extraMs: Long get() = tournament?.takeIf { it.matchEndMode == MatchEndMode.FINISH_HAND }?.let { it.matchExtraMinutes * 60_000L } ?: 0L
    val clock: RoundClock? get() = clocks.firstOrNull { it.roundId == roundId }

    /** The manual control of the selected round. Automatic when an admin did not change it. */
    val control: RoundControl get() = controls.of(roundId)
    val isStarted: Boolean get() = clock != null
    val isRunning: Boolean get() = clock?.isPaused == false
    val isOffline: Boolean get() = pending != null

    /** The time of the server, as well as this device knows it. */
    fun serverNow(deviceNowMs: Long): Long = deviceNowMs + serverOffsetMs

    /** The seconds left. A round that has not started shows the whole match. */
    fun remainingSeconds(deviceNowMs: Long): Long {
        val clock = clock ?: return durationMinutes * 60L
        return (clock.remainingMs(durationMinutes, serverNow(deviceNowMs)) + 999) / 1000
    }

    /**
     * The planned start time (HH:mm) of a round that has not started although its planned start is past, or null.
     * The Timer warns, so nobody forgets the Play: without it the players cannot add hands.
     */
    fun latePlannedStart(deviceNowMs: Long): String? {
        if (independent || isStarted) return null
        val tournament = tournament ?: return null
        val planned = tournament.plannedStartMs(roundId) ?: return null
        if (serverNow(deviceNowMs) < planned) return null
        return tournament.roundSchedules.firstOrNull { it.roundId == roundId }?.startTime?.trim()?.ifEmpty { null } ?: "00:00"
    }

    fun totalSeconds(): Long = (clock?.durationMs(durationMinutes) ?: (durationMinutes * 60_000L)) / 1000
}

/**
 * The planned start of a round as an instant, as the server reads it: null without a time zone or a date.
 * A date without a time starts at midnight.
 */
internal fun Tournament.plannedStartMs(roundId: Int): Long? {
    val zone = timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: return null
    val schedule = roundSchedules.firstOrNull { it.roundId == roundId } ?: return null
    val date = schedule.date?.trim()?.ifEmpty { null }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = schedule.startTime?.trim()?.ifEmpty { null }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime(0, 0)
    return date.atTime(time).toInstant(zone).toEpochMilliseconds()
}

/** The highest round that a Timer without a tournament can show. */
const val MAX_INDEPENDENT_ROUNDS = 99

data class PendingClockAction(val roundId: Int, val action: RoundClockAction, val pressedAtDeviceMs: Long)

/**
 * The Timer of a tournament, or a Timer without a tournament when [tournamentId] is null. The latter keeps its clock on
 * this device only: it never calls the server and lets the administrator pick any round.
 *
 * The Timer of a tournament. Play, Pause, Reset and "Set time" change the clock of the round on the server, so every
 * Timer of the round and the apps of the players follow the same clock. Without network, the Timer applies the action
 * at once and sends it again, with the time that passed since the press, until the server takes it.
 */
class RoundTimerController(
    private val repository: TournamentRepository,
    private val tournamentId: String?,
    initialRound: Int,
    private val telemetry: Telemetry = NoTelemetry,
    private val deviceNowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(
        RoundTimerState(roundId = initialRound.coerceAtLeast(1), independent = tournamentId == null),
    )
    val state: StateFlow<RoundTimerState> = mutableState.asStateFlow()

    /**
     * True until the first clocks of the server arrive, unless the administrator picks a round or presses a button first.
     * The round of the opening comes from the clocks that the tournament screen knew, which can be old: the first
     * clocks of the server move the Timer to the round whose clock runs.
     */
    private var followRunningRound = tournamentId != null

    /** Loads the tournament and the clocks. The screen calls it at start and then every few seconds. */
    suspend fun refresh() {
        val tournamentId = tournamentId ?: return
        if (mutableState.value.tournament == null) {
            when (val result = repository.listTournaments(RefreshMode.IF_CHANGED)) {
                is AppResult.Success -> {
                    val tournament = result.value.firstOrNull { it.id == tournamentId }
                    mutableState.update { state ->
                        state.copy(
                            tournament = tournament,
                            roundId = state.roundId.coerceIn(1, (tournament?.numRounds ?: 1).coerceAtLeast(1)),
                            clocks = if (state.clocks.isEmpty()) tournament?.roundClocks.orEmpty() else state.clocks,
                            loadFailed = tournament == null,
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update { it.copy(loadFailed = true) }
            }
        }
        // An action that waits for the network goes first. The clocks of the server would undo it on screen.
        mutableState.value.pending?.let { pending ->
            send(pending)
            return
        }
        val sentAt = deviceNowMs()
        val result = repository.getRoundClocks(tournamentId)
        if (result is AppResult.Success && mutableState.value.pending == null) apply(result.value, sentAt)
    }

    fun selectRound(roundId: Int) {
        followRunningRound = false
        mutableState.update { it.copy(roundId = roundId.coerceIn(1, it.numRounds.coerceAtLeast(1)), confirmReset = false) }
    }

    /** Play: starts the round, or resumes it after a pause. */
    suspend fun play() {
        val state = mutableState.value
        perform(if (state.clock == null) RoundClockAction.Start() else RoundClockAction.Resume())
    }

    suspend fun pause() = perform(RoundClockAction.Pause())

    /** "The round started N minutes ago", for a round whose gong rang without the Timer. */
    suspend fun startedMinutesAgo(minutes: Int) = perform(RoundClockAction.Start(agoSeconds = minutes.coerceAtLeast(0) * 60L))

    suspend fun setRemainingMinutes(minutes: Long) = perform(RoundClockAction.SetRemaining(remainingSeconds = minutes.coerceAtLeast(0) * 60))

    /** Reset. A round with saved hands asks first; [force] confirms it. */
    suspend fun reset(force: Boolean = false) {
        mutableState.update { it.copy(confirmReset = false) }
        perform(RoundClockAction.Reset(force = force))
    }

    /** Stop: the round ends now, before its time is over. The screen asks for a confirmation first. */
    suspend fun stop() = perform(RoundClockAction.Stop)

    /**
     * The Reset button. The Timer of a tournament always asks first: a reset stops the players until the next Play.
     * A Timer without a tournament resets at once.
     */
    suspend fun askReset() {
        if (tournamentId == null) reset() else mutableState.update { it.copy(confirmReset = true) }
    }

    /**
     * Changes the manual control of the selected round (the plan B of the Timer). Admins only: the server refuses
     * anyone else. The answer brings the clocks and the controls, so the screen shows the change at once.
     */
    suspend fun changeControl(change: RoundControlChange): AppResult<Unit> {
        val tournamentId = tournamentId ?: return AppResult.Failure(AppError.Unexpected("A Timer without a tournament has no control"))
        followRunningRound = false
        val sentAt = deviceNowMs()
        return when (val result = repository.updateRoundControl(tournamentId, mutableState.value.roundId, change)) {
            is AppResult.Success -> {
                apply(result.value, sentAt)
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> result
        }
    }

    fun dismissResetConfirmation() = mutableState.update { it.copy(confirmReset = false) }

    private suspend fun perform(action: RoundClockAction) {
        followRunningRound = false
        track(action)
        if (tournamentId == null) {
            val nowMs = deviceNowMs()
            val pending = PendingClockAction(mutableState.value.roundId, action, nowMs)
            mutableState.update { it.copy(clocks = it.clocks.applyLocally(pending, it.durationMinutes, nowMs, it.extraMs)) }
            return
        }
        val pending = PendingClockAction(mutableState.value.roundId, action, deviceNowMs())
        send(pending)
    }

    /** Counts the press of a button. A failed request is counted by the log writer of the data module. */
    private fun track(action: RoundClockAction) {
        val word = when (action) {
            is RoundClockAction.Start -> if (action.agoSeconds > 0) TelemetryClockAction.STARTED_AGO else TelemetryClockAction.STARTED
            is RoundClockAction.Pause -> TelemetryClockAction.PAUSED
            is RoundClockAction.Resume -> TelemetryClockAction.RESUMED
            is RoundClockAction.SetRemaining -> TelemetryClockAction.TIME_SET
            RoundClockAction.Stop -> TelemetryClockAction.STOPPED
            // The confirmation of a reset is the same press again.
            is RoundClockAction.Reset -> if (action.force) return else TelemetryClockAction.RESET
        }
        val kind = if (tournamentId == null) TelemetryTimerKind.INDEPENDENT else TelemetryTimerKind.TOURNAMENT
        telemetry.event(TelemetryEvent.ROUND_CLOCK, mapOf(TelemetryParam.ACTION to word, TelemetryParam.KIND to kind))
    }

    private suspend fun send(pending: PendingClockAction) {
        val tournamentId = tournamentId ?: return
        val agoSeconds = ((deviceNowMs() - pending.pressedAtDeviceMs) / 1000).coerceAtLeast(0)
        val action = when (val action = pending.action) {
            is RoundClockAction.Start -> action.copy(agoSeconds = action.agoSeconds + agoSeconds)
            is RoundClockAction.Pause -> action.copy(agoSeconds = agoSeconds)
            is RoundClockAction.Resume -> action.copy(agoSeconds = agoSeconds)
            is RoundClockAction.SetRemaining -> action.copy(remainingSeconds = (action.remainingSeconds - agoSeconds).coerceAtLeast(0))
            is RoundClockAction.Reset, RoundClockAction.Stop -> action
        }
        val sentAt = deviceNowMs()
        when (val result = repository.updateRoundClock(tournamentId, pending.roundId, action)) {
            is AppResult.Success -> {
                mutableState.update { it.copy(pending = null) }
                apply(result.value, sentAt)
            }
            is AppResult.Failure -> when (val error = result.error) {
                is AppError.Conflict -> mutableState.update {
                    it.copy(pending = null, confirmReset = error.message == ROUND_HAS_HANDS && action is RoundClockAction.Reset)
                }
                // The first failure shows the action at once. A later failure keeps what the screen shows.
                AppError.Network -> mutableState.update { state ->
                    val clocks = if (state.pending == null) {
                        state.clocks.applyLocally(pending, state.durationMinutes, state.serverNow(pending.pressedAtDeviceMs), state.extraMs)
                    } else {
                        state.clocks
                    }
                    state.copy(pending = pending, clocks = clocks)
                }
                else -> mutableState.update { it.copy(pending = null) }
            }
        }
    }

    /** Takes the clocks of the server. The offset assumes that the answer came back half way through the request. */
    private fun apply(clocks: RoundClocks, sentAtDeviceMs: Long) {
        val receivedAt = deviceNowMs()
        val offset = clocks.serverNowMs - (sentAtDeviceMs + receivedAt) / 2
        val follow = followRunningRound
        followRunningRound = false
        mutableState.update { state ->
            val running = if (follow) clocks.clocks.runningRound(state.durationMinutes, clocks.serverNowMs) else null
            state.copy(
                clocks = clocks.clocks,
                controls = clocks.controls,
                serverOffsetMs = offset,
                loadFailed = false,
                roundId = running?.coerceIn(1, state.numRounds.coerceAtLeast(1)) ?: state.roundId,
            )
        }
    }
}

/** The highest round whose clock runs or is paused before the end of its time, or null. */
internal fun List<RoundClock>.runningRound(durationMinutes: Int, nowMs: Long): Int? = filter { it.remainingMs(durationMinutes, nowMs) > 0 }
    .maxOfOrNull { it.roundId }

/** The clocks after an action that the server did not take yet. The server does the same on its side later. */
internal fun List<RoundClock>.applyLocally(
    pending: PendingClockAction,
    durationMinutes: Int,
    serverNowMs: Long,
    extraMs: Long = 0,
): List<RoundClock> {
    val current = firstOrNull { it.roundId == pending.roundId }
    val others = filter { it.roundId != pending.roundId }
    val updated: RoundClock? = when (val action = pending.action) {
        is RoundClockAction.Start -> current ?: RoundClock(pending.roundId, serverNowMs - action.agoSeconds * 1000)
        is RoundClockAction.Pause -> current?.let { if (it.isPaused) it else it.copy(pausedAtMs = serverNowMs) }
        is RoundClockAction.Resume -> current?.let { clock ->
            clock.pausedAtMs?.let { pausedAt -> clock.copy(pausedAtMs = null, pausedMs = clock.pausedMs + (serverNowMs - pausedAt)) } ?: clock
        }
        is RoundClockAction.Reset -> null
        is RoundClockAction.SetRemaining -> current?.let {
            it.copy(adjustMs = it.elapsedMs(serverNowMs) + action.remainingSeconds * 1000 - durationMinutes * 60_000L)
        }
        RoundClockAction.Stop -> current?.stoppedAt(serverNowMs, durationMinutes, extraMs)
    }
    return (others + listOfNotNull(updated)).sortedBy { it.roundId }
}
