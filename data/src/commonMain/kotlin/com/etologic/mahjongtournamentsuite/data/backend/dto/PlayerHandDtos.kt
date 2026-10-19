package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class HandRequestDto(
    val playerWinnerId: String,
    val playerLooserId: String,
    val handScore: String,
    val isChickenHand: Boolean,
    val isDraw: Boolean = false,
    val isLastHand: Boolean = false,
    val expectedVersion: Long? = null,
)

@Serializable
data class PenaltiesRequestDto(
    val playerEastPenalty: String,
    val playerSouthPenalty: String,
    val playerWestPenalty: String,
    val playerNorthPenalty: String,
    val expectedVersion: Long,
)

@Serializable
data class HandPreviewDto(
    val handId: Int,
    val seats: List<String>,
    val handScores: List<Int>,
    val tableScores: List<String>,
    val tablePoints: List<String>,
    val tableVersion: Long,
)
