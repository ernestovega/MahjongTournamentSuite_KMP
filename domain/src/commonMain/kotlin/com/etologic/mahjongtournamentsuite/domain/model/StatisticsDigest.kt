package com.etologic.mahjongtournamentsuite.domain.model

/** One player seat of a completed table. [emaId] is empty when the slot has no registered player. */
data class DigestResult(
    val emaId: String,
    val slotId: Int,
    val result: TableResult,
)

/** The results of one EMA player in one tournament. */
data class PersonTournamentDigest(
    val tournamentId: String,
    val results: List<DigestResult>,
)

/** The sums of one player over all tournaments. The server keeps them up to date. */
data class PlayerTotals(
    val emaId: String,
    val tables: Int,
    val points: Double,
    val score: Int,
    val tablesWithHands: Int,
    val handsPlayed: Int,
    val handsWon: Int,
    val selfDraws: Int,
    val dealIns: Int,
    val penaltyCount: Int,
)

/**
 * The statistics of all tournaments together. The server keeps the sums, so a request reads a fixed number
 * of documents, whatever the number of tournaments. [players] holds only the players of the leaderboards.
 */
data class StatisticsOverview(
    val tournaments: Int,
    val tablesCompleted: Int,
    val tablesWithHands: Int,
    val handsPlayed: Int,
    val drawnHands: Int,
    val winningHands: Int,
    val winningScoreSum: Int,
    val chickenHands: Int,
    val penaltyCount: Int,
    val highestHand: Int,
    val players: List<PlayerTotals>,
    /** The number of players with statistics. [players] holds only the players of the leaderboards. */
    val playerCount: Int = players.size,
    /** Winning hands that the winner drew by themselves. */
    val selfDraws: Int = 0,
)
