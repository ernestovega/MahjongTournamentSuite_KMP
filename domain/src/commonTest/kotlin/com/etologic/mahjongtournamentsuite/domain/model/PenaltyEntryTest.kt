package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenaltyEntryTest {
    @Test
    fun aPenaltyThatIsNotSharedTakesThePointsFromOneSeatOnly() {
        assertEquals(listOf(0, 0, -10, 0), PenaltyEntry(seatIndex = 2, amount = 10, shared = false).seatValues())
    }

    @Test
    fun aSharedPenaltyGivesAThirdToEachOtherSeat() {
        val values = PenaltyEntry(seatIndex = 0, amount = 30, shared = true).seatValues()

        assertEquals(listOf(-30, 10, 10, 10), values)
        assertEquals(0, values.sum())
    }

    @Test
    fun aSharedPenaltyMustSplitIntoThreeWholeNumbers() {
        assertTrue(PenaltyEntry(1, 3, shared = true).isValid)
        assertFalse(PenaltyEntry(1, 10, shared = true).isValid)
        assertTrue(PenaltyEntry(1, 10, shared = false).isValid)
    }

    @Test
    fun aPenaltyNeedsAPositiveAmountAndASeatOfTheTable() {
        assertFalse(PenaltyEntry(0, 0, shared = false).isValid)
        assertFalse(PenaltyEntry(0, -5, shared = false).isValid)
        assertFalse(PenaltyEntry(-1, 5, shared = false).isValid)
        assertFalse(PenaltyEntry(4, 5, shared = false).isValid)
        assertTrue(PenaltyEntry(3, 5, shared = false).isValid)
    }
}
