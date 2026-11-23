package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * More rounds than fit without repeats (docs/draw.md): blocks of the rounds that fit, and after k blocks nobody has met
 * anyone more than k times. With a perfect size, every complete block is the pattern: every pair exactly once.
 */
class DrawRoundsInBlocksTest {
    private val draw = DrawRoundsInBlocksUseCase()

    private fun teamsOf(ids: List<Int>) = ids.associateWith { (it - 1) / 4 + 1 }

    private fun drawOf(
        ids: List<Int>,
        rounds: List<Int>,
        teamOf: Map<Int, Int> = emptyMap(),
        history: List<TournamentTable> = emptyList(),
        seed: Long = 1,
    ): BlocksResult = assertNotNull(
        runBlocking { withTimeoutOrNull(120.seconds) { draw(ids, rounds, teamOf, history, maxConcurrency = 4, seed = seed) } },
        "No draw in blocks for ${ids.size} players x ${rounds.size} rounds",
    )

    /** The meetings of each pair in [tables], as a map from the pair (lower first) to times. */
    private fun meetings(tables: List<TournamentTable>): Map<Pair<Int, Int>, Int> = tables.flatMap { table -> pairsOf(table.playerIds.sorted()) }.groupingBy { it }.eachCount()

    /**
     * Every round seats each player once at numbered tables of 4, teammates never meet, and after each block (counting
     * [history]) nobody has met anyone more times than the blocks so far.
     */
    private fun check(
        ids: List<Int>,
        rounds: List<Int>,
        teamOf: Map<Int, Int> = emptyMap(),
        history: List<TournamentTable> = emptyList(),
        seed: Long = 1,
    ): BlocksResult {
        val result = drawOf(ids, rounds, teamOf, history, seed)
        val perBlock = RepeatFreeRounds.sure(ids.size, teamOf.isNotEmpty())
        assertEquals(perBlock, result.perBlock)
        val byRound = result.tables.groupBy { it.roundId }
        assertEquals(rounds.toSet(), byRound.keys)
        byRound.forEach { (round, tables) ->
            assertEquals((1..ids.size / 4).toList(), tables.map { it.tableId }.sorted(), "Tables of round $round")
            assertEquals(ids.sorted(), tables.flatMap { it.playerIds }.sorted(), "Round $round seats each player once")
            tables.forEach { table ->
                if (teamOf.isNotEmpty()) assertEquals(4, table.playerIds.map { teamOf[it] }.toSet().size, "Teammates at ${table.playerIds}")
                assertTrue(!table.isCompleted && !table.hasProgress && !table.useTotalsOnly)
            }
        }
        val playedRounds = history.map { it.roundId }.distinct().sorted()
        val all = playedRounds + rounds
        all.indices.forEach { upTo ->
            val block = upTo / perBlock + 1
            val soFar = all.take(upTo + 1).toSet()
            val worst = meetings((history + result.tables).filter { it.roundId in soFar }).values.maxOrNull() ?: 0
            if (worst > block) fail("${ids.size} x ${all.size}: after round ${upTo + 1} (block $block) a pair met $worst times")
        }
        return result
    }

    @Test
    fun withinTheRoundsThatFitItIsADrawWithoutRepeats() {
        val result = check((1..24).toList(), (1..5).toList())
        assertEquals(1, meetings(result.tables).values.max())
    }

    @Test
    fun sixteenPlayersPlayEveryRivalOnceInEachBlockOfFive() {
        val ids = (1..16).toList()
        val result = check(ids, (1..12).toList())
        val tables = result.tables
        // Two complete blocks: the pattern twice, every pair exactly twice. The last 2 rounds: no repeats among them.
        val tenRounds = meetings(tables.filter { it.roundId <= 10 })
        assertEquals(16 * 15 / 2, tenRounds.size)
        assertTrue(tenRounds.values.all { it == 2 }, "$tenRounds")
        assertEquals(1, meetings(tables.filter { it.roundId > 10 }).values.max())
    }

    @Test
    fun sixteenPlayersInTeamsPlayEveryOtherTeamsPlayerOnceInEachBlockOfFour() {
        val ids = (1..16).toList()
        val teams = teamsOf(ids)
        val result = check(ids, (1..9).toList(), teams)
        val eight = meetings(result.tables.filter { it.roundId <= 8 })
        // 16 players, 4 teams: each player has 12 rivals, every one twice in two blocks.
        assertEquals(16 * 12 / 2, eight.size)
        assertTrue(eight.values.all { it == 2 })
    }

    @Test
    fun sixtyFourPlayersBeyondTwentyOneRounds() {
        val ids = (1..64).toList()
        val result = check(ids, (1..23).toList())
        assertTrue(meetings(result.tables.filter { it.roundId <= 21 }).values.all { it == 1 })
    }

    @Test
    fun theOtherSizesSpreadTheRepeatsBlockByBlock() {
        listOf(20 to 11, 24 to 10, 32 to 12).forEach { (players, rounds) ->
            check((1..players).toList(), (1..rounds).toList())
        }
    }

    @Test
    fun theOtherSizesInTeams() {
        listOf(20 to 9, 24 to 8, 28 to 10).forEach { (players, rounds) ->
            val ids = (1..players).toList()
            check(ids, (1..rounds).toList(), teamsOf(ids))
        }
    }

    @Test
    fun aStartedTournamentGoesOnInTheBlockOfItsNextRound() {
        val ids = (1..16).toList()
        // 3 rounds played, from a pattern; 6 more: 2 to end the first block without repeats, then 4 of the second.
        val played = check(ids, (1..3).toList()).tables
        val rest = check(ids, (4..9).toList(), history = played)
        val firstBlock = meetings(played + rest.tables.filter { it.roundId <= 5 })
        assertEquals(1, firstBlock.values.max())
    }

    @Test
    fun aStartedTournamentWithTeamsAndSlotsThatAreNot1ToN() {
        // A tournament that lost team 2 (slots 5-8) and added team 6 (slots 21-24).
        val ids = (1..4).toList() + (9..24).toList()
        val teams = teamsOf(ids)
        val played = check(ids, (1..2).toList(), teams).tables
        check(ids, (3..8).toList(), teams, history = played)
    }

    @Test
    fun theSameSeedGivesTheSameBlocksOfThePattern() {
        val ids = (1..16).toList()
        val first = drawOf(ids, (1..10).toList(), seed = 42).tables
        val second = drawOf(ids, (1..10).toList(), seed = 42).tables
        assertEquals(first, second)
        val other = drawOf(ids, (1..10).toList(), seed = 43).tables
        assertTrue(first != other)
    }

    @Test
    fun wrongInputHasNoDraw() = runBlocking {
        assertNull(draw((1..18).toList(), listOf(1)))
        assertNull(draw(emptyList(), listOf(1)))
        assertNull(draw((1..16).toList(), emptyList()))
        assertNull(draw(listOf(1, 1, 2, 3), listOf(1)))
    }
}
