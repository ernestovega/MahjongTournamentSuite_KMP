package com.etologic.mahjongtournamentsuite.domain.model

/** The values of a hand that a player writes down. A null loser means a self-drawn hand. A null winner means a draw. */
data class HandProposal(
    val winnerSlotId: Int?,
    val loserSlotId: Int?,
    val score: Int,
    val isChickenHand: Boolean = false,
    /** The table ends with this hand, for example when the time is over. The server marks the table completed. */
    val isLastHand: Boolean = false,
)

/**
 * One penalty of a hand. [amount] is a positive number. The penalized player loses all of it.
 * When [shared] is true, each of the other three players receives a third of it.
 * [seatIndex] is the seat of the penalized player in the order east, south, west, north.
 */
data class PenaltyEntry(val seatIndex: Int, val amount: Int, val shared: Boolean) {
    /** A shared penalty needs an amount that splits into three whole numbers. */
    val isValid: Boolean get() = seatIndex in 0..3 && amount > 0 && (!shared || amount % 3 == 0)

    /** The score change of the four seats from this penalty. */
    fun seatValues(): List<Int> = List(4) { seat ->
        when {
            seat == seatIndex -> -amount
            shared -> amount / 3
            else -> 0
        }
    }
}

/** What the table looks like after a hand. The player shows it to the table before saving. */
data class HandPreview(
    val handId: Int,
    /** Slot ids by seat in the order east, south, west, north. */
    val seatSlotIds: List<Int>,
    /** Score change of every seat from this hand. */
    val handScores: List<Int>,
    /** Table scores after this hand. */
    val tableScores: List<String>,
    /** Table points after this hand. */
    val tablePoints: List<String>,
    /** The table version of the preview. Saving the hand needs it. */
    val tableVersion: Long,
)
