package com.etologic.mahjongtournamentsuite.domain.model

data class TournamentPlayer(
    val id: Int,
    val name: String,
    val team: Int,
    val country: String = "",
    /** EMA number of the shared player assigned to this tournament slot, if any. */
    val assignedEmaId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A real player that is independent of every tournament. */
data class Player(
    /** Unique EMA number. This is the shared-player primary key. */
    val emaId: String,
    val name: String,
    val country: String = "",
    val photoUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class Country(
    val code: String,
    val name: String,
)

data class TournamentRound(
    val roundId: Int,
)

data class TournamentTable(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val isCompleted: Boolean,
    val useTotalsOnly: Boolean,
    val usePointsCalculation: Boolean,
    val hasProgress: Boolean,
    val hasValidManualTotals: Boolean = false,
)
