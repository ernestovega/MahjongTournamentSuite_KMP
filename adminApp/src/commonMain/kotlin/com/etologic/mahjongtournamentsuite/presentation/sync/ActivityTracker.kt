package com.etologic.mahjongtournamentsuite.presentation.sync

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Remembers when the user last touched the screen. Polling uses it to slow down and stop when nobody works. */
class ActivityTracker(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var lastActivity: TimeMark = timeSource.markNow()

    fun markActive() {
        lastActivity = timeSource.markNow()
    }

    fun idleFor(): Duration = lastActivity.elapsedNow()
}
