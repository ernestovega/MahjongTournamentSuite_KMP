package com.etologic.mahjongtournamentsuite.domain.model

/**
 * How many rounds a tournament of tables of 4 can play without two players meeting twice (docs/draw.md).
 *
 * Each round a player meets 3 rivals, so with N players nobody can play more than (N - 1) / 3 rounds without meeting
 * someone again. With teams, a player never meets their 3 teammates, so (N - 4) / 3. That is a ceiling: whether it is
 * reached is the "social golfer problem", open for many sizes. For the small sizes, where it matters, the exact
 * maximum is known and kept in [KNOWN_INDIVIDUAL].
 *
 * The teams and the individual case are one problem: the teams are a round where everyone sits with their team, so
 * a team tournament fits exactly one round less than an individual one of the same size.
 */
object RepeatFreeRounds {
    /** The ceiling: (N - 1) / 3 rounds, or (N - 4) / 3 with teams. 0 for a size that is not a multiple of 4. */
    fun ceiling(players: Int, isTeams: Boolean): Int {
        if (players < 4 || players % 4 != 0) return 0
        return ((players - 1) / 3 - if (isTeams) 1 else 0).coerceAtLeast(0)
    }

    /**
     * The rounds without repeats that the app counts on: the known maximum for the small sizes, the ceiling for the
     * others. With teams, one round less.
     */
    fun max(players: Int, isTeams: Boolean): Int {
        if (players < 4 || players % 4 != 0) return 0
        val individual = KNOWN_INDIVIDUAL[players] ?: ceiling(players, isTeams = false)
        return (individual - if (isTeams) 1 else 0).coerceAtLeast(0)
    }

    /**
     * The rounds without repeats that the draw finds for sure, in a few seconds: all of [max] for the sizes with a
     * perfect pattern (16, 28, 40, 64, 256 players), and for the others what the draw by swaps found every time, measured
     * on 23-nov ([SURE_INDIVIDUAL], [SURE_TEAMS]; 4 threads, two draws of 20 s at most). Between and after the measured
     * sizes, 70 % of the ceiling, as measured from 32 players on. With more rounds than this, the draw tries without
     * repeats, and goes in blocks when it does not make it (docs/draw.md).
     */
    fun sure(players: Int, isTeams: Boolean): Int {
        if (players < 4 || players % 4 != 0) return 0
        if (players in PERFECT_KEPT) return max(players, isTeams)
        val measured = (if (isTeams) SURE_TEAMS else SURE_INDIVIDUAL)[players]
        val sure = measured ?: (ceiling(players, isTeams) * 7 / 10)
        return sure.coerceIn(minOf(1, max(players, isTeams)), max(players, isTeams))
    }

    /**
     * The most times that two players meet in [rounds] rounds, drawn in blocks of [sure] rounds: 1 while they fit, then
     * one more for each block begun.
     */
    fun timesAtMost(players: Int, rounds: Int, isTeams: Boolean): Int {
        val perBlock = sure(players, isTeams)
        if (perBlock < 1 || rounds < 1) return 0
        return (rounds + perBlock - 1) / perBlock
    }

    /**
     * True when the rounds without repeats can be a perfect pattern: every pair of players meets exactly once in
     * [max] rounds (and with teams, every pair of players of different teams). It exists only when N - 1 is a
     * multiple of 3 and N(N - 1) / 12 tables fit exactly: N = 16, 28, 40, 52, 64…
     */
    fun isPerfect(players: Int): Boolean = players % 12 == 4

    /**
     * The exact maximum of rounds without repeats of an individual tournament, where it is below the ceiling or
     * where the ceiling is reached only by a perfect pattern (the social golfer problem with groups of 4).
     * With 3 tables or fewer only 1 round fits: a table of 4 needs players of 4 different tables of the round before.
     */
    val KNOWN_INDIVIDUAL: Map<Int, Int> = mapOf(4 to 1, 8 to 1, 12 to 1, 16 to 5, 20 to 5)

    /** The perfect sizes whose pattern the app keeps (PerfectPattern). */
    private val PERFECT_KEPT = setOf(16, 28, 40, 64, 256)

    /**
     * Rounds without repeats that the draw by swaps found every time, measured on 23-nov. 24 players found 6 rounds, but
     * once in 12 s: 5 is the sure one.
     */
    private val SURE_INDIVIDUAL: Map<Int, Int> = mapOf(4 to 1, 8 to 1, 12 to 1, 20 to 5, 24 to 5, 32 to 7, 36 to 8, 44 to 10, 48 to 11)
    private val SURE_TEAMS: Map<Int, Int> = mapOf(4 to 0, 8 to 0, 12 to 0, 20 to 4, 24 to 4, 32 to 6, 36 to 7, 44 to 9, 48 to 10)
}
