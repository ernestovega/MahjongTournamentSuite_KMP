package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The perfect pattern: every pair exactly once, every position once in each round. */
class PerfectPatternTest {
    private fun checkPerfect(players: Int) {
        val rounds = assertNotNull(PerfectPattern.of(players), "No pattern for $players")
        assertEquals((players - 1) / 3, rounds.size, "Rounds of $players")
        assertEquals(RepeatFreeRounds.max(players, isTeams = false), rounds.size)
        val met = IntArray(players * players)
        rounds.forEachIndexed { r, tables ->
            assertEquals(players / 4, tables.size, "Tables of round $r")
            assertEquals((0 until players).toList(), tables.flatMap { it.toList() }.sorted(), "Round $r seats each position once")
            tables.forEach { table ->
                assertEquals(4, table.toSet().size)
                for (i in 0 until 4) {
                    for (j in i + 1 until 4) {
                        met[table[i] * players + table[j]]++
                        met[table[j] * players + table[i]]++
                    }
                }
            }
        }
        for (a in 0 until players) {
            for (b in 0 until players) {
                if (a != b) assertEquals(1, met[a * players + b], "$a and $b meet ${met[a * players + b]} times")
            }
        }
    }

    @Test
    fun sixteenPlayersMeetEachOtherOnceInFiveRounds() = checkPerfect(16)

    @Test
    fun twentyEightPlayersMeetEachOtherOnceInNineRounds() = checkPerfect(28)

    @Test
    fun fortyPlayersMeetEachOtherOnceInThirteenRounds() = checkPerfect(40)

    @Test
    fun sixtyFourPlayersMeetEachOtherOnceInTwentyOneRounds() = checkPerfect(64)

    @Test
    fun twoHundredFiftySixPlayersMeetEachOtherOnceInEightyFiveRounds() = checkPerfect(256)

    @Test
    fun anyRoundOfThePatternIsAWayToMakeTeams() {
        // Taking one round as the teams, the other rounds never seat two teammates together: (N - 4) / 3 rounds.
        listOf(16, 28, 40, 64).forEach { players ->
            val rounds = assertNotNull(PerfectPattern.of(players))
            rounds.indices.forEach { teamsRound ->
                val teamOf = IntArray(players)
                rounds[teamsRound].forEachIndexed { team, table -> table.forEach { teamOf[it] = team } }
                val others = rounds.filterIndexed { r, _ -> r != teamsRound }
                assertEquals(RepeatFreeRounds.max(players, isTeams = true), others.size)
                others.forEach { tables -> tables.forEach { table -> assertEquals(4, table.map { teamOf[it] }.toSet().size) } }
            }
        }
    }

    @Test
    fun theOtherSizesHaveNoPattern() {
        (4..400 step 4).filter { it !in listOf(16, 28, 40, 64, 256) }.forEach { players ->
            assertNull(PerfectPattern.of(players), "$players")
        }
        assertNull(PerfectPattern.of(15))
        assertNull(PerfectPattern.of(0))
    }

    @Test
    fun theFormulaAlwaysGivesTheSamePattern() {
        val first = PerfectPattern.of(16)!!.map { round -> round.map { it.toList() } }
        val second = PerfectPattern.of(16)!!.map { round -> round.map { it.toList() } }
        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
    }
}
