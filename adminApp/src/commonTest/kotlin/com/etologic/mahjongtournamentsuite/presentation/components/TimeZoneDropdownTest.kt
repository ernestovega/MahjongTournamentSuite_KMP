package com.etologic.mahjongtournamentsuite.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeZoneDropdownTest {
    @Test
    fun theZoneListHasRegionZonesAndUtcButNoTechnicalZones() {
        assertTrue("Europe/Madrid" in SelectableTimeZoneIds)
        assertTrue("UTC" in SelectableTimeZoneIds)
        assertFalse(SelectableTimeZoneIds.any { it.startsWith("Etc/") })
        assertTrue(timeZoneLabel("UTC").startsWith("UTC +0 "))
    }

    @Test
    fun theLabelStartsWithTheUtcOffsetOfTheZone() {
        assertEquals("UTC +0 UTC", timeZoneLabel("UTC"))
        assertEquals("UTC +5:30 Asia/Kolkata", timeZoneLabel("Asia/Kolkata"))
        assertTrue(timeZoneLabel("Europe/Madrid") in setOf("UTC +1 Europe/Madrid", "UTC +2 Europe/Madrid"))
        assertTrue(timeZoneLabel("America/Argentina/Buenos_Aires").startsWith("UTC -3 "))
    }

    @Test
    fun theZoneListGoesFromTheLowestOffsetToTheHighest() {
        val offsets = SelectableTimeZoneIds.map { timeZoneLabel(it).removePrefix("UTC ").substringBefore(' ') }
        val seconds = offsets.map { text ->
            val sign = if (text.startsWith("-")) -1 else 1
            val parts = text.drop(1).split(':')
            sign * (parts[0].toInt() * 60 + (parts.getOrNull(1)?.toInt() ?: 0))
        }
        assertEquals(seconds.sorted(), seconds)
    }
}
