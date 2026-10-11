package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Runs [check] on the pace of a [PollSchedule]. The checks never overlap, because the loop waits for each one.
 * When the user is away for too long, the loop calls [onIdle] and waits until that call returns.
 * [onIdle] gets the check, and it must call it once the user is back. The loop then counts the user as active.
 */
class TableSyncPoller(
    private val schedule: PollSchedule,
    private val activity: ActivityTracker,
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) {
    suspend fun run(
        hasUnsavedEdits: () -> Boolean,
        check: suspend () -> Unit,
        onIdle: suspend (check: suspend () -> Unit) -> Unit,
    ): Nothing {
        while (true) {
            when (val step = schedule.nextStep(activity.idleFor(), hasUnsavedEdits())) {
                PollStep.Stop -> {
                    onIdle { runCheck(check) }
                    activity.markActive()
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
