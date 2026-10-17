package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.BestHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.ChickenHandRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TeamRanking
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRankings

class CalculateTournamentRankingsUseCase {
    operator fun invoke(
        players: List<TournamentPlayer>,
        tables: List<RankingTable>,
        includeTeams: Boolean,
    ): TournamentRankings {
        val totals = players.associate { it.id to MutableTotal() }.toMutableMap()

        tables.forEach { rankingTable ->
            val table = rankingTable.table
            // The points of a table come only from its scores.
            val points = table.seatPoints
            val scores = table.seatScores
            add(totals, table.playerEastId, points[0], scores[0])
            add(totals, table.playerSouthId, points[1], scores[1])
            add(totals, table.playerWestId, points[2], scores[2])
            add(totals, table.playerNorthId, points[3], scores[3])
        }

        val playerRankings = players
            .map { player ->
                val total = totals.getValue(player.id)
                PlayerRanking(
                    position = 0,
                    playerId = player.id,
                    teamId = player.team,
                    points = total.points,
                    score = total.score,
                )
            }
            .sortedWith(compareByDescending<PlayerRanking> { it.points }.thenByDescending { it.score })
            .mapIndexed { index, ranking -> ranking.copy(position = index + 1) }

        val byPlayerId = playerRankings.associateBy { it.playerId }
        val teamRankings = if (includeTeams) {
            players.groupBy { it.team }
                .map { (teamId, teamPlayers) ->
                    val teamTotals = teamPlayers.mapNotNull { byPlayerId[it.id] }
                    TeamRanking(
                        position = 0,
                        teamId = teamId,
                        points = teamTotals.sumOf { it.points },
                        score = teamTotals.sumOf { it.score },
                    )
                }
                .sortedWith(compareByDescending<TeamRanking> { it.points }.thenByDescending { it.score })
                .mapIndexed { index, ranking -> ranking.copy(position = index + 1) }
        } else {
            emptyList()
        }

        val chickenRankings = tables
            .flatMap { it.hands }
            .filter { it.isDone && it.isChickenHand }
            .mapNotNull { it.playerWinnerId.toIntOrNull() }
            .groupingBy { it }
            .eachCount()
            .mapNotNull { (playerId, count) ->
                byPlayerId[playerId]?.let { player ->
                    ChickenHandRanking(
                        position = 0,
                        playerId = playerId,
                        chickenHands = count,
                        points = player.points,
                        score = player.score,
                    )
                }
            }
            .sortedWith(
                compareByDescending<ChickenHandRanking> { it.chickenHands }
                    .thenByDescending { it.points }
                    .thenByDescending { it.score },
            )
            .mapIndexed { index, ranking -> ranking.copy(position = index + 1) }

        val bestHands = tables
            .flatMap { it.hands }
            .filter { it.isDone }
            .mapNotNull { hand ->
                val playerId = hand.playerWinnerId.toIntOrNull() ?: return@mapNotNull null
                val handScore = hand.handScore.toIntOrNull() ?: return@mapNotNull null
                val player = byPlayerId[playerId] ?: return@mapNotNull null
                BestHandRanking(
                    position = 0,
                    playerId = playerId,
                    handScore = handScore,
                    points = player.points,
                    score = player.score,
                )
            }
            .sortedByDescending { it.handScore }
            .take(MAX_BEST_HANDS)
            .mapIndexed { index, ranking -> ranking.copy(position = index + 1) }

        return TournamentRankings(
            players = playerRankings,
            teams = teamRankings,
            chickenHands = chickenRankings,
            bestHands = bestHands,
        )
    }

    private fun add(
        totals: MutableMap<Int, MutableTotal>,
        playerId: String,
        points: String,
        score: String,
    ) {
        val total = totals[playerId.toIntOrNull()] ?: return
        total.points += points.replace(',', '.').toDoubleOrNull() ?: 0.0
        total.score += score.toIntOrNull() ?: 0
    }

    private data class MutableTotal(
        var points: Double = 0.0,
        var score: Int = 0,
    )

    private companion object {
        const val MAX_BEST_HANDS = 10
    }
}
