package com.etologic.mahjongtournamentsuite.domain.model

data class RankingTable(
    val table: TableState,
    val hands: List<TableHand>,
) {
    /** The table has something to rank: a hand that is done, a score typed by hand, or the table is completed. */
    val hasResult: Boolean
        get() = table.isCompleted || hands.any { it.isDone } || table.seatScores.any { (it.trim().toIntOrNull() ?: 0) != 0 }
}

/**
 * Some table has a result. Before that, for example before the first round starts, every ranking has every player
 * with 0 points, so the screens show that the ranking comes later instead.
 */
val List<RankingTable>.haveResults: Boolean get() = any { it.hasResult }

data class TournamentRankings(
    val players: List<PlayerRanking>,
    val teams: List<TeamRanking>,
    val chickenHands: List<ChickenHandRanking>,
    val bestHands: List<BestHandRanking>,
) {
    /**
     * Some player has points or score. Before the first hand every player has 0 of both, and a ranking would only list
     * the players in seat order, so the screens say that the ranking comes later instead.
     */
    val hasResults: Boolean get() = players.any { it.points != 0.0 || it.score != 0 }
}

data class PlayerRanking(
    val position: Int,
    val playerId: Int,
    val teamId: Int,
    val points: Double,
    val score: Int,
    /** Set when a substitute played for this player. The ranking has the result that counts for the player. */
    val substitution: PlayerSubstitution? = null,
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
