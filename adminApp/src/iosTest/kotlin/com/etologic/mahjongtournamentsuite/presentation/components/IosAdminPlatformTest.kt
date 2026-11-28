package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.presentation.screen.RankingDisplayCrossWindow
import platform.Foundation.localeIdentifier
import com.etologic.mahjongtournamentsuite.presentation.screen.rankingControlsInSeparateScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The admin part on iOS: calendars that start on Monday, and the Timer and Ranking in the same window. */
class IosAdminPlatformTest {
    @Test
    fun theCalendarsStartOnMonday() {
        assertEquals("en_GB", mondayFirstCalendarLocale().localeIdentifier)
    }

    @Test
    fun theRankingControlsStayInTheSameScreen() {
        assertFalse(rankingControlsInSeparateScreen)
        // Every window of the app shares one process, so nothing crosses between windows.
        var heard = false
        RankingDisplayCrossWindow.listen { heard = true }
        RankingDisplayCrossWindow.post("reveal")
        assertFalse(heard)
    }
}
