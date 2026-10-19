package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
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
            isCompleted = true,
            useTotalsOnly = false,
        )
        val hands = listOf(
            hand(id = 1, winner = "2", score = "8000", chicken = true),
            hand(id = 2, winner = "1", score = "12000", chicken = false),
        )

        val result = calculate(players, listOf(RankingTable(table, hands)), includeTeams = true)

        assertEquals(listOf(4, 2, 1, 3), result.players.map { it.playerId })
        assertEquals(listOf(2, 1), result.teams.map { it.teamId })
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
            playerEastScore = "100",
            playerSouthScore = "100",
            playerWestScore = "",
            playerNorthScore = "",
            isCompleted = false,
            useTotalsOnly = false,
        )
        val hands = listOf(
            hand(id = 1, winner = "1", score = "80", chicken = true, isDone = false),
            hand(id = 2, winner = "2", score = "90", chicken = false, isDone = false),
        )

        val result = calculate(players, listOf(RankingTable(table, hands)), includeTeams = false)

        assertEquals(emptyList(), result.chickenHands)
        assertEquals(emptyList(), result.bestHands)
    }

    private val fourPlayers = listOf(
        TournamentPlayer(id = 1, name = "P1", team = 1),
        TournamentPlayer(id = 2, name = "P2", team = 1),
        TournamentPlayer(id = 3, name = "P3", team = 2),
        TournamentPlayer(id = 4, name = "P4", team = 2),
    )

    private fun completedTable(round: Int, scores: List<String>, forcedZero: String = "") = RankingTable(
        TableState(
            roundId = round,
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
            forcedZeroPlayerId = forcedZero,
        ),
        emptyList(),
    )

    @Test
    fun aSubstitutedSlotKeepsEarlierRoundsAndGetsNothingFromTheRoundsOfTheSubstitute() {
        val players = fourPlayers.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 2)) else it
        }
        val tables = listOf(
            completedTable(1, listOf("30000", "10000", "-10000", "-30000")),
            completedTable(2, listOf("30000", "10000", "-10000", "-30000")),
        )

        val result = calculate(players, tables, includeTeams = true)

        val first = result.players.first { it.playerId == 1 }
        assertEquals(4.0, first.points)
        assertEquals(30000, first.score)
        assertEquals(4.0, result.players.first { it.playerId == 2 }.points)
        assertEquals(20000, result.players.first { it.playerId == 2 }.score)
        assertEquals(PlayerSubstitution(SubstitutionReason.ILLNESS, 2), first.substitution)
        assertEquals(8.0, result.teams.first { it.teamId == 1 }.points)
    }

    @Test
    fun aDisqualifiedSlotHasNoResultInAnyRound() {
        val players = fourPlayers.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.DISQUALIFIED, fromRound = 2)) else it
        }
        val tables = listOf(
            completedTable(1, listOf("30000", "10000", "-10000", "-30000")),
            completedTable(2, listOf("30000", "10000", "-10000", "-30000")),
        )

        val first = calculate(players, tables, includeTeams = false).players.first { it.playerId == 1 }

        assertEquals(0.0, first.points)
        assertEquals(0, first.score)
    }

    @Test
    fun handsWonByASubstituteDoNotCountForThePerson() {
        val players = fourPlayers.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.LATE, fromRound = 2)) else it
        }
        val round1 = completedTable(1, listOf("30000", "10000", "-10000", "-30000"))
        val round2 = completedTable(2, listOf("30000", "10000", "-10000", "-30000"))
        val tables = listOf(
            round1.copy(hands = listOf(hand(1, winner = "1", score = "20", chicken = false))),
            round2.copy(hands = listOf(hand(1, winner = "1", score = "40", chicken = true))),
        )

        val result = calculate(players, tables, includeTeams = false)

        assertEquals(listOf(20), result.bestHands.map { it.handScore })
        assertEquals(emptyList(), result.chickenHands)
    }

    @Test
    fun aSubstituteWhoLeftDuringTheSessionGetsZeroAndTheOthersShareFourTwoOne() {
        val tables = listOf(completedTable(1, listOf("30000", "20000", "10000", "-60000"), forcedZero = "2"))

        val result = calculate(fourPlayers, tables, includeTeams = false)

        assertEquals(listOf(4.0, 0.0, 2.0, 1.0), listOf(1, 2, 3, 4).map { id -> result.players.first { it.playerId == id }.points })
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
