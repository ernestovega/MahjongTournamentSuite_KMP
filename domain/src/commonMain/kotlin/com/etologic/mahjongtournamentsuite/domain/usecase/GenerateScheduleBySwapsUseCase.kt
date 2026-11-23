package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.random.Random

/**
 * The second way to draw, for the tournaments that the first one ([GenerateTournamentScheduleBruteForceParallelUseCase])
 * does not solve in time: many rounds for few players, or teams. See docs/draw.md.
 *
 * It keeps the same rules: every player once in each round, nobody meets the same rival twice (counting the pairs of
 * [alreadyMet], tables already played), and two players of one team never at one table. It searches in another way:
 *
 * 1. It deals every round at random, even with repeats.
 * 2. It counts the repeats: two players who meet more than once, two of a team at one table, or two who already met.
 * 3. While there is one, it takes a player of a table with a repeat and swaps them with the player of another table of
 *    that round that removes the most repeats. A swap just made is not undone for a few steps, so it does not go round
 *    in circles; and after a long time without progress, a few random swaps shake it.
 * 4. With no repeat left, the draw is ready. Seats and table numbers are shuffled.
 *
 * It runs until it finds a draw or its caller cancels it (a time limit): it never says that no draw exists.
 */
class GenerateScheduleBySwapsUseCase {
    suspend operator fun invoke(
        /** The slots that play, in any order and with any numbers. A multiple of 4. */
        playerIds: List<Int>,
        /** The round ids to draw, in order. */
        roundIds: List<Int>,
        /** The team of each slot in a team tournament; empty without teams. */
        teamOf: Map<Int, Int> = emptyMap(),
        /** The pairs of slots that already met, from tables already played: once for each time they met. */
        alreadyMet: Collection<Pair<Int, Int>> = emptyList(),
        /**
         * How many times two players may meet in all, counting [alreadyMet]: 1 for a draw without repeats. The rounds
         * beyond the ones that fit without repeats are drawn in blocks, each one allowing one more time
         * ([PlanRoundBlocksUseCase]). Teammates never meet, whatever the limit.
         */
        meetLimit: Int = 1,
        maxConcurrency: Int = 1,
        seed: Long = Random.Default.nextLong(),
        onProgress: (TournamentScheduleCalcProgress) -> Unit = {},
    ): BySwapsResult? = coroutineScope {
        val count = playerIds.size
        if (count == 0 || count % 4 != 0 || playerIds.toSet().size != count || roundIds.isEmpty() || meetLimit < 1) return@coroutineScope null
        val workers = maxConcurrency.coerceAtLeast(1)
        val index = playerIds.withIndex().associate { (position, id) -> id to position }
        // base[a * count + b]: the times a and b met before, up to meetLimit. At meetLimit they may not share a table
        // again: teammates, or a pair that met as often as it may.
        val base = IntArray(count * count)
        for ((first, second) in alreadyMet) {
            val a = index[first] ?: continue
            val b = index[second] ?: continue
            if (a == b) continue
            base[a * count + b] = minOf(base[a * count + b] + 1, meetLimit)
            base[b * count + a] = base[a * count + b]
        }
        for (a in 0 until count) {
            val teamA = teamOf[playerIds[a]]
            if (teamA == null || teamA == 0) continue
            for (b in 0 until count) if (a != b && teamOf[playerIds[b]] == teamA) base[a * count + b] = meetLimit
        }

        val found = CompletableDeferred<BySwapsResult>()
        // Each worker counts its own swaps, so no two threads write one number.
        val swaps = LongArray(workers)
        val jobs = List(workers) { worker ->
            launch(Dispatchers.Default) {
                val random = Random(seed + worker * 7_919L)
                while (isActive && !found.isCompleted) {
                    val search = SwapSearch(count, roundIds.size, base, meetLimit, random)
                    val rounds = search.run { steps ->
                        swaps[worker] += steps
                        // A pause every few thousand swaps, so a time limit or a cancel stops it also on one thread (web).
                        ensureActive()
                        yield()
                        !found.isCompleted
                    }
                    if (rounds != null) {
                        found.complete(BySwapsResult(toTables(rounds, roundIds, playerIds, random), swaps.sum()))
                        break
                    }
                }
            }
        }
        val progress = launch {
            while (!found.isCompleted) {
                onProgress(TournamentScheduleCalcProgress(runningTries = workers, tried = swaps.sum(), maxConcurrency = workers, backup = true))
                delay(100)
            }
        }
        val result = found.await()
        jobs.forEach { it.cancel() }
        progress.cancel()
        onProgress(TournamentScheduleCalcProgress(runningTries = 0, tried = result.swaps, maxConcurrency = workers, backup = true))
        result
    }
}

data class BySwapsResult(
    val tables: List<TournamentTable>,
    /** Swaps made by every worker until one found the draw. */
    val swaps: Long,
)

/** Turns the tables of positions into tables of slots, with shuffled seats and table numbers 1..T in each round. */
private fun toTables(rounds: List<List<IntArray>>, roundIds: List<Int>, playerIds: List<Int>, random: Random): List<TournamentTable> =
    rounds.flatMapIndexed { roundIndex, tables ->
        tables.shuffled(random).mapIndexed { tableIndex, seats ->
            TournamentTable(
                roundId = roundIds[roundIndex],
                tableId = tableIndex + 1,
                playerIds = seats.map { playerIds[it] }.shuffled(random),
                isCompleted = false,
                useTotalsOnly = false,
                hasProgress = false,
            )
        }
    }

/**
 * One search by swaps over positions 0 until [count]. meets[a * count + b] counts how often a and b share a table, plus
 * [base]: a pair above [limit] is a repeat (with the limit 1, a pair that meets twice), and the search ends when there
 * is none.
 */
private class SwapSearch(
    private val count: Int,
    private val numRounds: Int,
    private val base: IntArray,
    private val limit: Int,
    private val random: Random,
) {
    private val tablesPerRound = count / 4
    /** seat[(r * tables + t) * 4 + s]: the position at seat s of table t in round r. */
    private val seat = IntArray(numRounds * tablesPerRound * 4)
    /** tableOf[r * count + p]: the table of position p in round r. */
    private val tableOf = IntArray(numRounds * count)
    private val meets = base.copyOf()
    /** The step until which position p may not go back to a table of round r: tabu[r * count + p] and its table. */
    private val tabuUntil = LongArray(numRounds * count)
    private val tabuTable = IntArray(numRounds * count) { -1 }
    private var repeats = 0

    /**
     * Swaps until there is no repeat, or gives up (null) after too long without progress, so the caller starts afresh.
     * [onPause] gets the swaps since the last call and says whether to go on.
     */
    suspend fun run(onPause: suspend (Long) -> Boolean): List<List<IntArray>>? {
        deal()
        var step = 0L
        var best = repeats
        var sinceBest = 0L
        var sincePause = 0L
        val stuckLimit = 2_000L + 40L * count
        while (repeats > 0) {
            if (++sincePause >= PAUSE_EVERY) {
                if (!onPause(sincePause)) return null
                sincePause = 0
            }
            step++
            move(step)
            if (repeats < best) {
                best = repeats
                sinceBest = 0
            } else if (++sinceBest > stuckLimit) {
                // A long time without progress: a few random swaps shake the draw.
                repeat(1 + count / 16) { randomSwap() }
                best = repeats
                sinceBest = 0
                if (step > MAX_STEPS_PER_SEARCH) return null
            }
        }
        onPause(sincePause)
        return List(numRounds) { r -> List(tablesPerRound) { t -> IntArray(4) { s -> seat[(r * tablesPerRound + t) * 4 + s] } } }
    }

    /** Every round dealt at random. */
    private fun deal() {
        for (r in 0 until numRounds) {
            val order = (0 until count).shuffled(random)
            for (t in 0 until tablesPerRound) for (s in 0 until 4) {
                val p = order[t * 4 + s]
                seat[(r * tablesPerRound + t) * 4 + s] = p
                tableOf[r * count + p] = t
            }
            for (t in 0 until tablesPerRound) addTable(r, t, 1)
        }
    }

    private fun addTable(r: Int, t: Int, sign: Int) {
        val at = (r * tablesPerRound + t) * 4
        for (i in 0 until 4) for (j in i + 1 until 4) addPair(seat[at + i], seat[at + j], sign)
    }

    private fun addPair(a: Int, b: Int, sign: Int) {
        val before = meets[a * count + b]
        val after = before + sign
        meets[a * count + b] = after
        meets[b * count + a] = after
        repeats += maxOf(0, after - limit) - maxOf(0, before - limit)
    }

    /** The change of repeats if [p] left its table, whose seats start at [at]. */
    private fun leaveGain(p: Int, at: Int): Int {
        var d = 0
        for (s in 0 until 4) {
            val o = seat[at + s]
            if (o == p) continue
            if (meets[p * count + o] > limit) d--
        }
        return d
    }

    /** The change of repeats if [p] joined the table at [at] in place of [skip]. */
    private fun joinCost(p: Int, at: Int, skip: Int): Int {
        var d = 0
        for (s in 0 until 4) {
            val o = seat[at + s]
            if (o == skip) continue
            if (meets[p * count + o] >= limit) d++
        }
        return d
    }

    /**
     * The best swap of a player with a repeat: for each of them (up to [CANDIDATES_PER_STEP]), every player of the other
     * tables of that round is tried, and the swap that removes the most repeats is made (ties at random).
     */
    private fun move(step: Long) {
        var bestDelta = Int.MAX_VALUE
        var bestRound = -1
        var bestX = -1
        var bestY = -1
        var ties = 0
        val picked = pickConflicted()
        for (k in 0 until picked) {
            val r = pickedRound[k]
            val x = pickedPlayer[k]
            val tx = tableOf[r * count + x]
            val atX = (r * tablesPerRound + tx) * 4
            val leaveX = leaveGain(x, atX)
            for (t in 0 until tablesPerRound) {
                if (t == tx) continue
                val atY = (r * tablesPerRound + t) * 4
                val xTabu = tabuTable[r * count + x] == t && tabuUntil[r * count + x] > step
                for (s in 0 until 4) {
                    val y = seat[atY + s]
                    // x and y are at different tables, so the pair of x and y does not change.
                    val delta = leaveX + joinCost(x, atY, y) + leaveGain(y, atY) + joinCost(y, atX, x)
                    val tabu = xTabu || (tabuTable[r * count + y] == tx && tabuUntil[r * count + y] > step)
                    // A tabu swap is taken only when it leaves no repeat at all.
                    if (tabu && repeats + delta > 0) continue
                    if (delta < bestDelta) {
                        bestDelta = delta
                        bestRound = r
                        bestX = x
                        bestY = y
                        ties = 1
                    } else if (delta == bestDelta && random.nextInt(++ties) == 0) {
                        bestRound = r
                        bestX = x
                        bestY = y
                    }
                }
            }
        }
        if (bestY < 0) return
        val r = bestRound
        val tx = tableOf[r * count + bestX]
        val ty = tableOf[r * count + bestY]
        swap(r, bestX, tx, bestY, ty)
        // Neither goes back to the table it left for a few steps.
        tabuTable[r * count + bestX] = tx
        tabuUntil[r * count + bestX] = step + TABU_BASE + random.nextInt(TABU_SPREAD)
        tabuTable[r * count + bestY] = ty
        tabuUntil[r * count + bestY] = step + TABU_BASE + random.nextInt(TABU_SPREAD)
    }

    private val pickedRound = IntArray(CANDIDATES_PER_STEP)
    private val pickedPlayer = IntArray(CANDIDATES_PER_STEP)

    /**
     * Every player who sits with someone they may not meet, in every round; or, when there are more of them than
     * [CANDIDATES_PER_STEP], that many chosen at random among them. Returns how many are in pickedRound/pickedPlayer.
     */
    private fun pickConflicted(): Int {
        var seen = 0
        for (r in 0 until numRounds) {
            for (t in 0 until tablesPerRound) {
                val at = (r * tablesPerRound + t) * 4
                for (i in 0 until 4) {
                    val p = seat[at + i]
                    var conflicted = false
                    for (j in 0 until 4) {
                        val o = seat[at + j]
                        if (o != p && meets[p * count + o] > limit) {
                            conflicted = true
                            break
                        }
                    }
                    if (!conflicted) continue
                    // Reservoir sampling: each one has the same chance to be weighed.
                    val slot = if (seen < CANDIDATES_PER_STEP) seen else random.nextInt(seen + 1)
                    seen++
                    if (slot < CANDIDATES_PER_STEP) {
                        pickedRound[slot] = r
                        pickedPlayer[slot] = p
                    }
                }
            }
        }
        return minOf(seen, CANDIDATES_PER_STEP)
    }

    private fun randomSwap() {
        val r = random.nextInt(numRounds)
        val x = random.nextInt(count)
        val y = random.nextInt(count)
        val tx = tableOf[r * count + x]
        val ty = tableOf[r * count + y]
        if (tx != ty) swap(r, x, tx, y, ty)
    }

    private fun swap(r: Int, x: Int, tx: Int, y: Int, ty: Int) {
        addTable(r, tx, -1)
        addTable(r, ty, -1)
        val atX = (r * tablesPerRound + tx) * 4
        val atY = (r * tablesPerRound + ty) * 4
        for (s in 0 until 4) {
            if (seat[atX + s] == x) seat[atX + s] = y
            if (seat[atY + s] == y) seat[atY + s] = x
        }
        tableOf[r * count + x] = ty
        tableOf[r * count + y] = tx
        addTable(r, tx, 1)
        addTable(r, ty, 1)
    }

    private companion object {
        /** Players with a repeat whose swaps are weighed before each swap. */
        const val CANDIDATES_PER_STEP = 64
        /** Swaps between two pauses of the search. */
        const val PAUSE_EVERY = 500L
        /** Steps a swap is not undone, plus a random part. */
        const val TABU_BASE = 8L
        const val TABU_SPREAD = 8
        /** Swaps before one search gives up and a new one deals again. */
        const val MAX_STEPS_PER_SEARCH = 2_000_000L
    }
}
