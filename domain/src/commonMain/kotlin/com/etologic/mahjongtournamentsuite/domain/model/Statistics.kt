package com.etologic.mahjongtournamentsuite.domain.model

/** A rate or an average needs at least this many hands before a leaderboard shows it. */
const val MIN_HANDS_FOR_LEADERBOARD = 8

/** The result of one player at one completed table. The statistics of every scope come from these. */
data class TableResult(
    val tournamentId: String = "",
    val roundId: Int,
    val tableId: Int,
    /** Seat of the player: 0 east, 1 south, 2 west, 3 north. */
    val seat: Int,
    /** Place at the table, from 1 to 4. Players with the same score share the place. */
    val position: Int,
    val score: Int,
    val points: Double,
    /** False when the table has only totals. Then every hand value below is zero. */
    val hasHands: Boolean,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val selfDraws: Int = 0,
    val dealIns: Int = 0,
    val drawnHands: Int = 0,
    val winningScoreSum: Int = 0,
    val bestHand: Int = 0,
    val chickenHands: Int = 0,
    /** Sum of the hand scores of the hands that this player lost by discarding. */
    val dealInScoreSum: Int = 0,
    /** Hands that another player won by drawing the tile by themselves. */
    val lostToSelfDraws: Int = 0,
    /** Hands that another player won on a discard of a third player. This player did not discard it. */
    val otherWins: Int = 0,
    val penaltyCount: Int = 0,
    /** Sum of the penalty points that this player received, as a positive number. */
    val penaltyPoints: Int = 0,
    /** The biggest single penalty that this player received at this table, as a positive number. */
    val maxPenalty: Int = 0,
    /** What the three opponents did to this player at this table. */
    val rivals: List<RivalTally> = emptyList(),
    /** Runs of hands that this player won, one after the other. */
    val winRun: HandRun = HandRun.NONE,
    /** Runs of hands without a discard of the winning tile by this player. */
    val noDealInRun: HandRun = HandRun.NONE,
)

/**
 * The longest run of hands inside one table, with the run at the start and at the end of the table.
 * The start and end let a caller join runs of tables that follow each other.
 */
data class HandRun(val best: Int, val start: Int, val end: Int) {
    companion object {
        val NONE = HandRun(0, 0, 0)

        /** Builds the run values from one flag for each hand, in hand order. */
        fun of(flags: List<Boolean>): HandRun {
            var best = 0
            var current = 0
            flags.forEach { flag ->
                current = if (flag) current + 1 else 0
                if (current > best) best = current
            }
            return HandRun(best, flags.takeWhile { it }.size, flags.reversed().takeWhile { it }.size)
        }
    }
}

/**
 * What one opponent did to a player at one table.
 * [opponent] is the EMA number of the opponent. Without an EMA number it is the tournament id and the slot id.
 */
data class RivalTally(
    val opponent: String,
    /** 1 when the opponent finished above the player at this table. */
    val tablesBehind: Int = 0,
    /** Hands that the opponent won on a discard of the player. */
    val discardLosses: Int = 0,
    /** Hands that the opponent won by drawing the tile by themselves. */
    val selfDrawLosses: Int = 0,
    /** Hands that the opponent won on a discard of a third player, not of this player. */
    val othersDiscardWins: Int = 0,
)

/**
 * The weight of each rival variable. A higher weight makes the variable count more in the rival score.
 * Every variable counts only the tables that the player and the rival played together.
 */
object RivalWeights {
    const val TABLE_BEHIND = 10.0
    const val DISCARD_LOSS = 5.0
    const val SELF_DRAW_LOSS = 3.0

    const val OTHERS_DISCARD_WIN = 1.0

    /** A rival needs at least this many shared tables. */
    const val MIN_SHARED_TABLES = 2
}

/** An opponent with the sums of what the opponent did to a player over all shared tables. */
data class Rival(
    val opponent: String,
    val sharedTables: Int,
    val tablesBehind: Int,
    val discardLosses: Int,
    val selfDrawLosses: Int,
    val othersDiscardWins: Int,
) {
    /** The weighted score for each shared table. A higher score means a tougher rival. */
    val score: Double
        get() = (
            RivalWeights.TABLE_BEHIND * tablesBehind +
                RivalWeights.DISCARD_LOSS * discardLosses +
                RivalWeights.SELF_DRAW_LOSS * selfDrawLosses +
                RivalWeights.OTHERS_DISCARD_WIN * othersDiscardWins
            ) / sharedTables
}

/**
 * A player known only by sums, as the overview of all tournaments gives them.
 * The statistics hold one result with the sums, so the hand values and the rates are right.
 * The table count, the places and the streaks are not valid for such a player.
 */
fun PlayerStatistics.Companion.ofTotals(playerId: Int, totals: PlayerTotals): PlayerStatistics = PlayerStatistics(
    playerId,
    listOf(
        TableResult(
            roundId = 0, tableId = 0, seat = 0, position = 1,
            score = totals.score, points = totals.points,
            hasHands = totals.tablesWithHands > 0,
            handsPlayed = totals.handsPlayed, handsWon = totals.handsWon,
            selfDraws = totals.selfDraws, dealIns = totals.dealIns,
            penaltyCount = totals.penaltyCount,
        ),
    ),
)

/** The longest runs of a player. A null value means that the data has no hands. */
data class Streaks(
    val tableWins: Int,
    val handsWon: Int?,
    val handsWithoutDealIn: Int?,
)

/** The statistics of one player over a list of table results. Results are in play order. */
data class PlayerStatistics(
    val playerId: Int,
    val results: List<TableResult>,
) {
    private val handResults get() = results.filter { it.hasHands }

    val tablesPlayed: Int get() = results.size
    val tablesWithHands: Int get() = handResults.size
    val totalPoints: Double get() = results.sumOf { it.points }
    val totalScore: Int get() = results.sumOf { it.score }
    val averagePoints: Double? get() = results.takeIf { it.isNotEmpty() }?.let { totalPoints / it.size }
    val averageScore: Double? get() = results.takeIf { it.isNotEmpty() }?.let { totalScore.toDouble() / it.size }
    val bestTableScore: Int? get() = results.maxOfOrNull { it.score }
    val worstTableScore: Int? get() = results.minOfOrNull { it.score }
    val averagePosition: Double? get() = results.takeIf { it.isNotEmpty() }?.map { it.position }?.average()

    /** How many times the player finished in places 1, 2, 3 and 4. */
    val placeCounts: List<Int> get() = (1..4).map { place -> results.count { it.position == place } }

    val handsPlayed: Int get() = handResults.sumOf { it.handsPlayed }
    val handsWon: Int get() = handResults.sumOf { it.handsWon }
    val selfDraws: Int get() = handResults.sumOf { it.selfDraws }
    val dealIns: Int get() = handResults.sumOf { it.dealIns }
    val drawnHands: Int get() = handResults.sumOf { it.drawnHands }
    val lostToSelfDraws: Int get() = handResults.sumOf { it.lostToSelfDraws }
    val otherWins: Int get() = handResults.sumOf { it.otherWins }

    /** Tournaments with at least one completed table. Results without a tournament id count as one tournament. */
    val tournamentsPlayed: Int get() = results.map { it.tournamentId }.distinct().size

    /** Hands that another player won, whatever the way. */
    val handsLost: Int get() = dealIns + lostToSelfDraws + otherWins
    val winsByDiscard: Int get() = handsWon - selfDraws
    val chickenHands: Int get() = handResults.sumOf { it.chickenHands }
    val bestHand: Int? get() = handResults.maxOfOrNull { it.bestHand }?.takeIf { it > 0 }
    val penaltyCount: Int get() = handResults.sumOf { it.penaltyCount }
    val penaltyPoints: Int get() = handResults.sumOf { it.penaltyPoints }

    /** The most hands that the player won at one table. Null when no table has hands. */
    val mostHandsWonAtATable: Int? get() = handResults.maxOfOrNull { it.handsWon }

    /** The biggest single penalty that the player received. Null when the player received none. */
    val highestPenalty: Int? get() = handResults.maxOfOrNull { it.maxPenalty }?.takeIf { it > 0 }

    /** Wins by discard that are chicken hands. A chicken hand needs a loser, so all of them are wins by discard. */
    val chickenWins: Int get() = minOf(chickenHands, winsByDiscard)

    /** Share of the played hands that the player won. Null when the player has no hands. */
    val winRate: Double? get() = ratio(handsWon, handsPlayed)

    /** Share of the won hands that the player drew by themselves. */
    val selfDrawRate: Double? get() = ratio(selfDraws, handsWon)

    /** Share of the played hands that the player lost by discarding the winning tile. */
    val dealInRate: Double? get() = ratio(dealIns, handsPlayed)
    val averageWinningScore: Double? get() = ratio(handResults.sumOf { it.winningScoreSum }, handsWon)
    val averageDealInScore: Double? get() = ratio(handResults.sumOf { it.dealInScoreSum }, dealIns)

    /**
     * The skill score from 0 to 100. It is the mean of the four skill axes of the skills chart.
     * Null while the player has fewer than [MIN_HANDS_FOR_LEADERBOARD] hands.
     */
    val skillScore: Double? get() {
        if (handsPlayed < MIN_HANDS_FOR_LEADERBOARD) return null
        val axes = listOf(
            (winRate ?: 0.0) / SkillScale.WIN_RATE_FULL,
            (selfDrawRate ?: 0.0) / SkillScale.SELF_DRAW_RATE_FULL,
            1.0 - (dealInRate ?: 0.0) / SkillScale.DEAL_IN_RATE_FULL,
            ((averageWinningScore ?: SkillScale.HAND_VALUE_EMPTY) - SkillScale.HAND_VALUE_EMPTY) /
                (SkillScale.HAND_VALUE_FULL - SkillScale.HAND_VALUE_EMPTY),
        )
        return axes.map { it.coerceIn(0.0, 1.0) }.average() * 100.0
    }

    /** The level of the player from the [skillScore]. Null while the score is not available. */
    val level: PlayerLevel? get() = skillScore?.let { PlayerLevel.of(it) }

    /** True while the player has too few hands for a steady level. */
    val isLevelProvisional: Boolean get() = handsPlayed < STEADY_LEVEL_HANDS

    /** The opponents who did the most harm to the player, the toughest first. */
    fun toughestRivals(limit: Int = 3): List<Rival> =
        results.flatMap { result -> result.rivals.map { result to it } }
            .groupBy({ it.second.opponent }, { it.second })
            .map { (opponent, tallies) ->
                Rival(
                    opponent = opponent,
                    sharedTables = tallies.size,
                    tablesBehind = tallies.sumOf { it.tablesBehind },
                    discardLosses = tallies.sumOf { it.discardLosses },
                    selfDrawLosses = tallies.sumOf { it.selfDrawLosses },
                    othersDiscardWins = tallies.sumOf { it.othersDiscardWins },
                )
            }
            .filter { it.sharedTables >= RivalWeights.MIN_SHARED_TABLES && it.score > 0.0 }
            .sortedWith(compareByDescending<Rival> { it.score }.thenByDescending { it.sharedTables }.thenBy { it.opponent })
            .take(limit)

    /** The longest runs over all results in play order. Runs join across tables that follow each other. */
    val streaks: Streaks
        get() = Streaks(
            tableWins = longestRun(results.map { it.position == 1 }),
            handsWon = handResults.takeIf { it.isNotEmpty() }?.let { joinRuns(it.map { r -> r.winRun to r.handsPlayed }) },
            handsWithoutDealIn = handResults.takeIf { it.isNotEmpty() }?.let { joinRuns(it.map { r -> r.noDealInRun to r.handsPlayed }) },
        )

    private fun longestRun(flags: List<Boolean>): Int = HandRun.of(flags).best

    /** Joins the runs of tables in order. A table that is one full run continues the run of the table before it. */
    private fun joinRuns(tables: List<Pair<HandRun, Int>>): Int {
        var best = 0
        var open = 0
        tables.forEach { (run, hands) ->
            if (run.best == hands) {
                open += hands
            } else {
                best = maxOf(best, open + run.start, run.best)
                open = run.end
            }
        }
        return maxOf(best, open)
    }

    private fun ratio(part: Int, whole: Int): Double? = if (whole > 0) part.toDouble() / whole else null

    companion object
}

/** A level needs at least this many hands to stop being provisional. */
const val STEADY_LEVEL_HANDS = 100

/** The value of each skill axis where the axis is full. A skill at this value gives the full axis. */
object SkillScale {
    const val WIN_RATE_FULL = 0.40
    const val SELF_DRAW_RATE_FULL = 0.50
    const val DEAL_IN_RATE_FULL = 0.25

    /** The lowest hand value is 8 points, so the hand value axis starts there. */
    const val HAND_VALUE_EMPTY = 8.0
    const val HAND_VALUE_FULL = 32.0
}

/** The level of a player. [minScore] is the lowest skill score of the level. */
enum class PlayerLevel(val minScore: Int) {
    BEGINNER(0),
    INTERMEDIATE(30),
    ADVANCED(45),
    EXPERT(60),
    MASTER(75),
    ;

    companion object {
        fun of(score: Double): PlayerLevel = entries.last { score >= it.minScore }
    }
}

/** The running score of every player of one table after each counted hand. */
data class TableScoreSwing(
    val playerId: Int,
    val scoreAfterHand: List<Int>,
)

data class TableStatistics(
    val roundId: Int,
    val tableId: Int,
    val isCompleted: Boolean,
    /** False when the table has only totals. Then the hand values are empty. */
    val hasHands: Boolean,
    val players: List<PlayerStatistics>,
    val handsPlayed: Int,
    val drawnHands: Int,
    val selfDraws: Int,
    val dealIns: Int,
    val bestHand: Int?,
    val chickenHands: Int,
    val penaltyCount: Int,
    val swing: List<TableScoreSwing>,
)

data class TournamentStatistics(
    val tablesTotal: Int,
    val tablesCompleted: Int,
    /** Completed tables whose hands are counted in the hand statistics. */
    val tablesWithHands: Int,
    val players: List<PlayerStatistics>,
    val handsPlayed: Int,
    val drawnHands: Int,
    /** Winning hands that the winner drew by themselves. The wins by discard are the other winning hands. */
    val selfDraws: Int,
    val averageHandScore: Double?,
    val highestHand: Int?,
    val chickenHands: Int,
    val penaltyCount: Int,
    /** The penalty points of all counted hands, as a positive number. Null when the source has only the number of penalties. */
    val penaltyPoints: Int? = null,
    val tables: List<TableStatistics>,
) {
    val drawRate: Double? get() = if (handsPlayed > 0) drawnHands.toDouble() / handsPlayed else null

    private val eligible get() = players.filter { it.handsPlayed >= MIN_HANDS_FOR_LEADERBOARD }

    val winRateLeaders: List<PlayerStatistics> get() = eligible.sortedByDescending { it.winRate }
    val lowestDealInLeaders: List<PlayerStatistics> get() = eligible.sortedBy { it.dealInRate }
    val selfDrawLeaders: List<PlayerStatistics> get() = players.filter { it.selfDraws > 0 }.sortedByDescending { it.selfDraws }
}
