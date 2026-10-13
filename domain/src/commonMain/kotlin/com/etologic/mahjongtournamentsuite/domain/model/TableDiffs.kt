package com.etologic.mahjongtournamentsuite.domain.model

import kotlin.math.abs
import kotlin.math.ceil

/** The hand score that a player needs to win a hand and so reach a rank. */
data class PointsDiff(
    /** Needed hand score when the player wins by self-pick. */
    val bySelfPick: Int,
    /** Needed hand score when the player wins on a discard of the player to overtake. */
    val byDirectHu: Int,
    /** Needed hand score when the player wins on a discard of another player. */
    val byIndirectHu: Int,
)

/**
 * What one seat needs to reach the first, second and third place of the table.
 * A null value means that nothing is needed for that place.
 */
data class SeatDiffs(
    val seatIndex: Int,
    val points: Int,
    val pointsToBeFirst: PointsDiff? = null,
    val pointsToBeSecond: PointsDiff? = null,
    val pointsToBeThird: PointsDiff? = null,
)

private const val MIN_HAND_SCORE = 8

private val drawPointsDiff = PointsDiff(MIN_HAND_SCORE, MIN_HAND_SCORE, MIN_HAND_SCORE)

fun Int.neededHandScoreBySelfPick(): Int = when {
    this < 64 -> MIN_HAND_SCORE
    this % 4 > 0 -> (ceil(this.toFloat() / 4) - MIN_HAND_SCORE).toInt()
    else -> (this / 4) - MIN_HAND_SCORE + 1
}

fun Int.neededHandScoreByDirectHu(): Int = when {
    this < 48 -> MIN_HAND_SCORE
    this % 2 > 0 -> ceil((this - 32).toFloat() / 2).toInt()
    else -> ((this - 32) / 2) + 1
}

fun Int.neededHandScoreByIndirectHu(): Int = when {
    this < 40 -> MIN_HAND_SCORE
    else -> this - 32 + 1
}

private fun pointsDiffOf(gap: Int) = PointsDiff(
    bySelfPick = gap.neededHandScoreBySelfPick(),
    byDirectHu = gap.neededHandScoreByDirectHu(),
    byIndirectHu = gap.neededHandScoreByIndirectHu(),
)

/**
 * Computes the differences of a table. [points] has the table score of each seat.
 * The result has one item per seat, in the order of [points].
 * The rules are the ones of the MahjongScoring app.
 */
fun calculateTableDiffs(points: List<Int>): List<SeatDiffs> {
    require(points.size == 4) { "A table has four seats." }
    val ranked = points.mapIndexed { index, value -> SeatDiffs(index, value) }
        .sortedByDescending { it.points }
        .toMutableList()
    val p = ranked.map { it.points }
    val d21 = abs(p[0] - p[1])
    val d31 = abs(p[0] - p[2])
    val d32 = abs(p[1] - p[2])
    val d41 = abs(p[0] - p[3])
    val d42 = abs(p[1] - p[3])
    val d43 = abs(p[2] - p[3])

    // Index 0..3 are the places 1st..4th. Each entry holds [toBeFirst, toBeSecond, toBeThird].
    val needs = Array(4) { arrayOfNulls<PointsDiff>(3) }
    when {
        d43 == 0 && d42 == 0 && d41 == 0 -> for (i in 0..3) needs[i][0] = drawPointsDiff
        d32 == 0 && d31 == 0 -> {
            for (i in 0..2) needs[i][0] = drawPointsDiff
            needs[3][0] = pointsDiffOf(d41)
        }
        d43 == 0 && d42 == 0 -> {
            needs[1][0] = pointsDiffOf(d21); needs[1][1] = drawPointsDiff
            needs[2][0] = pointsDiffOf(d31); needs[2][1] = drawPointsDiff
            needs[3][0] = pointsDiffOf(d41); needs[3][1] = drawPointsDiff
        }
        d21 == 0 && d43 == 0 -> {
            needs[0][0] = drawPointsDiff
            needs[1][0] = drawPointsDiff
            needs[2][0] = pointsDiffOf(d31); needs[2][2] = drawPointsDiff
            needs[3][0] = pointsDiffOf(d41); needs[3][2] = drawPointsDiff
        }
        d21 == 0 -> {
            needs[0][0] = drawPointsDiff
            needs[1][0] = drawPointsDiff
            needs[2][0] = pointsDiffOf(d31)
            needs[3][0] = pointsDiffOf(d41); needs[3][2] = pointsDiffOf(d43)
        }
        d32 == 0 -> {
            needs[1][0] = pointsDiffOf(d21); needs[1][1] = drawPointsDiff
            needs[2][0] = pointsDiffOf(d31); needs[2][1] = drawPointsDiff
            needs[3][0] = pointsDiffOf(d41); needs[3][1] = pointsDiffOf(d42)
        }
        d43 == 0 -> {
            needs[1][0] = pointsDiffOf(d21)
            needs[2][0] = pointsDiffOf(d31); needs[2][1] = pointsDiffOf(d32); needs[2][2] = drawPointsDiff
            needs[3][0] = pointsDiffOf(d41); needs[3][1] = pointsDiffOf(d42); needs[3][2] = drawPointsDiff
        }
        else -> {
            needs[1][0] = pointsDiffOf(d21)
            needs[2][0] = pointsDiffOf(d31); needs[2][1] = pointsDiffOf(d32)
            needs[3][0] = pointsDiffOf(d41); needs[3][1] = pointsDiffOf(d42); needs[3][2] = pointsDiffOf(d43)
        }
    }

    // Remove the places that need the same as the place above.
    if (needs[1][1] != null && needs[1][1] == needs[1][0]) needs[1][1] = null
    if (needs[2][2] != null && needs[2][2] == needs[2][1]) needs[2][2] = null
    if (needs[2][1] != null && needs[2][1] == needs[2][0]) needs[2][1] = null
    if (needs[3][2] != null && needs[3][2] == needs[3][1]) needs[3][2] = null
    if (needs[3][1] != null && needs[3][1] == needs[3][0]) needs[3][1] = null

    return ranked.mapIndexed { place, seat ->
        seat.copy(pointsToBeFirst = needs[place][0], pointsToBeSecond = needs[place][1], pointsToBeThird = needs[place][2])
    }.sortedBy { it.seatIndex }
}
