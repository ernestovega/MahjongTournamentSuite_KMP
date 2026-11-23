package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The second way to draw (docs/draw.md): the same rules as the first, at the sizes the first one does not solve in time,
 * with tables already played, and with slots that are not 1..N (a tournament that lost a table).
 */
class GenerateScheduleBySwapsTest {
    private val draw = GenerateScheduleBySwapsUseCase()

    private fun drawOf(
        ids: List<Int>,
        rounds: List<Int>,
        teamOf: Map<Int, Int> = emptyMap(),
        history: List<TournamentTable> = emptyList(),
        seed: Long = 1,
        limitSeconds: Int = 90,
    ): BySwapsResult = assertNotNull(
        runBlocking {
            withTimeoutOrNull(limitSeconds.seconds) {
                draw(ids, rounds, teamOf, pairsOf(history), maxConcurrency = 4, seed = seed)
            }
        },
        "No draw for ${ids.size} players x ${rounds.size} rounds in $limitSeconds s",
    )

    private fun check(
        ids: List<Int>,
        rounds: List<Int>,
        teamOf: Map<Int, Int> = emptyMap(),
        history: List<TournamentTable> = emptyList(),
        seed: Long = 1,
    ): BySwapsResult {
        val result = drawOf(ids, rounds, teamOf, history, seed)
        val problems = DrawInvariants.tableProblems(result.tables, rounds, ids.size / 4, ids.toSet(), teamOf, history)
        if (problems.isNotEmpty()) fail("${ids.size} x ${rounds.size}:\n${problems.take(20).joinToString("\n")}")
        assertTrue(result.tables.none { it.isCompleted || it.hasProgress || it.useTotalsOnly })
        return result
    }

    private fun teamsOf(ids: List<Int>) = ids.associateWith { (it - 1) / 4 + 1 }

    @Test
    fun theSizesThatTheFirstWayDoesNotSolveInTime() {
        // Measured 22-nov: the first way finds none of 24 x 6, 28 x 7 or 40 x 7 in 40 s.
        listOf(16 to 5, 20 to 5, 24 to 6, 28 to 7, 32 to 7, 40 to 7, 64 to 10).forEach { (players, rounds) ->
            check((1..players).toList(), (1..rounds).toList())
        }
    }

    @Test
    fun teamTournaments() {
        listOf(16 to 4, 20 to 4, 24 to 4, 32 to 5, 40 to 7, 64 to 7).forEach { (players, rounds) ->
            val ids = (1..players).toList()
            check(ids, (1..rounds).toList(), teamsOf(ids))
        }
    }

    @Test
    fun theMaximumOf16PlayersIsFiveRounds() {
        // Each round a player meets 3 of the other 15, so 5 rounds meet all of them once. A sixth one has no draw.
        check((1..16).toList(), (1..5).toList())
        val start = TimeSource.Monotonic.markNow()
        assertNull(runBlocking { withTimeoutOrNull(500.milliseconds) { draw((1..16).toList(), (1..6).toList()) } })
        assertTrue(start.elapsedNow() < 5.seconds, "A time limit stops it")
    }

    @Test
    fun theRoundsLeftOfATournamentInPlayAvoidTheTablesPlayed() {
        val ids = (1..32).toList()
        val first = check(ids, (1..7).toList()).tables
        val played = first.filter { it.roundId <= 3 }
        // The same 32 players draw rounds 4 to 7 again, with nobody meeting a rival of rounds 1 to 3.
        check(ids, (4..7).toList(), history = played)
        // With teams, too.
        val teams = teamsOf(ids)
        val teamPlayed = check(ids, (1..5).toList(), teams).tables.filter { it.roundId <= 2 }
        check(ids, (3..5).toList(), teams, teamPlayed)
    }

    @Test
    fun aTableLessInATournamentInPlayKeepsTheNumbersOfThoseWhoStay() {
        val all = (1..32).toList()
        val played = check(all, (1..7).toList()).tables.filter { it.roundId <= 2 }
        // Slots 5, 12, 20 and 31 leave after round 2. The others keep their numbers: the slots are not 1..N any more.
        val staying = all - setOf(5, 12, 20, 31)
        val result = check(staying, (3..7).toList(), history = played)
        assertTrue(result.tables.flatMap { it.playerIds }.none { it in setOf(5, 12, 20, 31) })
        // And a table more: slots 33 to 36 come in round 3 with nobody met before.
        check(all + (33..36), (3..7).toList(), history = played)
    }

    @Test
    fun withALimitOfTwoNobodyMeetsAnyoneThreeTimes() {
        // 16 players, 5 rounds played (everyone met everyone once), and 5 more with the limit 2: possible only with
        // every pair exactly once more. Teammates still never meet.
        val ids = (1..16).toList()
        val played = check(ids, (1..5).toList()).tables
        val more = assertNotNull(
            runBlocking { withTimeoutOrNull(60.seconds) { draw(ids, (6..10).toList(), alreadyMet = pairsOf(played), meetLimit = 2, maxConcurrency = 4) } },
        ).tables
        val met = pairsOf(played + more).map { (a, b) -> minOf(a, b) to maxOf(a, b) }.groupingBy { it }.eachCount()
        assertEquals(120, met.size)
        assertTrue(met.values.all { it == 2 }, "$met")
        assertNull(runBlocking { draw(ids, listOf(1), meetLimit = 0) })
    }

    @Test
    fun aPairThatAlreadyMetAsOftenAsItMayDoesNotMeetAgain() {
        // 1 and 2 met twice already: with the limit 2 they may not share a table; the others may meet twice.
        val ids = (1..16).toList()
        val history = listOf(1 to 2, 2 to 1)
        repeat(5) { seed ->
            val result = assertNotNull(runBlocking { draw(ids, (1..6).toList(), alreadyMet = history, meetLimit = 2, seed = seed.toLong()) })
            assertTrue(result.tables.none { 1 in it.playerIds && 2 in it.playerIds })
            val met = pairsOf(result.tables).map { (a, b) -> minOf(a, b) to maxOf(a, b) }.groupingBy { it }.eachCount()
            assertTrue(met.values.all { it <= 2 })
        }
    }

    @Test
    fun manyDrawsAreAllValidAndNotTheSame() {
        val ids = (1..24).toList()
        val seen = (1L..10L).map { seed -> check(ids, (1..4).toList(), seed = seed).tables.map { it.playerIds } }.toSet()
        assertEquals(10, seen.size, "Ten seeds give ten different draws")
    }

    @Test
    fun aWrongInputHasNoDraw() = runBlocking {
        assertNull(draw(emptyList(), listOf(1)))
        assertNull(draw((1..18).toList(), listOf(1)))
        assertNull(draw((1..16).toList(), emptyList()))
        assertNull(draw((1..15).toList() + 1, listOf(1)))
    }

    @Test
    fun theProgressEndsWithNoWorkerRunning() {
        val reports = mutableListOf<TournamentScheduleCalcProgress>()
        runBlocking { draw((1..28).toList(), (1..5).toList(), maxConcurrency = 3) { synchronized(reports) { reports += it } } }
        val last = reports.last()
        assertEquals(0, last.runningTries)
        assertEquals(3, last.maxConcurrency)
        assertTrue(reports.all { it.backup }, "Every report says it is the second way")
    }

    private fun pairsOf(tables: List<TournamentTable>): List<Pair<Int, Int>> = tables.flatMap { table ->
        val ids = table.playerIds
        ids.indices.flatMap { i -> (i + 1 until ids.size).map { j -> ids[i] to ids[j] } }
    }
}
