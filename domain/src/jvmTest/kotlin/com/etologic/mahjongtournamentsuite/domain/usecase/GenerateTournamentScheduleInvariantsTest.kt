package com.etologic.mahjongtournamentsuite.domain.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The draw of the app, as it is, at the sizes that tournaments use: each player once a round, nobody meets a rival
 * twice, teams apart. Written before any change of the number of players with a tournament in play
 * (docs/player-count-change.md), so that work can never change the draw of a new tournament without a red test.
 */
class GenerateTournamentScheduleInvariantsTest {
    private val generate = GenerateTournamentScheduleBruteForceParallelUseCase()

    private fun drawOf(numPlayers: Int, numRounds: Int, isTeams: Boolean, concurrency: Int = 4): TournamentScheduleCalcResult =
        assertNotNull(runBlocking { generate(numPlayers, numRounds, isTeams, concurrency) {} }, "No draw for $numPlayers x $numRounds")

    private fun check(numPlayers: Int, numRounds: Int, isTeams: Boolean, concurrency: Int = 4) {
        val draw = drawOf(numPlayers, numRounds, isTeams, concurrency)
        val problems = DrawInvariants.playerProblems(draw.players, numPlayers, isTeams) +
            DrawInvariants.tableProblems(
                tables = draw.tables,
                rounds = (1..numRounds).toList(),
                tablesPerRound = numPlayers / 4,
                playing = (1..numPlayers).toSet(),
                teamOf = if (isTeams) draw.players.associate { it.id to it.team } else emptyMap(),
            )
        if (problems.isNotEmpty()) fail("$numPlayers players x $numRounds rounds${if (isTeams) " with teams" else ""}:\n${problems.take(20).joinToString("\n")}")
        assertTrue(draw.triesUsed >= 1)
        assertEquals(numRounds * numPlayers / 4, draw.tables.size)
        // A new draw has no data in its tables.
        assertTrue(draw.tables.none { it.isCompleted || it.hasProgress || it.useTotalsOnly })
    }

    @Test
    fun individualTournamentsOfEverySize() {
        // Sizes that the draw finds in seconds (measured 22-nov). 28 x 7 or 40 x 7 are not found in 40 s: see the doc.
        listOf(16 to 1, 16 to 3, 16 to 4, 20 to 4, 24 to 4, 28 to 2, 32 to 5, 64 to 7, 100 to 7).forEach { (players, rounds) ->
            check(players, rounds, isTeams = false)
        }
    }

    @Test
    fun teamTournamentsOfEverySize() {
        // 20 x 4, 24 x 4 or 64 x 7 with teams can take minutes (measured 22-nov), so they are not here.
        listOf(16 to 1, 16 to 3, 16 to 4, 28 to 2, 100 to 7).forEach { (players, rounds) ->
            check(players, rounds, isTeams = true)
        }
    }

    @Test
    fun manyDrawsOfOneSizeAreAllValidAndNotTheSame() {
        val seen = mutableSetOf<List<List<Int>>>()
        repeat(25) {
            check(24, 4, isTeams = false, concurrency = 1 + it % 4)
            seen += drawOf(24, 4, isTeams = false).tables.map { it.playerIds }
        }
        // The draw is random: 25 draws of 24 players are never all the same.
        assertTrue(seen.size > 20, "Only ${seen.size} different draws in 25")
        repeat(10) { check(16, 3, isTeams = true, concurrency = 1 + it % 3) }
    }

    @Test
    fun oneWorkerGivesTheSameKindOfDrawAsMany() {
        check(32, 5, isTeams = false, concurrency = 1)
        check(16, 4, isTeams = true, concurrency = 1)
        check(32, 5, isTeams = false, concurrency = 32)
    }

    @Test
    fun theProgressEndsWithNoTryRunning() {
        val reports = mutableListOf<TournamentScheduleCalcProgress>()
        assertNotNull(runBlocking { generate(24, 4, false, 3) { synchronized(reports) { reports += it } } })
        assertTrue(reports.isNotEmpty())
        val last = reports.last()
        assertEquals(3, last.maxConcurrency)
        assertEquals(0, last.runningTries)
        assertTrue(last.tried >= 1)
        assertTrue(reports.zipWithNext().all { (a, b) -> b.tried >= a.tried }, "The count of tries never goes back")
    }

    @Test
    fun theRulesOfTheChecksCatchABrokenDraw() {
        val draw = drawOf(16, 2, isTeams = true)
        val tables = draw.tables.toMutableList()
        // Two players of round 1 swap with each other in round 2: they meet twice, and a team can share a table.
        val first = tables[0]
        tables[4] = tables[4].copy(playerIds = first.playerIds)
        val problems = DrawInvariants.tableProblems(tables, listOf(1, 2), 4, (1..16).toSet(), draw.players.associate { it.id to it.team })
        assertTrue(problems.any { "meet at" in it }, problems.joinToString())
        assertTrue(problems.any { "instead of each player once" in it }, problems.joinToString())
        assertTrue(DrawInvariants.playerProblems(draw.players.drop(1), 16, true).isNotEmpty())
    }
}
