package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RepeatFreeRounds

/**
 * The perfect pattern of rounds without repeats (docs/draw.md): every pair of the N players meets exactly once, in
 * (N - 1) / 3 rounds, without search and without chance (the chance comes later, in who takes
 * each place of the pattern).
 *
 * - For N = 16, 64, 256 it is the geometry of the 4 × 4 grid (and its 3D and 4D versions) over the field of 4 numbers:
 *   the players are the points, the tables are the lines, and each round is a direction, all the lines that go that
 *   way. Two points are on exactly one line, so two players meet exactly once.
 * - For N = 28 and 40 it is kept as found, once, from two classic figures: the 28 points of the Hermitian curve over
 *   the field of 9 numbers, and the 40 points of the 3D projective space over the field of 3 numbers. In both, the
 *   lines meet the points in groups of 4, two points share exactly one line, and the lines split into rounds that seat
 *   everyone once. The split was found by an exact search (scratch script, 23-nov) and is checked by the tests.
 * - The other perfect sizes (52, 76…) have a pattern too, but none is kept: tournaments that size play fewer rounds
 *   than fit without repeats, and the draw searches them.
 */
object PerfectPattern {
    /**
     * The rounds of the pattern over the positions 0 until [players]: each round, its tables of 4 positions.
     * Null when there is no pattern for that size.
     */
    fun of(players: Int): List<List<IntArray>>? {
        if (!RepeatFreeRounds.isPerfect(players)) return null
        when (players) {
            28 -> return parse(FOUND_28)
            40 -> return parse(FOUND_40)
        }
        var dimension = 0
        var size = 1
        while (size < players) {
            size *= 4
            dimension++
        }
        if (size != players || dimension < 2) return null
        return directions(dimension).map { direction -> parallelLines(players, direction) }
    }

    /**
     * The directions of the space of [dimension] coordinates: one nonzero vector for each, the one whose highest
     * nonzero coordinate is 1. There are (4^n - 1) / 3.
     */
    private fun directions(dimension: Int): List<Int> {
        val points = 1 shl (2 * dimension)
        return (1 until points).filter { vector ->
            var highest = 0
            for (coordinate in 0 until dimension) {
                val value = (vector shr (2 * coordinate)) and 3
                if (value != 0) highest = value
            }
            highest == 1
        }
    }

    /** The lines of one direction: each point p with p + t·d for the 4 numbers t, every point on exactly one line. */
    private fun parallelLines(points: Int, direction: Int): List<IntArray> {
        val taken = BooleanArray(points)
        val lines = ArrayList<IntArray>(points / 4)
        for (start in 0 until points) {
            if (taken[start]) continue
            val line = IntArray(4) { t -> start xor scale(t, direction) }
            line.forEach { taken[it] = true }
            lines += line
        }
        return lines
    }

    /** t·v, coordinate by coordinate, in the field of 4 numbers (0, 1, a, a + 1, with a·a = a + 1). Sum is xor. */
    private fun scale(t: Int, vector: Int): Int {
        var result = 0
        var rest = vector
        var shift = 0
        while (rest != 0) {
            result = result or (TIMES[t * 4 + (rest and 3)] shl shift)
            rest = rest shr 2
            shift += 2
        }
        return result
    }

    private fun parse(rounds: List<String>): List<List<IntArray>> = rounds.map { round -> round.split(',').map { table -> table.split(' ').map { it.toInt() }.toIntArray() } }

    private val FOUND_28 = listOf(
        "1 5 11 12,2 7 18 22,0 14 20 25,4 8 24 26,9 13 17 23,6 15 16 21,3 10 19 27",
        "2 14 19 26,5 9 18 20,3 4 11 13,8 12 16 22,0 6 17 24,7 15 23 27,1 10 21 25",
        "2 11 21 24,5 10 23 26,6 12 25 27,0 4 9 15,16 17 18 19,3 7 8 14,1 13 20 22",
        "0 1 2 3,7 9 25 26,10 15 17 22,4 12 18 21,11 14 16 23,6 8 19 20,5 13 24 27",
        "2 9 16 27,1 4 19 23,8 13 18 25,5 14 17 21,3 15 20 24,0 7 10 12,6 11 22 26",
        "2 4 17 25,0 5 19 22,20 21 26 27,12 13 14 15,8 9 10 11,1 7 16 24,3 6 18 23",
        "2 6 10 13,4 14 22 27,3 5 16 25,7 11 17 20,0 8 21 23,9 12 19 24,1 15 18 26",
        "2 5 8 15,4 10 16 20,3 12 17 26,22 23 24 25,1 6 9 14,7 13 19 21,0 11 18 27",
        "2 12 20 23,4 5 6 7,1 8 17 27,11 15 19 25,10 14 18 24,3 9 21 22,0 13 16 26",
    )

    private val FOUND_40 = listOf(
        "1 6 9 12,10 21 27 33,5 14 24 31,0 28 29 30,4 17 26 35,11 16 23 39,3 13 18 20,2 32 36 37,7 19 22 34,8 15 25 38",
        "1 4 7 10,11 14 30 34,12 18 23 37,5 21 28 38,2 31 35 39,8 16 29 33,3 15 17 19,6 13 24 32,9 20 22 36,0 25 26 27",
        "10 16 22 37,6 20 28 39,1 15 18 21,4 14 23 32,3 31 36 38,2 24 25 29,12 13 30 35,11 19 26 33,5 17 27 34,0 7 8 9",
        "10 13 28 34,6 15 23 31,0 16 17 18,1 33 36 39,11 21 25 32,9 19 24 35,3 22 27 29,7 14 26 38,2 4 8 12,5 20 30 37",
        "10 20 26 32,1 5 8 11,9 18 29 31,0 13 14 15,12 17 22 39,7 21 24 36,3 33 35 37,4 16 25 34,6 19 30 38,2 23 27 28",
        "10 14 29 35,0 37 38 39,6 18 26 34,2 13 17 21,4 15 24 33,9 16 30 32,8 19 23 36,11 20 27 31,3 5 7 12,1 22 25 28",
        "10 15 30 36,3 32 34 39,9 13 27 38,7 16 28 31,1 23 26 29,12 20 25 33,8 21 22 35,2 14 18 19,11 17 24 37,0 4 5 6",
        "2 6 7 11,12 15 29 34,8 14 27 37,10 18 24 39,9 17 28 33,3 23 25 30,0 19 20 21,4 13 22 31,5 16 26 36,1 32 35 38",
        "6 21 29 37,2 33 34 38,0 10 11 12,1 13 16 19,8 17 30 31,3 24 26 28,9 14 25 39,4 18 27 36,7 20 23 35,5 15 22 32",
        "11 13 29 36,7 15 27 39,4 19 28 37,5 18 25 35,9 21 23 34,12 16 24 38,1 14 17 20,2 22 26 30,0 31 32 33,3 6 8 10",
        "5 13 23 33,11 18 22 38,4 21 30 39,9 15 26 37,0 1 2 3,8 20 24 34,10 19 25 31,6 16 27 35,7 17 29 32,12 14 28 36",
        "2 15 16 20,12 21 26 31,0 34 35 36,7 13 25 37,1 24 27 30,10 17 23 38,6 14 22 33,3 4 9 11,5 19 29 39,8 18 28 32",
        "1 31 34 37,12 19 27 32,0 22 23 24,2 5 9 10,11 15 28 35,6 17 25 36,8 13 26 39,7 18 30 33,4 20 29 38,3 14 16 21",
    )

    private val TIMES = intArrayOf(
        0, 0, 0, 0,
        0, 1, 2, 3,
        0, 2, 3, 1,
        0, 3, 1, 2,
    )
}
