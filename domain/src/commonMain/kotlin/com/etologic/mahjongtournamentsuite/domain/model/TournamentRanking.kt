package com.etologic.mahjongtournamentsuite.domain.model

data class RankingTable(
    val table: TableState,
    val hands: List<TableHand>,
)

data class TournamentRankings(
    val players: List<PlayerRanking>,
    val teams: List<TeamRanking>,
    val chickenHands: List<ChickenHandRanking>,
    val bestHands: List<BestHandRanking>,
)

data class PlayerRanking(
    val position: Int,
    val playerId: Int,
    val teamId: Int,
    val points: Double,
    val score: Int,
)

data class TeamRanking(
    val position: Int,
    val teamId: Int,
    val points: Double,
    val score: Int,
)

data class ChickenHandRanking(
    val position: Int,
    val playerId: Int,
    val chickenHands: Int,
    val points: Double,
    val score: Int,
)

data class BestHandRanking(
    val position: Int,
    val playerId: Int,
    val handScore: Int,
    val points: Double,
    val score: Int,
)
