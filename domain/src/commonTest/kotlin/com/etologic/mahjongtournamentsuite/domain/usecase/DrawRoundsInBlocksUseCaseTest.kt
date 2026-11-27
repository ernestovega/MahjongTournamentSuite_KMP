package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The draw in blocks: what it refuses, the perfect pattern with and without teams, and a block that finds no way. */
class DrawRoundsInBlocksUseCaseTest {
    private val draw = DrawRoundsInBlocksUseCase()

    private fun pairs(tables: List<TournamentTable>) = tables.flatMap { table ->
        val ids = table.playerIds.sorted()
        ids.indices.flatMap { a -> (a + 1 until ids.size).map { b -> ids[a] to ids[b] } }
    }

    @Test
    fun aDrawWithoutPlayersRoundsOrWithRepeatedSlotsIsRefused() = runTest {
        assertNull(draw(playerIds = emptyList(), roundIds = listOf(1)))
        assertNull(draw(playerIds = (1..6).toList(), roundIds = listOf(1)))
        assertNull(draw(playerIds = listOf(1, 2, 3, 3), roundIds = listOf(1)))
        assertNull(draw(playerIds = (1..8).toList(), roundIds = emptyList()))
    }

    @Test
    fun aTeamTournamentOfOneTableHasNoRoundWithoutRepeats() = runTest {
        assertNull(draw(playerIds = (1..4).toList(), roundIds = listOf(1), teamOf = (1..4).associateWith { 1 }))
    }

    @Test
    fun sixteenPlayersPlayTheWholePatternAndThenStartAnotherBlock() = runTest {
        val result = assertNotNull(draw(playerIds = (1..16).toList(), roundIds = (1..6).toList(), seed = 1))

        assertEquals(5, result.perBlock)
        assertEquals(24, result.tables.size)
        // In the first block every pair meets once.
        val first = pairs(result.tables.filter { it.roundId <= 5 })
        assertEquals(first.size, first.toSet().size)
        assertEquals(0L, result.swaps)
    }

    @Test
    fun withTeamsOneRoundOfThePatternIsTheTeams() = runTest {
        val teamOf = (1..16).associateWith { (it - 1) / 4 + 1 }

        val result = assertNotNull(draw(playerIds = (1..16).toList(), roundIds = (1..4).toList(), teamOf = teamOf, seed = 2))

        assertTrue(result.tables.none { table -> table.playerIds.map { teamOf[it] }.toSet().size < 4 })
    }

    @Test
    fun teamsThatAreNotOfFourAreDrawnBySwaps() = runTest {
        // 8 teams of 2: the pattern does not fit, so the swaps draw the rounds.
        val teamOf = (1..16).associateWith { (it - 1) / 2 + 1 }

        val result = assertNotNull(draw(playerIds = (1..16).toList(), roundIds = listOf(1, 2), teamOf = teamOf, seed = 3))

        assertEquals(8, result.tables.size)
    }

    @Test
    fun aBlockThatFindsNoWayInItsTimeGivesNoDraw() = runTest {
        val noTime = DrawRoundsInBlocksUseCase(blockTry = Duration.ZERO)
        val played = listOf(TournamentTable(roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4), isCompleted = true, useTotalsOnly = false, hasProgress = true))

        assertNull(noTime(playerIds = (1..8).toList(), roundIds = listOf(2), history = played))
    }

    @Test
    fun fourTeamsOfUnevenSizesAreNotThePattern() = runTest {
        // 4 teams, as many as the pattern needs, but of 5, 3, 4 and 4 players: a team of 5 never fits 4 tables.
        val ends = listOf(0, 5, 8, 12, 16)
        val teamOf = (1..16).associateWith { id -> ends.indexOfFirst { id <= it } }
        val noTime = DrawRoundsInBlocksUseCase(blockTry = Duration.ZERO)

        assertNull(noTime(playerIds = (1..16).toList(), roundIds = listOf(1, 2), teamOf = teamOf, seed = 4))
    }

    @Test
    fun slotsWithoutATeamSitTogetherAsOneTeam() = runTest {
        // Slots 13 to 16 have no team: they are the fourth team of the pattern, and never sit together.
        val teamOf = (1..12).associateWith { (it - 1) / 4 + 1 }

        val result = assertNotNull(draw(playerIds = (1..16).toList(), roundIds = (1..4).toList(), teamOf = teamOf, seed = 5))

        assertEquals(0L, result.swaps)
        assertTrue(result.tables.none { table -> table.playerIds.count { it > 12 } > 1 })
    }
}
