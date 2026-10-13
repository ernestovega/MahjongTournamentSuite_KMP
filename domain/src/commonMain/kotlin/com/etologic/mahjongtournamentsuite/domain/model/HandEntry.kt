package com.etologic.mahjongtournamentsuite.domain.model

/** The values of a hand that a player writes down. A null loser means a self-drawn hand. */
data class HandProposal(
    val winnerSlotId: Int,
    val loserSlotId: Int?,
    val score: Int,
    val isChickenHand: Boolean = false,
)

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
