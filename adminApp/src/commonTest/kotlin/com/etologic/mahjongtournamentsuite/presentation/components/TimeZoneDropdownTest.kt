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
        assertEquals(SelectableTimeZoneIds.sorted(), SelectableTimeZoneIds)
    }

    @Test
    fun theTextNarrowsTheListWhateverTheCaseAndTheSeparators() {
        val zones = listOf("America/New_York", "Europe/Madrid", "Europe/Berlin")

        assertEquals(listOf("Europe/Madrid"), matchingTimeZoneIds("madrid", zones))
        assertEquals(listOf("America/New_York"), matchingTimeZoneIds("new york", zones))
        assertEquals(listOf("Europe/Madrid", "Europe/Berlin"), matchingTimeZoneIds("europe", zones))
        assertEquals(zones, matchingTimeZoneIds("", zones))
        assertEquals(emptyList(), matchingTimeZoneIds("atlantis", zones))
    }
}
