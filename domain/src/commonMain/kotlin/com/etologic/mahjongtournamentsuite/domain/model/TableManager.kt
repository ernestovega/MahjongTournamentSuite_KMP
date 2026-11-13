package com.etologic.mahjongtournamentsuite.domain.model

data class TableState(
    val version: Long = 0,
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val playerEastId: String,
    val playerSouthId: String,
    val playerWestId: String,
    val playerNorthId: String,
    val playerEastScore: String,
    val playerSouthScore: String,
    val playerWestScore: String,
    val playerNorthScore: String,
    val manualPlayerEastScore: String = "",
    val manualPlayerSouthScore: String = "",
    val manualPlayerWestScore: String = "",
    val manualPlayerNorthScore: String = "",
    val isCompleted: Boolean,
    val useTotalsOnly: Boolean,
    /** Slot id of a substitute who left during this session. That seat gets 0 table points. Empty for most tables. */
    val forcedZeroPlayerId: String = "",
)

data class TableHand(
    val handId: Int,
    val playerWinnerId: String,
    val playerLooserId: String,
    val handScore: String,
    val isChickenHand: Boolean,
    val isDone: Boolean,
    val playerEastPenalty: String,
    val playerSouthPenalty: String,
    val playerWestPenalty: String,
    val playerNorthPenalty: String,
)

private val WHOLE_NUMBER = Regex("^-?\\d+$")

/**
 * A score or penalty read the way the server reads it: digits with an optional minus sign, spaces around ignored.
 * "+8" and "8.5" are not whole numbers. The shared scoring cases (testdata/scoring-cases.json) check this.
 */
fun String.toWholeNumberOrNull(): Int? = trim().takeIf { WHOLE_NUMBER.matches(it) }?.toIntOrNull()
