package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_MATCH_DURATION_MINUTES
import com.etologic.mahjongtournamentsuite.domain.model.ROUND_HAS_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.RoundClock
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import kotlin.time.Clock
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
    /** The clock of the server minus the clock of this device. */
    val serverOffsetMs: Long = 0,
    /** An action that could not reach the server yet. The Timer shows it at once and sends it again. */
    val pending: PendingClockAction? = null,
    /** The server refused a reset because the round has hands. The screen asks for a confirmation. */
    val confirmReset: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val numRounds: Int get() = tournament?.numRounds ?: 1
    val durationMinutes: Int get() = tournament?.matchDurationMinutes ?: DEFAULT_MATCH_DURATION_MINUTES
    val clock: RoundClock? get() = clocks.firstOrNull { it.roundId == roundId }
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

    fun totalSeconds(): Long = (clock?.durationMs(durationMinutes) ?: (durationMinutes * 60_000L)) / 1000
}

data class PendingClockAction(val roundId: Int, val action: RoundClockAction, val pressedAtDeviceMs: Long)

/**
 * The Timer of a tournament. Play, Pause, Reset and "Set time" change the clock of the round on the server, so every
 * Timer of the round and the apps of the players follow the same clock. Without network, the Timer applies the action
 * at once and sends it again, with the time that passed since the press, until the server takes it.
 */
class RoundTimerController(
    private val repository: TournamentRepository,
    private val tournamentId: String,
    initialRound: Int,
    private val deviceNowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(RoundTimerState(roundId = initialRound.coerceAtLeast(1)))
    val state: StateFlow<RoundTimerState> = mutableState.asStateFlow()

    /** Loads the tournament and the clocks. The screen calls it at start and then every few seconds. */
    suspend fun refresh() {
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

    fun dismissResetConfirmation() = mutableState.update { it.copy(confirmReset = false) }

    private suspend fun perform(action: RoundClockAction) {
        val pending = PendingClockAction(mutableState.value.roundId, action, deviceNowMs())
        send(pending)
    }

    private suspend fun send(pending: PendingClockAction) {
        val agoSeconds = ((deviceNowMs() - pending.pressedAtDeviceMs) / 1000).coerceAtLeast(0)
        val action = when (val action = pending.action) {
            is RoundClockAction.Start -> action.copy(agoSeconds = action.agoSeconds + agoSeconds)
            is RoundClockAction.Pause -> action.copy(agoSeconds = agoSeconds)
            is RoundClockAction.Resume -> action.copy(agoSeconds = agoSeconds)
            is RoundClockAction.SetRemaining -> action.copy(remainingSeconds = (action.remainingSeconds - agoSeconds).coerceAtLeast(0))
            is RoundClockAction.Reset -> action
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
                        state.clocks.applyLocally(pending, state.durationMinutes, state.serverNow(pending.pressedAtDeviceMs))
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
        mutableState.update { it.copy(clocks = clocks.clocks, serverOffsetMs = offset, loadFailed = false) }
    }
}

/** The clocks after an action that the server did not take yet. The server does the same on its side later. */
internal fun List<RoundClock>.applyLocally(pending: PendingClockAction, durationMinutes: Int, serverNowMs: Long): List<RoundClock> {
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
    }
    return (others + listOfNotNull(updated)).sortedBy { it.roundId }
}
