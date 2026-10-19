package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry

/** Hand entry by a player who sits at the table. */
interface PlayerHandRepository {
    /**
     * Adds penalties to the hand in play. The hand does not advance. The penalties count in the table totals
     * when the hand is saved. [expectedVersion] is the table version that the app shows.
     */
    suspend fun addPenalties(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        penalties: List<PenaltyEntry>,
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
