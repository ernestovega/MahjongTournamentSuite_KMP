package com.etologic.mahjongtournamentsuite.domain.model

/**
 * The table points of a table. They come only from the scores of the table. The scores are the manual
 * scores when the table uses manual totals, or the totals of the hands. Nobody types or forces table points.
 */
object TablePoints {
    /**
     * The points of the four seats (East, South, West, North) for four scores.
     * Ties share the points of their places. The result is null while a score is missing.
     */
    fun fromScores(scores: List<Int?>): List<String>? {
        require(scores.size == 4) { "A table has four seats." }
        if (scores.any { it == null }) return null
        val values = scores.map { it!! }
        val ranked = values.withIndex().sortedByDescending { it.value }
        val (a, b, c, d) = ranked.map { it.value }
        val assigned = when {
            a == b && b == c && c == d -> listOf("1,75", "1,75", "1,75", "1,75")
            a == b && b == c -> listOf("2,33", "2,33", "2,33", "0")
            b == c && c == d -> listOf("4", "1", "1", "1")
            a == b && c == d -> listOf("3", "3", "0,5", "0,5")
            a == b -> listOf("3", "3", "1", "0")
            b == c -> listOf("4", "1,5", "1,5", "0")
            c == d -> listOf("4", "2", "0,5", "0,5")
            else -> listOf("4", "2", "1", "0")
        }
        val bySeat = Array(4) { "" }
        ranked.forEachIndexed { place, entry -> bySeat[entry.index] = assigned[place] }
        return bySeat.toList()
    }
}

/** The scores of the four seats as text: the manual scores for a table with manual totals, otherwise the saved totals. */
val TableState.seatScores: List<String>
    get() = if (useTotalsOnly) {
        listOf(
            manualPlayerEastScore.ifBlank { playerEastScore },
            manualPlayerSouthScore.ifBlank { playerSouthScore },
            manualPlayerWestScore.ifBlank { playerWestScore },
            manualPlayerNorthScore.ifBlank { playerNorthScore },
        )
    } else {
        listOf(playerEastScore, playerSouthScore, playerWestScore, playerNorthScore)
    }

/** The table points of the four seats, in the order East, South, West, North. A seat has "" while the scores are not valid. */
val TableState.seatPoints: List<String>
    get() = TablePoints.fromScores(seatScores.map { it.trim().toIntOrNull() }) ?: List(4) { "" }
