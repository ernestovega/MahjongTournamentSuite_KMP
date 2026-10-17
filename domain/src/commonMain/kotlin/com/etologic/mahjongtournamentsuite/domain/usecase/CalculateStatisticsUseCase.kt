package com.etologic.mahjongtournamentsuite.domain.usecase

import com.etologic.mahjongtournamentsuite.domain.model.HandRun
import com.etologic.mahjongtournamentsuite.domain.model.PlayerStatistics
import com.etologic.mahjongtournamentsuite.domain.model.RankingTable
import com.etologic.mahjongtournamentsuite.domain.model.RivalTally
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableResult
import com.etologic.mahjongtournamentsuite.domain.model.TableScoreSwing
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TableStatistics
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentStatistics
import com.etologic.mahjongtournamentsuite.domain.model.seatPoints
import com.etologic.mahjongtournamentsuite.domain.model.seatScores

/**
 * Builds the statistics of one tournament from its tables and hands.
 * A table counts when it is completed. Hand values count only for tables that have hands.
 * The hand scoring matches `handScoring.ts` on the server and `TableEditorState` in the admin app.
 */
class CalculateStatisticsUseCase {
    operator fun invoke(
        players: List<TournamentPlayer>,
        tables: List<RankingTable>,
        tournamentId: String = "",
    ): TournamentStatistics {
        val emaBySlot = players.associate { it.id to it.assignedEmaId.orEmpty() }
        val opponentKey = { slotId: Int -> emaBySlot[slotId].orEmpty().ifBlank { "$tournamentId#$slotId" } }
        val analyses = tables
            .sortedWith(compareBy({ it.table.roundId }, { it.table.tableId }))
            .map { analyse(it, tournamentId, opponentKey) }

        val completed = analyses.filter { it.table.table.isCompleted }
        val resultsByPlayer = completed.flatMap { it.results }.groupBy { it.first }
        val playerStatistics = players.map { player ->
            PlayerStatistics(player.id, resultsByPlayer[player.id].orEmpty().map { it.second })
        }
        val byId = playerStatistics.associateBy { it.playerId }

        val tableStatistics = analyses.map { analysis ->
            val table = analysis.table.table
            val tableResults = analysis.results.groupBy({ it.first }, { it.second })
            TableStatistics(
                roundId = table.roundId,
                tableId = table.tableId,
                isCompleted = table.isCompleted,
                hasHands = analysis.hasHands,
                players = tableResults.map { (id, results) -> PlayerStatistics(id, results) },
                handsPlayed = analysis.handChanges.size,
                drawnHands = analysis.drawnHands,
                selfDraws = analysis.selfDraws,
                dealIns = analysis.dealIns,
                bestHand = analysis.winningScores.maxOrNull(),
                chickenHands = analysis.chickenHands,
                penaltyCount = analysis.results.sumOf { it.second.penaltyCount },
                swing = analysis.swing,
            )
        }

        val handTables = completed.filter { it.hasHands }
        val winningScores = handTables.flatMap { it.winningScores }

        return TournamentStatistics(
            tablesTotal = tables.size,
            tablesCompleted = completed.size,
            tablesWithHands = handTables.size,
            players = playerStatistics,
            handsPlayed = handTables.sumOf { it.handChanges.size },
            drawnHands = handTables.sumOf { it.drawnHands },
            selfDraws = playerStatistics.sumOf { it.selfDraws },
            averageHandScore = winningScores.takeIf { it.isNotEmpty() }?.average(),
            highestHand = winningScores.maxOrNull(),
            chickenHands = handTables.sumOf { it.chickenHands },
            penaltyCount = handTables.sumOf { table -> table.results.sumOf { it.second.penaltyCount } },
            tables = tableStatistics,
        )
    }

    private class TableAnalysis(
        val table: RankingTable,
        val hasHands: Boolean,
        /** The seat score change of every counted hand. */
        val handChanges: List<List<Int>>,
        val results: List<Pair<Int, TableResult>>,
        val swing: List<TableScoreSwing>,
        val winningScores: List<Int>,
        val drawnHands: Int,
        val selfDraws: Int,
        val dealIns: Int,
        val chickenHands: Int,
    )

    private fun analyse(rankingTable: RankingTable, tournamentId: String, opponentKey: (Int) -> String): TableAnalysis {
        val table = rankingTable.table
        val seatIds = seatIds(table)
        val seatScores = table.seatScores.map { it.trim().toIntOrNull() }
        val seatPoints = table.seatPoints.map { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val empty = TableAnalysis(rankingTable, false, emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, 0, 0)
        if (!table.isCompleted || seatIds == null || seatScores.any { it == null }) return empty

        val scores = seatScores.map { it!! }
        val counted = if (table.useTotalsOnly) emptyList() else countedHands(rankingTable.hands, table.isCompleted, seatIds)
        val hasHands = counted.isNotEmpty()

        val running = IntArray(4)
        val won = IntArray(4)
        val selfDraws = IntArray(4)
        val dealIns = IntArray(4)
        val winSum = IntArray(4)
        val best = IntArray(4)
        val chicken = IntArray(4)
        val dealInSum = IntArray(4)
        val lostToSelfDraws = IntArray(4)
        val otherWins = IntArray(4)
        val penaltyCount = IntArray(4)
        val penaltyPoints = IntArray(4)
        val maxPenalty = IntArray(4)
        val swing = List(4) { mutableListOf<Int>() }
        val behind = Array(4) { IntArray(4) }
        val discardLosses = Array(4) { IntArray(4) }
        val selfDrawLosses = Array(4) { IntArray(4) }
        val othersDiscardWins = Array(4) { IntArray(4) }
        val wonFlags = List(4) { mutableListOf<Boolean>() }
        val noDealInFlags = List(4) { mutableListOf<Boolean>() }
        val winningScores = mutableListOf<Int>()
        var drawn = 0
        var tableSelfDraws = 0
        var tableDealIns = 0
        var tableChicken = 0

        counted.forEach { hand ->
            hand.change.forEachIndexed { seat, delta ->
                running[seat] += delta
                swing[seat] += running[seat]
            }
            hand.penalties.forEachIndexed { seat, penalty ->
                if (penalty < 0) {
                    penaltyCount[seat]++
                    penaltyPoints[seat] += -penalty
                }
            }
            hand.worstPenalties.forEachIndexed { seat, worst -> if (worst > maxPenalty[seat]) maxPenalty[seat] = worst }
            val winner = hand.winnerSeat
            (0..3).forEach { seat ->
                wonFlags[seat] += seat == winner
                noDealInFlags[seat] += seat != hand.loserSeat
            }
            if (winner == null) {
                drawn++
                return@forEach
            }
            winningScores += hand.score
            won[winner]++
            winSum[winner] += hand.score
            if (hand.score > best[winner]) best[winner] = hand.score
            if (hand.isChicken) {
                chicken[winner]++
                tableChicken++
            }
            val loser = hand.loserSeat
            if (loser == null) {
                selfDraws[winner]++
                tableSelfDraws++
            } else {
                dealIns[loser]++
                dealInSum[loser] += hand.score
                tableDealIns++
            }
            (0..3).filter { it != winner && it != loser }.forEach { seat ->
                if (loser == null) lostToSelfDraws[seat]++ else otherWins[seat]++
            }
            (0..3).filter { it != winner }.forEach { seat ->
                when {
                    loser == null -> selfDrawLosses[seat][winner]++
                    seat == loser -> discardLosses[seat][winner]++
                    else -> othersDiscardWins[seat][winner]++
                }
            }
        }

        val results = (0..3).map { seat ->
            val position = 1 + scores.count { it > scores[seat] }
            seatIds[seat] to TableResult(
                tournamentId = tournamentId,
                roundId = table.roundId,
                tableId = table.tableId,
                seat = seat,
                position = position,
                score = scores[seat],
                points = seatPoints[seat],
                hasHands = hasHands,
                handsPlayed = if (hasHands) counted.size else 0,
                handsWon = won[seat],
                selfDraws = selfDraws[seat],
                dealIns = dealIns[seat],
                drawnHands = drawn,
                winningScoreSum = winSum[seat],
                bestHand = best[seat],
                chickenHands = chicken[seat],
                dealInScoreSum = dealInSum[seat],
                lostToSelfDraws = lostToSelfDraws[seat],
                otherWins = otherWins[seat],
                penaltyCount = penaltyCount[seat],
                penaltyPoints = penaltyPoints[seat],
                maxPenalty = maxPenalty[seat],
                rivals = (0..3).filter { it != seat }.map { other ->
                    RivalTally(
                        opponent = opponentKey(seatIds[other]),
                        tablesBehind = if (scores[other] > scores[seat]) 1 else 0,
                        discardLosses = discardLosses[seat][other],
                        selfDrawLosses = selfDrawLosses[seat][other],
                        othersDiscardWins = othersDiscardWins[seat][other],
                    )
                },
                winRun = if (hasHands) HandRun.of(wonFlags[seat]) else HandRun.NONE,
                noDealInRun = if (hasHands) HandRun.of(noDealInFlags[seat]) else HandRun.NONE,
            )
        }
        return TableAnalysis(
            table = rankingTable,
            hasHands = hasHands,
            handChanges = counted.map { it.change },
            results = results,
            swing = (0..3).map { TableScoreSwing(seatIds[it], swing[it]) },
            winningScores = winningScores,
            drawnHands = drawn,
            selfDraws = tableSelfDraws,
            dealIns = tableDealIns,
            chickenHands = tableChicken,
        )
    }

    private class CountedHand(
        val winnerSeat: Int?,
        val loserSeat: Int?,
        val score: Int,
        val isChicken: Boolean,
        val change: List<Int>,
        val penalties: List<Int>,
        /** The biggest single penalty of each seat in this hand, as a positive number. */
        val worstPenalties: List<Int>,
    )

    /** Seat ids in the order east, south, west, north. Null when a seat is empty or repeated. */
    private fun seatIds(table: TableState): List<Int>? {
        val ids = listOf(table.playerEastId, table.playerSouthId, table.playerWestId, table.playerNorthId)
            .map { it.trim().toIntOrNull() ?: return null }
        return ids.takeIf { it.toSet().size == 4 }
    }

    private fun countedHands(hands: List<TableHand>, tableCompleted: Boolean, seatIds: List<Int>): List<CountedHand> =
        hands.sortedBy { it.handId }.mapNotNull { hand ->
            if (!hand.isDone && !tableCompleted) return@mapNotNull null
            countedHand(hand, seatIds)
        }

    private fun countedHand(hand: TableHand, seatIds: List<Int>): CountedHand? {
        val winnerText = hand.playerWinnerId.trim()
        val loserText = hand.playerLooserId.trim().let { if (it == "-") "" else it }
        val scoreText = hand.handScore.trim()
        val penaltyTexts = listOf(hand.playerEastPenalty, hand.playerSouthPenalty, hand.playerWestPenalty, hand.playerNorthPenalty)
        val penalties = penaltyTexts.map(::penaltyOf)
        val worstPenalties = penaltyTexts.map(::worstPenaltyOf)

        val isEmpty = winnerText.isEmpty() && loserText.isEmpty() && scoreText.isEmpty() && !hand.isChickenHand
        if (isEmpty) return null
        if (winnerText.isEmpty() && loserText.isEmpty() && (scoreText.isEmpty() || scoreText == "0")) {
            if (hand.isChickenHand) return null
            return CountedHand(null, null, 0, false, penalties, penalties, worstPenalties)
        }
        if (winnerText.isEmpty() || scoreText.isEmpty()) return null
        val score = scoreText.toIntOrNull() ?: return null
        if (score < MIN_HAND_SCORE) return null
        if (hand.isChickenHand && score > MAX_CHICKEN_HAND_SCORE) return null
        val winnerSeat = seatIds.indexOf(winnerText.toIntOrNull() ?: return null).takeIf { it >= 0 } ?: return null
        val loserSeat = if (loserText.isEmpty()) {
            null
        } else {
            seatIds.indexOf(loserText.toIntOrNull() ?: return null).takeIf { it >= 0 && it != winnerSeat } ?: return null
        }
        val change = List(4) { seat ->
            val base = when {
                loserSeat == null -> if (seat == winnerSeat) (score + MIN_HAND_SCORE) * LOSER_COUNT else -(score + MIN_HAND_SCORE)
                seat == winnerSeat -> score + MIN_HAND_SCORE * LOSER_COUNT
                seat == loserSeat -> -(score + MIN_HAND_SCORE)
                else -> -MIN_HAND_SCORE
            }
            base + penalties[seat]
        }
        return CountedHand(winnerSeat, loserSeat, score, hand.isChickenHand, change, penalties, worstPenalties)
    }

    /** A penalty text can hold several values separated by semicolons. A bad part counts as zero. */
    private fun penaltyOf(text: String): Int =
        text.split(';').sumOf { it.trim().toIntOrNull() ?: 0 }

    /** The biggest single penalty of a penalty text, as a positive number. Only negative values are penalties. */
    private fun worstPenaltyOf(text: String): Int =
        text.split(';').maxOfOrNull { -(it.trim().toIntOrNull() ?: 0) }?.coerceAtLeast(0) ?: 0

    private companion object {
        const val MIN_HAND_SCORE = 8
        const val MAX_CHICKEN_HAND_SCORE = 12
        const val LOSER_COUNT = 3
    }
}
