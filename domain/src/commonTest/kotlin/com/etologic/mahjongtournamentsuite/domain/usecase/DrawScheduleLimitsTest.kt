package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration

/** What the draw refuses, what it does when no way finds a draw in time, and the draw on the slots of a tournament. */
class DrawScheduleLimitsTest {
    private val noTime: (Int, Int) -> Duration = { _, _ -> Duration.ZERO }
    private val draw = DrawTournamentScheduleUseCase()
    private val outOfTime = DrawTournamentScheduleUseCase(firstLimit = noTime, secondLimit = noTime, blocksLimit = noTime)

    private fun slots(vararg teams: Int) = teams.mapIndexed { index, team -> TournamentPlayer(id = index + 1, name = "P${index + 1}", team = team) }

    @Test
    fun aDrawNeedsTablesOfFourAndRounds() = runTest {
        assertNull(draw(numPlayers = 0, numRounds = 1, isTeams = false, maxConcurrency = 1) {})
        assertNull(draw(numPlayers = 6, numRounds = 1, isTeams = false, maxConcurrency = 1) {})
        assertNull(draw(numPlayers = 8, numRounds = 0, isTeams = false, maxConcurrency = 1) {})
    }

    @Test
    fun withoutTimeThereIsNoDraw() = runTest {
        assertNull(outOfTime(numPlayers = 8, numRounds = 2, isTeams = false, maxConcurrency = 1) {})
        assertNull(outOfTime(numPlayers = 16, numRounds = 3, isTeams = false, maxConcurrency = 1) {}, "the pattern needs its time too")
        assertNull(outOfTime(numPlayers = 8, numRounds = 9, isTeams = false, maxConcurrency = 1) {}, "rounds in blocks need their time too")
    }

    @Test
    fun theSlotsOfATournamentMustBeTablesOfFourWithDifferentNumbers() = runTest {
        assertNull(draw.onSlots(emptyList(), numRounds = 1, isTeams = false, maxConcurrency = 1) {})
        assertNull(draw.onSlots(slots(0, 0, 0, 0, 0, 0), numRounds = 1, isTeams = false, maxConcurrency = 1) {})
        assertNull(draw.onSlots(slots(0, 0, 0, 0), numRounds = 0, isTeams = false, maxConcurrency = 1) {})
        val repeated = slots(0, 0, 0, 0).map { it.copy(id = 1) }
        assertNull(draw.onSlots(repeated, numRounds = 1, isTeams = false, maxConcurrency = 1) {})
    }

    @Test
    fun teamsThatAreNotOfFourNeedTimeForTheirBlocks() = runTest {
        val incomplete = (1..24).filter { it !in setOf(1, 2, 3, 5) }.map { TournamentPlayer(id = it, name = "P$it", team = (it - 1) / 4 + 1) }

        assertNull(outOfTime.onSlots(incomplete, numRounds = 4, isTeams = true, maxConcurrency = 1) {})
    }

    @Test
    fun aDrawMovesOntoSlotsOnlyWhenTheyFit() = runTest {
        val found = assertNotNull(draw(numPlayers = 16, numRounds = 1, isTeams = true, maxConcurrency = 1) {})
        val teams = (1..16).map { (it - 1) / 4 + 1 }.toIntArray()

        assertNull(found.onSlots(slots(1, 1, 1, 1), isTeams = true), "another count")
        assertNull(found.onSlots(slots(*teams).map { it.copy(id = 3) }, isTeams = true), "a repeated number")
        assertNull(found.onSlots(slots(1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4), isTeams = true), "a team without 4 slots")
        assertNull(found.onSlots(slots(*IntArray(16) { it / 8 + 1 }), isTeams = true), "another number of teams")

        val moved = assertNotNull(found.onSlots(slots(*teams).map { it.copy(id = it.id * 10, team = it.team + 4) }, isTeams = true))
        assertEquals((1..16).map { it * 10 }.toSet(), moved.tables.flatMap { it.playerIds }.toSet())
    }
}
