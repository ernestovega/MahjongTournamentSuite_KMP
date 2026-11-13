package com.etologic.mahjongtournamentsuite.domain.model

/**
 * One round of a player, or of a team when the results are those of all its players.
 * [round] has only the tables of that round. [cumulative] has every table up to that round, so its rates move less.
 */
data class PlayerRound(
    val roundId: Int,
    val round: PlayerStatistics,
    val cumulative: PlayerStatistics,
) {
    /** True when the round has no results yet. */
    val isEmpty: Boolean get() = round.results.isEmpty()
}

/** The results split by round, in the order of [rounds]. A round without results is there too, empty. */
fun PlayerStatistics.byRound(rounds: List<Int>): List<PlayerRound> = rounds.sorted().map { roundId ->
    PlayerRound(
        roundId = roundId,
        round = PlayerStatistics(playerId, results.filter { it.roundId == roundId }),
        cumulative = PlayerStatistics(playerId, results.filter { it.roundId <= roundId }.sortedWith(compareBy({ it.roundId }, { it.tableId }))),
    )
}

/** The best result of a round in one value, and the slot of the player who made it. */
data class RoundRecord(val value: Int, val playerId: Int)

/** The hands and the tables of one round of a tournament. */
data class TournamentRoundTotals(
    val roundId: Int,
    val tablesTotal: Int,
    val tablesCompleted: Int,
    val handsPlayed: Int,
    val drawnHands: Int,
    val selfDraws: Int,
    val penaltyCount: Int,
    /** The sum of the scores of the winning hands, to make the average with the hands won. */
    val winningScoreSum: Int,
    val highestHand: RoundRecord?,
    val highestTableScore: RoundRecord?,
    /** The completed tables with hands, the ones that the hands come from. */
    val tablesWithHands: Int = 0,
) {
    val handsWon: Int get() = handsPlayed - drawnHands
    val winsByDiscard: Int get() = handsWon - selfDraws
    val drawRate: Double? get() = if (handsPlayed > 0) drawnHands.toDouble() / handsPlayed else null
    val selfDrawRate: Double? get() = if (handsWon > 0) selfDraws.toDouble() / handsWon else null
    val averageHandScore: Double? get() = if (handsWon > 0) winningScoreSum.toDouble() / handsWon else null
    val averageHandsPerTable: Double? get() = if (tablesWithHands > 0) handsPlayed.toDouble() / tablesWithHands else null

    operator fun plus(other: TournamentRoundTotals): TournamentRoundTotals = TournamentRoundTotals(
        roundId = maxOf(roundId, other.roundId),
        tablesTotal = tablesTotal + other.tablesTotal,
        tablesCompleted = tablesCompleted + other.tablesCompleted,
        handsPlayed = handsPlayed + other.handsPlayed,
        drawnHands = drawnHands + other.drawnHands,
        selfDraws = selfDraws + other.selfDraws,
        penaltyCount = penaltyCount + other.penaltyCount,
        winningScoreSum = winningScoreSum + other.winningScoreSum,
        highestHand = listOfNotNull(highestHand, other.highestHand).maxByOrNull { it.value },
        highestTableScore = listOfNotNull(highestTableScore, other.highestTableScore).maxByOrNull { it.value },
        tablesWithHands = tablesWithHands + other.tablesWithHands,
    )
}

/** A round of the tournament, alone and added up with every round before it. */
data class TournamentRoundSlice(val round: TournamentRoundTotals, val cumulative: TournamentRoundTotals)

/**
 * The tournament split by round, in the order of [rounds]. Only the completed tables with hands count in the hands,
 * like in the totals of the tournament. A round without tables is there too, empty.
 */
fun TournamentStatistics.byRound(rounds: List<Int>): List<TournamentRoundSlice> {
    val single = rounds.sorted().map { roundId ->
        val roundTables = tables.filter { it.roundId == roundId }
        val counted = roundTables.filter { it.isCompleted && it.hasHands }
        val results = counted.flatMap { table -> table.players.flatMap { player -> player.results.map { player.playerId to it } } }
        TournamentRoundTotals(
            roundId = roundId,
            tablesTotal = roundTables.size,
            tablesCompleted = roundTables.count { it.isCompleted },
            handsPlayed = counted.sumOf { it.handsPlayed },
            drawnHands = counted.sumOf { it.drawnHands },
            selfDraws = counted.sumOf { it.selfDraws },
            penaltyCount = counted.sumOf { it.penaltyCount },
            winningScoreSum = results.sumOf { it.second.winningScoreSum },
            highestHand = results.filter { it.second.bestHand > 0 }.maxByOrNull { it.second.bestHand }
                ?.let { RoundRecord(it.second.bestHand, it.first) },
            highestTableScore = roundTables.filter { it.isCompleted }
                .flatMap { table -> table.players.flatMap { player -> player.results.map { player.playerId to it } } }
                .maxByOrNull { it.second.score }
                ?.let { RoundRecord(it.second.score, it.first) },
            tablesWithHands = counted.size,
        )
    }
    return single.runningReduce { total, next -> total + next }.zip(single) { cumulative, round -> TournamentRoundSlice(round, cumulative) }
}
