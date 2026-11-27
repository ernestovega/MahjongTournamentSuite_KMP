package com.etologic.mahjongtournamentsuite.domain.model

data class TableState(
    val version: Long = 0,
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val playerEastId: String,
    val playerSouthId: String,
    val playerWestId: String,
    val playerNorthId: String,
    val playerEastScore: String,
    val playerSouthScore: String,
    val playerWestScore: String,
    val playerNorthScore: String,
    val manualPlayerEastScore: String = "",
    val manualPlayerSouthScore: String = "",
    val manualPlayerWestScore: String = "",
    val manualPlayerNorthScore: String = "",
    val isCompleted: Boolean,
    val useTotalsOnly: Boolean,
    /** Slot id of a substitute who left during this session. That seat gets 0 table points. Empty for most tables. */
    val forcedZeroPlayerId: String = "",
    /**
     * True when the server left out the results of this table: the tournament hides them until the prize giving.
     * The rankings and the statistics leave such a table out. Only the public reads give it.
     */
    val resultsHidden: Boolean = false,
)

data class TableHand(
    val handId: Int,
    val playerWinnerId: String,
    val playerLooserId: String,
    val handScore: String,
    val isChickenHand: Boolean,
    val isDone: Boolean,
    val playerEastPenalty: String,
    val playerSouthPenalty: String,
    val playerWestPenalty: String,
    val playerNorthPenalty: String,
    /** When the server marked the hand done, in milliseconds since 1970. Null for a hand saved before the server kept it. */
    val doneAtMs: Long? = null,
)

/** The shortest time between two hands that counts as play. Shorter gaps come from hands entered one after the other. */
const val MIN_HAND_TIME_MS = 60_000L

/** The longest time between two hands that counts as play. Longer gaps are breaks, or hands entered much later. */
const val MAX_HAND_TIME_MS = 30 * 60_000L

/**
 * The time that each hand of a table took: the time between a done hand and the done hand before it.
 * Only two hands in a row with a time count, and only a gap between [MIN_HAND_TIME_MS] and [MAX_HAND_TIME_MS],
 * so hands entered at the end of the table, or after a break, leave no time.
 */
fun handTimesMs(hands: List<TableHand>): List<Long> {
    val stamped = hands.filter { it.isDone && it.doneAtMs != null }.sortedBy { it.handId }
    return stamped.zipWithNext()
        .filter { (previous, next) -> next.handId == previous.handId + 1 }
        .map { (previous, next) -> next.doneAtMs!! - previous.doneAtMs!! }
        .filter { it in MIN_HAND_TIME_MS..MAX_HAND_TIME_MS }
}

private val WHOLE_NUMBER = Regex("^-?\\d+$")

/**
 * A score or penalty read the way the server reads it: digits with an optional minus sign, spaces around ignored.
 * "+8" and "8.5" are not whole numbers. The shared scoring cases (testdata/scoring-cases.json) check this.
 */
fun String.toWholeNumberOrNull(): Int? = trim().takeIf { WHOLE_NUMBER.matches(it) }?.toIntOrNull()
