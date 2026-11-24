package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The draw of more rounds than fit without repeats (docs/draw.md), or of the rounds left of a tournament that already
 * played some ([history]).
 *
 * The rounds of the tournament go in blocks of [RepeatFreeRounds.sure] rounds, the ones the draw finds for sure: in the first block nobody meets the same
 * rival twice; in the second, nobody meets them a third time, and so on. So after k blocks, nobody has met anyone more
 * than k times. The last block, when it is not complete, follows the same rule.
 *
 * In a new draw of a size with a perfect pattern ([PerfectPattern]), each block is the pattern with the players placed
 * at random in it, a new placing each block: every pair meets exactly once in each complete block, and at most once
 * in the last one. Otherwise, and always with tables already played, each block is drawn by swaps
 * ([GenerateScheduleBySwapsUseCase]) with the tables already drawn or played and the limit of the block.
 *
 * A block by swaps has [blockTry] of real time. With tables already played the rounds left of their block may have no
 * way to finish without repeats, or the search may be stuck: it tries again with another seed, and then allows one
 * more meeting from that block on (it starts a new block there).
 */
class DrawRoundsInBlocksUseCase(
    private val swaps: GenerateScheduleBySwapsUseCase = GenerateScheduleBySwapsUseCase(),
    private val blockTry: Duration = 5.seconds,
) {
    suspend operator fun invoke(
        /** The slots that play. A multiple of 4. */
        playerIds: List<Int>,
        /** The rounds to draw, in order, after the rounds of [history]. */
        roundIds: List<Int>,
        /** The team of each slot in a team tournament; empty without teams. */
        teamOf: Map<Int, Int> = emptyMap(),
        /** The tables of the rounds already drawn and kept (played, or being played). */
        history: List<TournamentTable> = emptyList(),
        maxConcurrency: Int = 1,
        seed: Long = Random.Default.nextLong(),
        onProgress: (TournamentScheduleCalcProgress) -> Unit = {},
    ): BlocksResult? {
        val count = playerIds.size
        if (count == 0 || count % 4 != 0 || playerIds.toSet().size != count || roundIds.isEmpty()) return null
        val isTeams = teamOf.values.any { it != 0 }
        val perBlock = RepeatFreeRounds.sure(count, isTeams)
        if (perBlock < 1) return null
        val random = Random(seed)
        val playedRounds = history.map { it.roundId }.toSet().size
        val met = history.flatMap { pairsOf(it.playerIds) }.toMutableList()
        val drawn = ArrayList<TournamentTable>()
        var swapsMade = 0L
        // The meetings allowed beyond the block number, after a block that could not finish (see blockTry).
        var raised = 0
        // The position of each round in the tournament says its block.
        val blocks = roundIds.withIndex().groupBy { (index, _) -> (playedRounds + index) / perBlock }
        for ((block, rounds) in blocks.entries.sortedBy { it.key }) {
            val ids = rounds.map { it.value }
            // The pattern only fits on blocks of the pattern: with tables played, the pairs that met are any.
            val pattern = if (history.isEmpty()) fromPattern(playerIds, ids, teamOf, isTeams, random) else null
            val tables = pattern ?: run {
                var found: BySwapsResult? = null
                for (attempt in 0 until TRIES_PER_BLOCK) {
                    // The last try allows one more meeting, and so do the blocks after it.
                    if (attempt == TRIES_PER_BLOCK - 1) raised++
                    val seedOfTry = random.nextLong()
                    found = withContext(Dispatchers.Default) {
                        withTimeoutOrNull(blockTry) {
                            swaps(
                                playerIds = playerIds,
                                roundIds = ids,
                                teamOf = teamOf,
                                alreadyMet = met,
                                meetLimit = block + 1 + raised,
                                maxConcurrency = maxConcurrency,
                                seed = seedOfTry,
                                onProgress = { onProgress(it.copy(tried = swapsMade + it.tried)) },
                            )
                        }
                    }
                    if (found != null) break
                }
                val result = found ?: return null
                swapsMade += result.swaps
                result.tables
            }
            drawn += tables
            tables.forEach { met += pairsOf(it.playerIds) }
        }
        return BlocksResult(tables = drawn, swaps = swapsMade, perBlock = perBlock, extraMeetings = raised)
    }

    private companion object {
        const val TRIES_PER_BLOCK = 3
    }

    /**
     * The block as the perfect pattern, with the players at random places, [roundIds] of its rounds in random order
     * and, with teams, one round of the pattern as the teams. Null when the size has no pattern or the teams are not
     * N / 4 teams of 4.
     */
    private fun fromPattern(
        playerIds: List<Int>,
        roundIds: List<Int>,
        teamOf: Map<Int, Int>,
        isTeams: Boolean,
        random: Random,
    ): List<TournamentTable>? {
        val pattern = PerfectPattern.of(playerIds.size) ?: return null
        val place = IntArray(playerIds.size)
        val rounds: List<List<IntArray>>
        if (!isTeams) {
            playerIds.shuffled(random).forEachIndexed { position, id -> place[position] = id }
            rounds = pattern.shuffled(random)
        } else {
            val teams = playerIds.groupBy { teamOf[it] ?: 0 }.values.toList()
            if (teams.size != playerIds.size / 4 || teams.any { it.size != 4 }) return null
            val teamsRound = random.nextInt(pattern.size)
            // The lines of that round are the teams: each real team takes one line, its players the 4 places.
            pattern[teamsRound].zip(teams.shuffled(random)).forEach { (line, team) ->
                line.zip(team.shuffled(random)).forEach { (position, id) -> place[position] = id }
            }
            rounds = pattern.filterIndexed { index, _ -> index != teamsRound }.shuffled(random)
        }
        if (rounds.size < roundIds.size) return null
        return rounds.zip(roundIds).flatMap { (tables, roundId) ->
            tables.shuffled(random).mapIndexed { tableIndex, seats ->
                TournamentTable(
                    roundId = roundId,
                    tableId = tableIndex + 1,
                    playerIds = seats.map { place[it] }.shuffled(random),
                    isCompleted = false,
                    useTotalsOnly = false,
                    hasProgress = false,
                )
            }
        }
    }
}

data class BlocksResult(
    val tables: List<TournamentTable>,
    /** Swaps made by the blocks drawn by swaps; 0 when every block was the pattern. */
    val swaps: Long,
    /** The rounds of each block: the rounds that fit without repeats. */
    val perBlock: Int,
    /**
     * The meetings allowed beyond one more each block, when a block could not finish in time (usually the rounds left
     * of a block already started): 0 when every block kept its limit.
     */
    val extraMeetings: Int = 0,
)

/** The pairs of players of one table. */
internal fun pairsOf(playerIds: List<Int>): List<Pair<Int, Int>> = buildList {
    for (i in playerIds.indices) for (j in i + 1 until playerIds.size) add(playerIds[i] to playerIds[j])
}
