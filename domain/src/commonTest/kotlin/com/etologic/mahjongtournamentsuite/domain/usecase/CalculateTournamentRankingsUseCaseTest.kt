package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateTournamentRankingsUseCaseTest {
    private val calculate = CalculateTournamentRankingsUseCase()

    @Test
    fun sortsPlayersTeamsAndSpecialHandsLikeTheWindowsApp() {
        val players = listOf(
            TournamentPlayer(id = 1, name = "Old name", team = 1),
            TournamentPlayer(id = 2, name = "Old name", team = 1),
            TournamentPlayer(id = 3, name = "Old name", team = 2),
            TournamentPlayer(id = 4, name = "Old name", team = 2),
        )
        val table = TableState(
            roundId = 1,
            tableId = 1,
            playerIds = listOf(1, 2, 3, 4),
            playerEastId = "1",
            playerSouthId = "2",
            playerWestId = "3",
            playerNorthId = "4",
            playerEastScore = "12000",
            playerSouthScore = "18000",
            playerWestScore = "9000",
            playerNorthScore = "21000",
            playerEastPoints = "2",
            playerSouthPoints = "4",
            playerWestPoints = "0",
            playerNorthPoints = "1",
            isCompleted = true,
            useTotalsOnly = false,
            usePointsCalculation = true,
        )
        val hands = listOf(
            hand(id = 1, winner = "2", score = "8000", chicken = true),
            hand(id = 2, winner = "1", score = "12000", chicken = false),
        )

        val result = calculate(players, listOf(RankingTable(table, hands)), includeTeams = true)

        assertEquals(listOf(2, 1, 4, 3), result.players.map { it.playerId })
        assertEquals(listOf(1, 2), result.teams.map { it.teamId })
        assertEquals(2, result.chickenHands.single().playerId)
        assertEquals(1, result.bestHands.first().playerId)
    }

    @Test
    fun ignoresHandsThatAreNotDoneInSpecialRankings() {
        val players = listOf(
            TournamentPlayer(id = 1, name = "Player 1", team = 1),
            TournamentPlayer(id = 2, name = "Player 2", team = 1),
        )
        val table = TableState(
            roundId = 1,
            tableId = 1,
            playerIds = listOf(1, 2),
            playerEastId = "1",
            playerSouthId = "2",
            playerWestId = "",
            playerNorthId = "",
            playerEastPoints = "1",
            playerSouthPoints = "1",
            playerWestPoints = "",
            playerNorthPoints = "",
            playerEastScore = "100",
            playerSouthScore = "100",
            playerWestScore = "",
            playerNorthScore = "",
            isCompleted = false,
            useTotalsOnly = false,
            usePointsCalculation = true,
        )
        val hands = listOf(
            hand(id = 1, winner = "1", score = "80", chicken = true, isDone = false),
            hand(id = 2, winner = "2", score = "90", chicken = false, isDone = false),
        )

        val result = calculate(players, listOf(RankingTable(table, hands)), includeTeams = false)

        assertEquals(emptyList(), result.chickenHands)
        assertEquals(emptyList(), result.bestHands)
    }

    private fun hand(
        id: Int,
        winner: String,
        score: String,
        chicken: Boolean,
        isDone: Boolean = true,
    ) = TableHand(
        handId = id,
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
