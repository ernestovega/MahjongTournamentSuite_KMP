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

    @Test
    fun knowsTheMonthsAndTheLeapYears() {
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-00-10"), "there is no month 0")
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-13-10"), "there is no month 13")
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-05-00"), "there is no day 0")
        assertTrue(TournamentDateRangeValidator.isValidDate("2026-01-31"))
        assertTrue(TournamentDateRangeValidator.isValidDate("2026-06-30"))
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-09-31"))
        assertFalse(TournamentDateRangeValidator.isValidDate("2026-11-31"))
        assertTrue(TournamentDateRangeValidator.isValidDate("2000-02-29"), "a year divisible by 400 is a leap year")
        assertFalse(TournamentDateRangeValidator.isValidDate("2100-02-29"), "a year divisible by 100 is not")
    }

    @Test
    fun aRangeNeedsTwoValidDates() {
        assertFalse(TournamentDateRangeValidator.isValidRange("2026-02-30", "2026-03-01"))
        assertFalse(TournamentDateRangeValidator.isValidRange("2026-03-01", "soon"))
    }
}
