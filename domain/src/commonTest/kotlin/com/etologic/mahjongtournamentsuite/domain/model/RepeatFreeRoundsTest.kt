package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepeatFreeRoundsTest {
    @Test
    fun theCeilingIsWhatThreeNewRivalsARoundAllow() {
        assertEquals(5, RepeatFreeRounds.ceiling(16, isTeams = false))
        assertEquals(4, RepeatFreeRounds.ceiling(16, isTeams = true))
        assertEquals(6, RepeatFreeRounds.ceiling(20, isTeams = false))
        assertEquals(9, RepeatFreeRounds.ceiling(28, isTeams = false))
        assertEquals(8, RepeatFreeRounds.ceiling(28, isTeams = true))
        assertEquals(19, RepeatFreeRounds.ceiling(60, isTeams = false))
        assertEquals(18, RepeatFreeRounds.ceiling(60, isTeams = true))
        assertEquals(133, RepeatFreeRounds.ceiling(400, isTeams = false))
    }

    @Test
    fun aSizeThatIsNotTablesOfFourHasNoRounds() {
        listOf(-4, 0, 1, 17, 30).forEach { players ->
            assertEquals(0, RepeatFreeRounds.ceiling(players, isTeams = false), "$players")
            assertEquals(0, RepeatFreeRounds.max(players, isTeams = true), "$players")
        }
    }

    @Test
    fun theMaximumIsNeverAboveTheCeilingAndTeamsHaveOneRoundLess() {
        (4..400 step 4).forEach { players ->
            val individual = RepeatFreeRounds.max(players, isTeams = false)
            assertTrue(individual in 1..RepeatFreeRounds.ceiling(players, isTeams = false), "$players")
            assertEquals((individual - 1).coerceAtLeast(0), RepeatFreeRounds.max(players, isTeams = true), "$players")
        }
    }

    @Test
    fun twentyPlayersFitOneRoundLessThanTheCeiling() {
        assertEquals(5, RepeatFreeRounds.max(20, isTeams = false))
        assertEquals(4, RepeatFreeRounds.max(20, isTeams = true))
    }

    @Test
    fun thePerfectSizesAreFourMoreThanAMultipleOfTwelve() {
        assertEquals(listOf(4, 16, 28, 40, 52, 64), (4..64 step 4).filter { RepeatFreeRounds.isPerfect(it) })
        assertFalse(RepeatFreeRounds.isPerfect(20))
    }

    @Test
    fun theDrawIsSureOfTheWholePatternAndOfLessForTheOtherSizes() {
        listOf(16, 28, 40, 64, 256).forEach { players ->
            assertEquals(RepeatFreeRounds.max(players, isTeams = false), RepeatFreeRounds.sure(players, isTeams = false), "$players")
            assertEquals(RepeatFreeRounds.max(players, isTeams = true), RepeatFreeRounds.sure(players, isTeams = true), "$players")
        }
        assertEquals(7, RepeatFreeRounds.sure(32, isTeams = false))
        assertEquals(6, RepeatFreeRounds.sure(32, isTeams = true))
        assertEquals(5, RepeatFreeRounds.sure(24, isTeams = false))
        (16..400 step 4).forEach { players ->
            listOf(false, true).forEach { teams ->
                val sure = RepeatFreeRounds.sure(players, teams)
                assertTrue(sure in 1..RepeatFreeRounds.max(players, teams), "$players $teams: $sure")
            }
        }
    }

    @Test
    fun theTimesTwoPlayersMeetAtMostGrowOneForEachBlockBegun() {
        assertEquals(1, RepeatFreeRounds.timesAtMost(16, 5, isTeams = false))
        assertEquals(2, RepeatFreeRounds.timesAtMost(16, 6, isTeams = false))
        assertEquals(2, RepeatFreeRounds.timesAtMost(16, 10, isTeams = false))
        assertEquals(3, RepeatFreeRounds.timesAtMost(16, 11, isTeams = false))
        assertEquals(3, RepeatFreeRounds.timesAtMost(16, 9, isTeams = true))
        assertEquals(1, RepeatFreeRounds.timesAtMost(60, 10, isTeams = false))
        assertEquals(0, RepeatFreeRounds.timesAtMost(17, 3, isTeams = false))
    }
}
