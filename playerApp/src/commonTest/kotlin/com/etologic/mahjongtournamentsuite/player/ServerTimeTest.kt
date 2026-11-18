package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The app follows the clock of the server, so a phone with a wrong clock still shows the hand buttons on time. */
class ServerTimeTest {
    @Test
    fun theOffsetIsTheServerClockMinusTheDeviceClock() {
        ServerTime.learn(serverNowMs = 10_000, deviceNowMs = 7_000)
        assertEquals(3_000, ServerTime.offsetMs)
        ServerTime.learn(serverNowMs = 5_000, deviceNowMs = 7_000)
        assertEquals(-2_000, ServerTime.offsetMs)
        ServerTime.learn(serverNowMs = 1, deviceNowMs = 1)
    }

    @Test
    fun anAnswerWithoutTheServerTimeChangesNothing() {
        ServerTime.learn(serverNowMs = 10_000, deviceNowMs = 4_000)
        ServerTime.learn(serverNowMs = 0, deviceNowMs = 99_000)
        assertEquals(6_000, ServerTime.offsetMs)
        ServerTime.learn(serverNowMs = 1, deviceNowMs = 1)
    }

    @Test
    fun nowAddsTheOffset() {
        val device = Clock.System.now().toEpochMilliseconds()
        ServerTime.learn(serverNowMs = device + 60_000, deviceNowMs = device)
        val now = ServerTime.nowMs()
        assertTrue(now - device in 60_000..65_000, "now was ${now - device} ms ahead")
        assertTrue(ServerTime.now().toEpochMilliseconds() - device in 60_000..65_000)
        ServerTime.learn(serverNowMs = 1, deviceNowMs = 1)
    }
}
