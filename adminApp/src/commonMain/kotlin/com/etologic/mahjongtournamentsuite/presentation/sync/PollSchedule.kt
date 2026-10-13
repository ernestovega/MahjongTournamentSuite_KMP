package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface PollStep {
    /** Check the server after [delay]. */
    data class Wait(val delay: Duration) : PollStep

    /** Stop checking. The user is away. */
    data object Stop : PollStep
}

/**
 * Decides how often the editor asks the server for changes.
 * A user with unsaved edits has the most to lose, so that user gets the shortest interval.
 * Polling slows down when the user is idle and stops after a longer idle time.
 */
class PollSchedule(
    private val activeInterval: Duration = 10.seconds,
    private val editingInterval: Duration = 5.seconds,
    private val slowInterval: Duration = 30.seconds,
    private val slowAfter: Duration = 2.minutes,
    private val stopAfter: Duration = 5.minutes,
) {
    init {
        require(editingInterval > Duration.ZERO) { "The editing interval must be positive." }
        require(slowAfter <= stopAfter) { "Polling must slow down before it stops." }
    }

    fun nextStep(idle: Duration, hasUnsavedEdits: Boolean): PollStep = when {
        idle >= stopAfter -> PollStep.Stop
        idle >= slowAfter -> PollStep.Wait(slowInterval)
        hasUnsavedEdits -> PollStep.Wait(editingInterval)
        else -> PollStep.Wait(activeInterval)
    }
}
