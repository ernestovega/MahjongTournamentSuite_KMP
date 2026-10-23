package com.etologic.mahjongtournamentsuite.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedTimeTest {
    @Test
    fun timesUseThe24HourFormat() {
        assertEquals("08:00", formatLocalizedTime("08:00"))
        assertEquals("13:20", formatLocalizedTime("13:20"))
        assertEquals("23:00", formatLocalizedTime("23:00"))
        assertEquals("00:05", formatLocalizedTime("0:5"))
    }

    @Test
    fun aTextThatIsNotATimeStaysAsItIs() {
        assertEquals("soon", formatLocalizedTime("soon"))
        assertEquals("25:00", formatLocalizedTime("25:00"))
    }
}
