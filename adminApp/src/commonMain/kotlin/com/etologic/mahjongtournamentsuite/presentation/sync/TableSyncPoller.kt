package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [check] on the pace of a [PollSchedule]. The checks never overlap, because the loop waits for each one.
 * When the user is away for too long, the loop stops the checks and waits for new activity.
 * When the user is back, the loop runs one check at once and goes on with the normal pace.
 */
class TableSyncPoller(
    private val schedule: PollSchedule,
    private val activity: ActivityTracker,
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) {
    suspend fun run(
        hasUnsavedEdits: () -> Boolean,
        check: suspend () -> Unit,
    ): Nothing {
        while (true) {
            when (val step = schedule.nextStep(activity.idleFor(), hasUnsavedEdits())) {
                PollStep.Stop -> {
                    while (schedule.nextStep(activity.idleFor(), hasUnsavedEdits()) is PollStep.Stop) wait(ACTIVITY_CHECK_INTERVAL)
                    runCheck(check)
                }

                is PollStep.Wait -> {
                    wait(step.delay)
                    // The user can become idle during the wait. Then the loop decides again.
                    if (schedule.nextStep(activity.idleFor(), hasUnsavedEdits()) !is PollStep.Stop) runCheck(check)
                }
            }
        }
    }

    /** One failed check must not end polling. */
    private suspend fun runCheck(check: suspend () -> Unit) {
        try {
            check()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The next tick tries again.
        }
    }
}

private val ACTIVITY_CHECK_INTERVAL = 1.seconds
