package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The draw by swaps on its own: what it refuses, the pairs that already met, the teams, and a draw with no way. */
class GenerateScheduleBySwapsUseCaseTest {
    private val swaps = GenerateScheduleBySwapsUseCase()

    private fun pairs(tables: List<TournamentTable>) = tables.flatMap { table ->
        val ids = table.playerIds.sorted()
        ids.indices.flatMap { a -> (a + 1 until ids.size).map { b -> ids[a] to ids[b] } }
    }

    @Test
    fun aDrawThatCannotBeTablesOfFourIsRefused() = runTest {
        assertNull(swaps(playerIds = emptyList(), roundIds = listOf(1)))
        assertNull(swaps(playerIds = (1..6).toList(), roundIds = listOf(1)))
        assertNull(swaps(playerIds = listOf(1, 2, 3, 3), roundIds = listOf(1)))
        assertNull(swaps(playerIds = (1..8).toList(), roundIds = emptyList()))
        assertNull(swaps(playerIds = (1..8).toList(), roundIds = listOf(1), meetLimit = 0))
    }

    @Test
    fun thePairsThatAlreadyMetNeverMeetAgain() = runTest {
        // Slots 1 to 4 met; a pair of one slot and one that does not play counts for nothing.
        val met = listOf(1 to 2, 1 to 3, 1 to 4, 2 to 3, 2 to 4, 3 to 4, 1 to 1, 1 to 99, 99 to 1)

        val result = assertNotNull(swaps(playerIds = (1..16).toList(), roundIds = listOf(2, 3), alreadyMet = met, seed = 1))

        assertEquals(8, result.tables.size)
        assertTrue(pairs(result.tables).none { (a, b) -> a <= 4 && b <= 4 })
        assertEquals(pairs(result.tables).size, pairs(result.tables).toSet().size, "nobody meets twice")
    }

    @Test
    fun teammatesNeverShareATableAndSlotsWithoutATeamMayMeetAnyone() = runTest {
        // Slots 1 to 4 and 5 to 8 are teams; 9 to 12 are team 0, and 13 to 16 have no team at all.
        val teamOf = (1..12).associateWith { if (it > 8) 0 else (it - 1) / 4 + 1 }

        val result = assertNotNull(swaps(playerIds = (1..16).toList(), roundIds = listOf(1, 2, 3), teamOf = teamOf, seed = 2))

        assertTrue(result.tables.none { table -> table.playerIds.count { it <= 4 } > 1 || table.playerIds.count { it in 5..8 } > 1 })
    }

    @Test
    fun aDrawWithNoWayGoesOnUntilItsCallerStopsIt() = runTest {
        // One table cannot play two rounds without its players meeting again: there is no swap to make.
        val found = withContext(Dispatchers.Default) {
            withTimeoutOrNull(4.seconds) { swaps(playerIds = (1..4).toList(), roundIds = listOf(1, 2)) }
        }

        assertNull(found)
    }
}
