package com.etologic.mahjongtournamentsuite.player

import kotlin.test.Test
import kotlin.test.assertFalse

class PlayerProcessStateForTestsTest {
    @Test
    fun aResetOpensTheInPlayTabAgainAtTheNextStart() {
        StartTab.liveOpened = true
        PlayerProcessStateForTests.reset()
        assertFalse(StartTab.liveOpened)
    }
}
