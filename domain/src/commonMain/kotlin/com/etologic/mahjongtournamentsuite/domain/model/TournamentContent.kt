package com.etologic.mahjongtournamentsuite.domain.model

data class TournamentPlayer(
    val id: Int,
    val name: String,
    val team: Int,
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
)
