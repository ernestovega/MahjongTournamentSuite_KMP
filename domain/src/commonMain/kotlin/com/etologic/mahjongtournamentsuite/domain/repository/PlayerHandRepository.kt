package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal

/** Hand entry by a player who sits at the table. */
interface PlayerHandRepository {
    /**
     * A player of the table assigns the four seats. [seatSlotIds] is in the order east, south, west, north.
     * It works until the first hand is saved. [expectedVersion] is the table version that the app shows.
     */
    suspend fun assignSeats(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        seatSlotIds: List<Int>,
        expectedVersion: Long,
    ): AppResult<Unit>

    /** Calculates the hand and the table totals. It writes nothing. */
    suspend fun previewHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
    ): AppResult<HandPreview>

    /**
     * Saves the hand. [expectedVersion] is the version of the preview. The server refuses the save when
     * the table changed after the preview.
     */
    suspend fun recordHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
        expectedVersion: Long,
    ): AppResult<HandPreview>
}
