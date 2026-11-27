package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tables and hands that the statistics skip, and the keys of the rivals. */
class CalculateStatisticsEdgeBranchesTest {
    private val calculate = CalculateStatisticsUseCase()
    private val emaIds = mapOf(2 to "04000002", 3 to " ")
    private val players = (1..4).map { TournamentPlayer(id = it, name = "P$it", team = 1, assignedEmaId = emaIds[it]) }

    @Test
    fun aRivalIsTheEmaNumberOrTheTournamentAndTheSlot() {
        val table = table(scores = listOf("30", "10", "-10", "-30"))

        val result = calculate(players, listOf(RankingTable(table, emptyList())), tournamentId = "T")
        val opponents = result.players.first { it.playerId == 1 }.results.single().rivals.map { it.opponent }.toSet()

        assertEquals(setOf("04000002", "T#3", "T#4"), opponents)
    }

    @Test
    fun tablesWithoutFourSeatsOrScoresAreNotCounted() {
        val tables = listOf(
            table(scores = listOf("30", "10", "-10", "-30")).copy(playerEastId = ""),
            table(scores = listOf("30", "10", "-10", "-30")).copy(playerEastId = "2"),
            table(scores = listOf("30", "x", "-10", "-30")),
        )

        val result = calculate(players, tables.map { RankingTable(it, emptyList()) })

        assertEquals(0, result.players.sumOf { it.results.size })
    }

    @Test
    fun onlyValidHandsAreCounted() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10", penalties = listOf("-5;abc;-10", "", "3", "")),
            hand(2, winner = "", loser = "", score = ""),
            hand(3, winner = "", loser = "", score = "0", chicken = true),
            hand(4, winner = "", loser = "2", score = "10"),
            hand(5, winner = "1", loser = "", score = ""),
            hand(6, winner = "1", loser = "2", score = "abc"),
            hand(7, winner = "1", loser = "2", score = "5"),
            hand(8, winner = "1", loser = "2", score = "20", chicken = true),
            hand(9, winner = "x", loser = "2", score = "10"),
            hand(10, winner = "9", loser = "2", score = "10"),
            hand(11, winner = "1", loser = "x", score = "10"),
            hand(12, winner = "1", loser = "9", score = "10"),
            hand(13, winner = "1", loser = "1", score = "10"),
            hand(14, winner = "3", loser = "4", score = "8").copy(isDone = false),
        )

        val result = calculate(players, listOf(RankingTable(table(scores = listOf("0", "0", "0", "0")), hands)))
        val east = result.players.first { it.playerId == 1 }

        assertEquals(2, east.handsPlayed, "the valid hand and the hand not marked done of a completed table")
        assertEquals(1, east.handsWon)
        assertEquals(10, east.highestPenalty)
        assertEquals(1, result.players.first { it.playerId == 3 }.handsWon)
    }

    @Test
    fun anOpenTableCountsOnlyItsDoneHandsAndAChickenWithoutAWinnerIsNotAHand() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10"),
            hand(2, winner = "3", loser = "4", score = "8").copy(isDone = false),
            hand(3, winner = "", loser = "", score = "", chicken = true),
        )
        val open = table(scores = listOf("0", "0", "0", "0")).copy(isCompleted = false)

        val result = calculate(players, listOf(RankingTable(open, hands)))

        assertEquals(0, result.players.first { it.playerId == 3 }.handsWon)
    }

    @Test
    fun aHandOfOnlyPenaltiesCountsAndAScoreWithoutWinnerDoesNot() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10"),
            hand(3, winner = "", loser = "-", score = "0", penalties = listOf("-10", "", "", "")),
            hand(4, winner = "", loser = "", score = "7"),
        )
        val result = calculate(players, listOf(RankingTable(table(scores = listOf("0", "0", "0", "0")), hands)))
        val east = result.players.first { it.playerId == 1 }

        assertEquals(2, east.handsPlayed, "the won hand and the hand of only a penalty")
        assertEquals(10, east.highestPenalty)
    }

    private fun table(scores: List<String>) = TableState(
        roundId = 1,
        tableId = 1,
        playerIds = listOf(1, 2, 3, 4),
        playerEastId = "1",
        playerSouthId = "2",
        playerWestId = "3",
        playerNorthId = "4",
        playerEastScore = scores[0],
        playerSouthScore = scores[1],
        playerWestScore = scores[2],
        playerNorthScore = scores[3],
        isCompleted = true,
        useTotalsOnly = false,
    )

    private fun hand(id: Int, winner: String, loser: String, score: String, chicken: Boolean = false, penalties: List<String> = listOf("", "", "", "")) = TableHand(
        handId = id,
        playerWinnerId = winner,
        playerLooserId = loser,
        handScore = score,
        isChickenHand = chicken,
        isDone = true,
        playerEastPenalty = penalties[0],
        playerSouthPenalty = penalties[1],
        playerWestPenalty = penalties[2],
        playerNorthPenalty = penalties[3],
    )
}
