package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The draw of the app: the first way within its time, then the second way (or the pattern), then the blocks or no draw
 * (docs/draw.md).
 */
class DrawTournamentScheduleUseCaseTest {
    private fun checkDraw(result: TournamentScheduleCalcResult, players: Int, rounds: Int, isTeams: Boolean) {
        val problems = DrawInvariants.playerProblems(result.players, players, isTeams) +
            DrawInvariants.tableProblems(
                tables = result.tables,
                rounds = (1..rounds).toList(),
                tablesPerRound = players / 4,
                playing = (1..players).toSet(),
                teamOf = if (isTeams) result.players.associate { it.id to it.team } else emptyMap(),
            )
        if (problems.isNotEmpty()) fail(problems.take(20).joinToString("\n"))
        assertTrue(result.triesUsed >= 1)
    }

    @Test
    fun anEasyDrawIsMadeByTheFirstWay() {
        val reports = mutableListOf<TournamentScheduleCalcProgress>()
        val result = assertNotNull(runBlocking { DrawTournamentScheduleUseCase()(24, 4, false, 4) { synchronized(reports) { reports += it } } })
        checkDraw(result, 24, 4, false)
        assertFalse(result.usedBackup)
        assertTrue(reports.none { it.backup })
    }

    @Test
    fun whenTheFirstWayRunsOutOfTimeTheSecondMakesTheSameKindOfDraw() {
        // No time at all for the first way: with 1 ms a fast machine (CI) finished an easy size with it, without the second.
        val draw = DrawTournamentScheduleUseCase(firstLimit = { _, _ -> Duration.ZERO })
        for ((players, rounds, teams) in listOf(Triple(24, 4, false), Triple(16, 4, true), Triple(28, 5, false), Triple(32, 5, true))) {
            val reports = mutableListOf<TournamentScheduleCalcProgress>()
            val result = assertNotNull(runBlocking { draw(players, rounds, teams, 4) { synchronized(reports) { reports += it } } })
            checkDraw(result, players, rounds, teams)
            assertTrue(result.usedBackup)
            assertTrue(reports.last().backup && reports.last().runningTries == 0)
        }
    }

    /** A caller on virtual time (runTest, as the presenter tests) does not make the limits run out at once. */
    @Test
    fun theLimitsCountRealTimeAlsoInATestScope() = runTest {
        val result = assertNotNull(DrawTournamentScheduleUseCase()(16, 4, true, 2) {})
        checkDraw(result, 16, 4, true)
        // An easy size: this test is about the clock, not about how hard the draw is (a busy machine must not fail it).
        val second = assertNotNull(DrawTournamentScheduleUseCase(firstLimit = { _, _ -> 1.milliseconds })(24, 4, false, 2) {})
        checkDraw(second, 24, 4, false)
    }

    @Test
    fun whenNeitherFindsOneThereIsNoDraw() {
        // 44 x 10 fits without repeats, and the draw finds it for sure, but not with no time at all.
        val draw = DrawTournamentScheduleUseCase(firstLimit = { _, _ -> Duration.ZERO }, secondLimit = { _, _ -> Duration.ZERO })
        assertNull(runBlocking { draw(44, 10, false, 2) {} })
        assertNull(runBlocking { draw(18, 2, false, 2) {} })
        assertNull(runBlocking { draw(16, 0, false, 2) {} })
    }

    @Test
    fun moreRoundsThanFitGoInBlocks() {
        // 16 players can play 5 rounds at most without meeting twice: 6 rounds are a block of 5 and one more round.
        val reports = mutableListOf<TournamentScheduleCalcProgress>()
        val result = assertNotNull(runBlocking { DrawTournamentScheduleUseCase()(16, 6, false, 2) { synchronized(reports) { reports += it } } })
        assertEquals(5, result.perBlock)
        assertEquals(false, result.usedBackup)
        assertEquals((1..16).toList(), result.players.map { it.id })
        val met = result.tables.flatMap { pairsOf(it.playerIds.sorted()) }.groupingBy { it }.eachCount()
        assertEquals(2, met.values.max())
        assertEquals(0, reports.last().runningTries)
    }

    @Test
    fun theRoundsThatFitButAreNotFoundInTimeGoInBlocksToo() {
        // 32 players fit 10 rounds, but the draw finds 7 for sure: 9 rounds with no time are blocks of 7 and 2.
        val draw = DrawTournamentScheduleUseCase(firstLimit = { _, _ -> Duration.ZERO }, secondLimit = { _, _ -> 1.milliseconds })
        val result = assertNotNull(runBlocking { draw(32, 9, false, 2) {} })
        assertEquals(7, result.perBlock)
        val met = result.tables.flatMap { pairsOf(it.playerIds.sorted()) }.groupingBy { it }.eachCount()
        assertTrue(met.values.max() <= 2)
        assertEquals(9, result.tables.map { it.roundId }.distinct().size)
    }

    @Test
    fun aSizeWithAPerfectPatternUsesItWhenTheFirstWayIsLate() {
        val draw = DrawTournamentScheduleUseCase(firstLimit = { _, _ -> Duration.ZERO })
        listOf(Triple(28, 9, false), Triple(28, 8, true), Triple(40, 13, false), Triple(16, 4, true)).forEach { (players, rounds, teams) ->
            val result = assertNotNull(runBlocking { draw(players, rounds, teams, 2) {} })
            checkDraw(result, players, rounds, teams)
            assertTrue(result.usedBackup)
            assertEquals(0, result.perBlock)
        }
    }

    @Test
    fun theTimeLimitsGrowWithTablesAndRounds() {
        assertEquals(16_600.milliseconds, firstDrawLimit(4, 4))
        assertEquals(44_000.milliseconds, secondDrawLimit(4, 7))
        assertEquals(1.minutes, firstDrawLimit(100, 15))
        assertEquals(5.minutes, secondDrawLimit(100, 15))
        val sizes = listOf(4 to 1, 4 to 4, 7 to 7, 16 to 7, 25 to 7, 60 to 11, 100 to 15)
        sizes.zipWithNext().forEach { (a, b) ->
            assertTrue(firstDrawLimit(b.first, b.second) >= firstDrawLimit(a.first, a.second))
            assertTrue(secondDrawLimit(b.first, b.second) >= secondDrawLimit(a.first, a.second))
        }
        sizes.forEach { (t, r) -> assertTrue(firstDrawLimit(t, r) >= 15.seconds && secondDrawLimit(t, r) >= 30.seconds) }
    }

    /** After people leave a team tournament, the teams that lost someone are smaller; teammates still never meet. */
    @Test
    fun onTheSlotsWithIncompleteTeamsTeammatesNeverMeet() {
        val removed = setOf(1, 2, 3, 5)
        val slots = (1..24).filter { it !in removed }.map { TournamentPlayer(id = it, name = "P$it", team = (it - 1) / 4 + 1) }
        val result = assertNotNull(runBlocking { DrawTournamentScheduleUseCase().onSlots(slots, 4, true, 2) {} })
        assertEquals(slots.map { it.id }, result.players.map { it.id })
        assertEquals(slots.map { it.team }, result.players.map { it.team })
        assertEquals(20, result.tables.size)
        val teamOf = slots.associate { it.id to it.team }
        result.tables.groupBy { it.roundId }.forEach { (_, round) ->
            assertEquals(slots.map { it.id }.toSet(), round.flatMap { it.playerIds }.toSet())
            round.forEach { table -> assertEquals(4, table.playerIds.map { teamOf.getValue(it) }.toSet().size) }
        }
    }

    @Test
    fun onTheSlotsWithWholeTeamsItIsTheUsualDrawOnThoseNumbers() {
        val slots = (5..24).map { TournamentPlayer(id = it, name = "P$it", team = (it - 1) / 4 + 1) }
        val result = assertNotNull(runBlocking { DrawTournamentScheduleUseCase().onSlots(slots, 3, true, 2) {} })
        assertEquals(slots.map { it.id }, result.players.map { it.id }.sorted())
        assertEquals(15, result.tables.size)
        assertTrue(result.tables.all { t -> t.playerIds.all { it in 5..24 } })
    }

    @Test
    fun onTheSlotsRefusesACountThatIsNotTables() {
        val slots = (1..18).map { TournamentPlayer(id = it, name = "P$it", team = 0) }
        assertNull(runBlocking { DrawTournamentScheduleUseCase().onSlots(slots, 3, false, 2) {} })
    }
}
