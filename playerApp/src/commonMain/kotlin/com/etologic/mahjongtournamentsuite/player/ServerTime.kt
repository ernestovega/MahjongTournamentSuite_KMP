package com.etologic.mahjongtournamentsuite.player

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The time of the server, as well as this device knows it. The server decides when the players can add hands and set
 * seats by its own clock, so the app follows it too: a phone with a wrong clock still shows the buttons at the right time.
 */
internal object ServerTime {
    /** The clock of the server minus the clock of this device, from the last answer with the time of the server. */
    var offsetMs: Long = 0
        private set

    /** Takes the time of the server from an answer. A missing time (zero) changes nothing. */
    fun learn(serverNowMs: Long, deviceNowMs: Long = Clock.System.now().toEpochMilliseconds()) {
        if (serverNowMs > 0) offsetMs = serverNowMs - deviceNowMs
    }

    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds() + offsetMs

    fun now(): Instant = Instant.fromEpochMilliseconds(nowMs())
}
