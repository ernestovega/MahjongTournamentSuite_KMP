package com.etologic.mahjongtournamentsuite.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentDateRangeValidatorTest {
    @Test
    fun acceptsSingleDayAndMultiDayRanges() {
        assertTrue(TournamentDateRangeValidator.isValidRange("2026-02-28", "2026-02-28"))
        assertTrue(TournamentDateRangeValidator.isValidRange("2026-07-04", "2026-07-07"))
    }

    @Test
    fun rejectsInvalidCalendarDates() {
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-02-29"))
        assertTrue(TournamentDateRangeValidator.isValidDate("2028-02-29"))
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-04-31"))
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-1-01"))
    }

    @Test
    fun rejectsReversedRanges() {
        assertFalse(TournamentDateRangeValidator.isValidRange("2026-07-08", "2026-07-07"))
    }
}
