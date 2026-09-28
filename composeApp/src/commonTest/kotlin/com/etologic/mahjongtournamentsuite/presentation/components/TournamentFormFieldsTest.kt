package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentFormFieldsTest {

    @Test
    fun datesConvertBetweenApiAndDisplayFormats() {
        assertEquals("28-09-2026", "2026-09-28".toDisplayTournamentDate())
        assertEquals("2026-09-28", "28-09-2026".toIsoTournamentDateOrNull())
    }

    @Test
    fun invalidDatesDoNotConvert() {
        assertEquals("", "2026-02-30".toDisplayTournamentDate())
        assertNull("30-02-2026".toIsoTournamentDateOrNull())
        assertNull("2026-09-28".toIsoTournamentDateOrNull())
    }

    @Test
    fun endDateMovesToStartWhenItIsEmptyOrEarlier() {
        assertEquals("28-09-2026", adjustedEndDate("28-09-2026", ""))
        assertEquals("28-09-2026", adjustedEndDate("28-09-2026", "27-09-2026"))
    }

    @Test
    fun laterEndDateIsNotChanged() {
        assertEquals("30-09-2026", adjustedEndDate("28-09-2026", "30-09-2026"))
    }

    @Test
    fun partialEndDateRemainsEditable() {
        assertEquals("30-", adjustedEndDate("28-09-2026", "30-"))
    }
}
