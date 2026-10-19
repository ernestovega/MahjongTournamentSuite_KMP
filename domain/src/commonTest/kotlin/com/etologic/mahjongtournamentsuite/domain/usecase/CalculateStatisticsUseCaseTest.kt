package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.HandRun
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculateStatisticsUseCaseTest {
    private val calculate = CalculateStatisticsUseCase()
    private val players = (1..4).map { TournamentPlayer(id = it, name = "P$it", team = if (it <= 2) 1 else 2) }

    @Test
    fun countsWinsDealInsSelfDrawsAndDraws() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10"),
            hand(2, winner = "1", loser = "", score = "8", chicken = true),
            hand(3, winner = "", loser = "", score = "0"),
            hand(4, winner = "3", loser = "-", score = "20"),
        )
        // Scores follow the hand scoring: 34/-18/-8/-8, 24/-16/-16/-16 ... summed below.
        val table = table(completed = true, scores = listOf("0", "0", "0", "0"))

        val result = calculate(players, listOf(RankingTable(table, hands)))
        val p1 = result.players.first { it.playerId == 1 }
        val p2 = result.players.first { it.playerId == 2 }
        val p3 = result.players.first { it.playerId == 3 }

        assertEquals(4, p1.handsPlayed)
        assertEquals(2, p1.handsWon)
        assertEquals(1, p1.chickenHands)
        assertEquals(1, p2.dealIns)
        assertEquals(10.0, p2.averageDealInScore)
        assertEquals(1, p3.selfDraws)
        assertEquals(1, result.drawnHands)
        assertEquals(20, result.highestHand)
        assertEquals(1, result.tablesWithHands)
    }

    @Test
    fun theRoundsOfASubstituteAreNotInTheStatisticsOfThePerson() {
        val substituted = players.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 2)) else it
        }
        val tables = listOf(
            RankingTable(table(round = 1, completed = true, scores = listOf("30", "10", "-10", "-30")), emptyList()),
            RankingTable(table(round = 2, completed = true, scores = listOf("30", "10", "-10", "-30")), emptyList()),
        )

        val result = calculate(substituted, tables)

        assertEquals(1, result.players.first { it.playerId == 1 }.results.size)
        assertEquals(2, result.players.first { it.playerId == 2 }.results.size)
    }

    @Test
    fun aDisqualifiedPlayerHasZeroPointsAndScoreInTheStatistics() {
        val disqualified = players.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.DISQUALIFIED, fromRound = 2)) else it
        }
        val tables = listOf(
            RankingTable(table(round = 1, completed = true, scores = listOf("30", "10", "-10", "-30")), emptyList()),
            RankingTable(table(round = 2, completed = true, scores = listOf("30", "10", "-10", "-30")), emptyList()),
        )

        val results = calculate(disqualified, tables).players.first { it.playerId == 1 }.results

        assertEquals(1, results.size)
        assertEquals(0.0, results.single().points)
        assertEquals(0, results.single().score)
    }

    @Test
    fun anExcludedPlayerHasNoStatistics() {
        val excluded = players.map {
            if (it.id == 1) it.copy(substitution = PlayerSubstitution(SubstitutionReason.ILLNESS, fromRound = 2, excludedFromEma = true)) else it
        }
        val tables = listOf(
            RankingTable(table(round = 1, completed = true, scores = listOf("30", "10", "-10", "-30")), emptyList()),
        )

        val result = calculate(excluded, tables)

        assertEquals(0, result.players.first { it.playerId == 1 }.results.size)
        assertEquals(1, result.players.first { it.playerId == 2 }.results.size)
    }

    @Test
    fun totalsOnlyTableCountsInResultsButNotInHandStatistics() {
        val table = table(
            completed = true,
            totalsOnly = true,
            scores = listOf("30", "10", "-10", "-30"),
            points = listOf("4", "2", "1", "0"),
        )

        val result = calculate(players, listOf(RankingTable(table, emptyList())))
        val first = result.players.first { it.playerId == 1 }

        assertEquals(1, first.tablesPlayed)
        assertEquals(1, first.placeCounts[0])
        assertEquals(4.0, first.totalPoints)
        assertEquals(0, first.handsPlayed)
        assertNull(first.winRate)
        assertEquals(0, result.tablesWithHands)
        assertEquals(1, result.tablesCompleted)
    }

    @Test
    fun ignoresIncompleteTablesAndInvalidHands() {
        val open = table(completed = false, scores = listOf("0", "0", "0", "0"))
        val result = calculate(players, listOf(RankingTable(open, listOf(hand(1, "1", "2", "10")))))
        assertEquals(0, result.tablesCompleted)

        val done = table(completed = true, scores = listOf("0", "0", "0", "0"))
        val invalid = listOf(hand(1, "1", "2", "5"), hand(2, "1", "1", "10"), hand(3, "9", "2", "10"))
        val second = calculate(players, listOf(RankingTable(done, invalid)))
        assertEquals(0, second.handsPlayed)
    }

    @Test
    fun tiedScoresShareThePlace() {
        val table = table(completed = true, totalsOnly = true, scores = listOf("20", "20", "0", "-40"))
        val result = calculate(players, listOf(RankingTable(table, emptyList())))
        assertEquals(listOf(1, 1, 3, 4), result.players.map { it.results.single().position })
    }

    @Test
    fun countsRunsAndRivalTalliesOfOneTable() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10"),
            hand(2, winner = "1", loser = "-", score = "8"),
            hand(3, winner = "3", loser = "1", score = "10"),
            hand(4, winner = "1", loser = "2", score = "8"),
        )
        val table = table(completed = true, scores = listOf("30", "-10", "0", "-20"))

        val result = calculate(players, listOf(RankingTable(table, hands)))
        val p1 = result.players.first { it.playerId == 1 }.results.single()
        val p2 = result.players.first { it.playerId == 2 }.results.single()

        assertEquals(HandRun(best = 2, start = 2, end = 1), p1.winRun)
        assertEquals(HandRun(best = 2, start = 0, end = 0), p2.noDealInRun)
        val against1 = p2.rivals.first { it.opponent == "#1" }
        assertEquals(RivalTally("#1", tablesBehind = 1, discardLosses = 2, selfDrawLosses = 1), against1)
        // Player 3 won hand 3 on a discard of player 1, so it counts against player 2 as a win on another discard.
        assertEquals(1, p2.rivals.first { it.opponent == "#3" }.othersDiscardWins)
        assertEquals(3, p2.rivals.size)
    }

    @Test
    fun keepsTheBiggestSinglePenaltyAndTheMostHandsWonAtATable() {
        val hands = listOf(
            hand(1, winner = "1", loser = "2", score = "10", penalties = listOf("", "-10;-20", "", "")),
            hand(2, winner = "1", loser = "2", score = "8", penalties = listOf("", "-5", "3", "")),
            hand(3, winner = "3", loser = "2", score = "12", chicken = true),
        )
        val table = table(completed = true, scores = listOf("30", "-10", "0", "-20"))

        val result = calculate(players, listOf(RankingTable(table, hands)))
        val p1 = result.players.first { it.playerId == 1 }
        val p2 = result.players.first { it.playerId == 2 }
        val p3 = result.players.first { it.playerId == 3 }

        assertEquals(2, p1.mostHandsWonAtATable)
        assertEquals(20, p2.highestPenalty)
        // A positive value is not a penalty.
        assertNull(p3.highestPenalty)
        // The chicken hand of player 3 is a win by discard.
        assertEquals(1, p3.chickenWins)
    }

    @Test
    fun ranksRivalsByWeightedScoreOverSharedTables() {
        val tallies = listOf(
            RivalTally("a", tablesBehind = 1, discardLosses = 1, othersDiscardWins = 1),
            RivalTally("b", tablesBehind = 0, selfDrawLosses = 1),
            RivalTally("c", tablesBehind = 1, discardLosses = 3, othersDiscardWins = 2),
        )
        val results = (1..2).map { round ->
            TableResult(roundId = round, tableId = 1, seat = 0, position = 4, score = 0, points = 0.0, hasHands = true, rivals = tallies)
        }
        val rivals = PlayerStatistics(1, results).toughestRivals()

        assertEquals(listOf("c", "a", "b"), rivals.map { it.opponent })
        assertEquals(2, rivals.first().sharedTables)
        assertEquals(emptyList(), PlayerStatistics(1, results.take(1)).toughestRivals())
    }

    @Test
    fun joinsRunsOfTablesThatFollowEachOther() {
        fun result(round: Int, position: Int, hands: Int, noDealIn: HandRun) = TableResult(
            roundId = round, tableId = 1, seat = 0, position = position, score = 0, points = 0.0,
            hasHands = true, handsPlayed = hands, noDealInRun = noDealIn,
        )
        val statistics = PlayerStatistics(
            1,
            listOf(
                result(1, 1, 5, HandRun(best = 3, start = 1, end = 3)),
                result(2, 1, 4, HandRun(best = 4, start = 4, end = 4)),
                result(3, 2, 6, HandRun(best = 2, start = 2, end = 0)),
                result(4, 1, 3, HandRun(best = 1, start = 1, end = 1)),
            ),
        )

        val streaks = statistics.streaks
        assertEquals(2, streaks.tableWins)
        assertEquals(3 + 4 + 2, streaks.handsWithoutDealIn)
        assertEquals(0, streaks.handsWon)
    }

    private fun table(
        round: Int = 1,
        completed: Boolean,
        scores: List<String>,
        points: List<String> = listOf("0", "0", "0", "0"),
        totalsOnly: Boolean = false,
    ) = TableState(
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
        isCompleted = completed,
        useTotalsOnly = totalsOnly,
    )

    private fun hand(
        id: Int,
        winner: String,
        loser: String,
        score: String,
        chicken: Boolean = false,
        penalties: List<String> = listOf("", "", "", ""),
    ) = TableHand(
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
