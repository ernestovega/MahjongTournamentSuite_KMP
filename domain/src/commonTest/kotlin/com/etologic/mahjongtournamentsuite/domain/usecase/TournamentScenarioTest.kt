package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.NonMemberPlayer
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.isAssigned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A deterministic tournament scenario that exercises the real ranking use case.
 *
 * The fixture uses EMA assignments, tournament-only players, two rounds,
 * repeated table loading in a different order, completed hands, and an
 * incomplete hand that must not affect special rankings.
 */
class TournamentScenarioTest {
    private val calculateRankings = CalculateTournamentRankingsUseCase()

    @Test
    fun sixteenPlayerTournamentKeepsTotalsAndRankingsStableAfterReload() {
        val players = fixturePlayers()
        val tables = fixtureTables()

        assertTrue(players.all(TournamentPlayer::isAssigned))
        assertEquals(12, players.mapNotNull { it.assignedEmaId }.toSet().size)
        assertEquals(4, players.count { it.nonMember != null })

        val rankings = calculateRankings(players, tables, includeTeams = true)
        val reloadedRankings = calculateRankings(players, tables.reversed(), includeTeams = true)

        assertEquals(rankings, reloadedRankings)
        assertEquals(
            listOf(1, 5, 2, 9, 3, 6, 13, 4, 7, 10, 8, 11, 14, 12, 15, 16),
            rankings.players.map { it.playerId },
        )
        assertEquals(listOf(1, 2, 3, 4), rankings.teams.map { it.teamId })
        assertEquals(56.0, rankings.players.sumOf { it.points })
        assertEquals(0, rankings.players.sumOf { it.score })
        assertEquals(8, rankings.chickenHands.sumOf { it.chickenHands })
        assertTrue(rankings.bestHands.none { it.handScore == 9999 })
    }

    private fun fixturePlayers(): List<TournamentPlayer> = (1..16).map { id ->
        TournamentPlayer(
            id = id,
            name = "Player $id",
            team = ((id - 1) / 4) + 1,
            assignedEmaId = if (id <= 12) "EMA-$id" else null,
            nonMember = if (id > 12) {
                NonMemberPlayer("Guest", "$id", "EU")
            } else {
                null
            },
        )
    }

    private fun fixtureTables(): List<RankingTable> {
        val rounds = listOf(
            listOf(
                listOf(1, 2, 3, 4),
                listOf(5, 6, 7, 8),
                listOf(9, 10, 11, 12),
                listOf(13, 14, 15, 16),
            ),
            listOf(
                listOf(1, 6, 11, 16),
                listOf(2, 7, 12, 13),
                listOf(3, 8, 9, 14),
                listOf(4, 5, 10, 15),
            ),
        )
        val scoreWeights = listOf(
            listOf("30000", "10000", "-10000", "-30000"),
            listOf("26000", "12000", "-8000", "-30000"),
        )
        val pointWeights = listOf("4", "2", "1", "0")

        return rounds.flatMapIndexed { roundIndex, round ->
            round.mapIndexed { tableIndex, playerIds ->
                val scores = scoreWeights[roundIndex]
                val table = TableState(
                    roundId = roundIndex + 1,
                    tableId = tableIndex + 1,
                    playerIds = playerIds,
                    playerEastId = playerIds[0].toString(),
                    playerSouthId = playerIds[1].toString(),
                    playerWestId = playerIds[2].toString(),
                    playerNorthId = playerIds[3].toString(),
                    playerEastScore = scores[0],
                    playerSouthScore = scores[1],
                    playerWestScore = scores[2],
                    playerNorthScore = scores[3],
                    playerEastPoints = pointWeights[0],
                    playerSouthPoints = pointWeights[1],
                    playerWestPoints = pointWeights[2],
                    playerNorthPoints = pointWeights[3],
                    isCompleted = true,
                    useTotalsOnly = false,
                    usePointsCalculation = true,
                )
                RankingTable(
                    table = table,
                    hands = listOf(
                        hand(
                            handId = 1,
                            winner = table.playerEastId,
                            score = (100 + roundIndex * 10 + tableIndex).toString(),
                            chicken = true,
                        ),
                        hand(
                            handId = 2,
                            winner = table.playerSouthId,
                            score = "9999",
                            chicken = false,
                            isDone = false,
                        ),
                    ),
                )
            }
        }
    }

    private fun hand(
        handId: Int,
        winner: String,
        score: String,
        chicken: Boolean,
        isDone: Boolean = true,
    ) = TableHand(
        handId = handId,
        playerWinnerId = winner,
        playerLooserId = "",
        handScore = score,
        isChickenHand = chicken,
        isDone = isDone,
        playerEastPenalty = "",
        playerSouthPenalty = "",
        playerWestPenalty = "",
        playerNorthPenalty = "",
    )
}
