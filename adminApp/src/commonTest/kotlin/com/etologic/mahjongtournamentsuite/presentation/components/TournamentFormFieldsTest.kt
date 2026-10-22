package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TournamentFormFieldsTest {

    @Test
    fun datesConvertBetweenApiAndDisplayFormats() {
        assertEquals("28/09/2026", "2026-09-28".toDisplayTournamentDate())
        assertEquals("2026-09-28", "28/09/2026".toIsoTournamentDateOrNull())
    }

    @Test
    fun invalidDatesDoNotConvert() {
        assertEquals("", "2026-02-30".toDisplayTournamentDate())
        assertNull("30/02/2026".toIsoTournamentDateOrNull())
        assertNull("2026-09-28".toIsoTournamentDateOrNull())
    }

    @Test
    fun endDateMovesToStartWhenItIsEmptyOrEarlier() {
        assertEquals("28/09/2026", adjustedEndDate("28/09/2026", ""))
        assertEquals("28/09/2026", adjustedEndDate("28/09/2026", "27/09/2026"))
    }

    @Test
    fun laterEndDateIsNotChanged() {
        assertEquals("30/09/2026", adjustedEndDate("28/09/2026", "30/09/2026"))
    }

    @Test
    fun partialEndDateRemainsEditable() {
        assertEquals("30/", adjustedEndDate("28/09/2026", "30/"))
    }
}

class MatchTimeFieldHelpersTest {
    @Test
    fun matchDurationAcceptsWholeMinutesFromOneToTheMaximum() {
        assertEquals(120, matchDurationOrNull("120"))
        assertEquals(95, matchDurationOrNull(" 95 "))
        assertEquals(MAX_MATCH_DURATION_MINUTES, matchDurationOrNull(MAX_MATCH_DURATION_MINUTES.toString()))
        assertNull(matchDurationOrNull("0"))
        assertNull(matchDurationOrNull((MAX_MATCH_DURATION_MINUTES + 1).toString()))
        assertNull(matchDurationOrNull(""))
        assertNull(matchDurationOrNull("1h30"))
    }

    @Test
    fun extraMinutesAcceptWholeMinutesFromOneToTheMaximum() {
        assertEquals(15, matchExtraMinutesOrNull("15"))
        assertEquals(MAX_MATCH_EXTRA_MINUTES, matchExtraMinutesOrNull(MAX_MATCH_EXTRA_MINUTES.toString()))
        assertNull(matchExtraMinutesOrNull("0"))
        assertNull(matchExtraMinutesOrNull((MAX_MATCH_EXTRA_MINUTES + 1).toString()))
        assertNull(matchExtraMinutesOrNull(""))
    }

    @Test
    fun timeZoneNamesNeedTheAreaAndCityShape() {
        assertEquals(true, isTimeZoneIdShape("Europe/Madrid"))
        assertEquals(true, isTimeZoneIdShape(" America/Argentina/Buenos_Aires "))
        assertEquals(true, isTimeZoneIdShape("UTC"))
        assertEquals(false, isTimeZoneIdShape(""))
        assertEquals(false, isTimeZoneIdShape("Madrid"))
        assertEquals(false, isTimeZoneIdShape("Europe/"))
        assertEquals(false, isTimeZoneIdShape("GMT+2"))
    }
}
