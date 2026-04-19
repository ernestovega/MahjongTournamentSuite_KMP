package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
enum class TournamentRoleDto {
    READER,
    EDITOR,
    ADMIN,
}

@Serializable
data class TournamentDto(
    val id: String,
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long = 0,
    val isCompleted: Boolean = false,
    val createdByUid: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Backwards-compatible aliases (older backend / legacy Firestore docs).
    val createdBy: String? = null,
    val created: String? = null,
    val updated: String? = null,
)

@Serializable
data class TournamentsResponseDto(
    val tournaments: List<TournamentDto>,
)

@Serializable
data class CreateTournamentRequestDto(
    val name: String,
    val isTeams: Boolean,
    val numPlayers: Int,
    val numRounds: Int,
    val numTries: Long,
    val players: List<TournamentPlayerDto>,
    val tables: List<TournamentTableDto>,
)

@Serializable
data class TournamentMemberDto(
    val uid: String,
    val role: TournamentRoleDto,
)

@Serializable
data class MembersResponseDto(
    val members: List<TournamentMemberDto>,
)

@Serializable
data class UpsertMemberRequestDto(
    val role: TournamentRoleDto,
)

@Serializable
data class OkResponseDto(
    val ok: Boolean,
)

@Serializable
data class TournamentPlayerDto(
    val id: Int,
    val name: String,
    val team: Int,
)

@Serializable
data class TournamentPlayersResponseDto(
    val players: List<TournamentPlayerDto>,
)

@Serializable
data class TournamentRoundDto(
    val roundId: Int,
)

@Serializable
data class TournamentRoundsResponseDto(
    val rounds: List<TournamentRoundDto>,
)

@Serializable
data class TournamentTableDto(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val isCompleted: Boolean = false,
    val useTotalsOnly: Boolean = false,
)

@Serializable
data class TournamentTablesResponseDto(
    val tables: List<TournamentTableDto>,
)

@Serializable
data class TableStateDto(
    val roundId: Int,
    val tableId: Int,
    val playerIds: List<Int>,
    val playerEastId: String = "",
    val playerSouthId: String = "",
    val playerWestId: String = "",
    val playerNorthId: String = "",
    val playerEastScore: String = "",
    val playerSouthScore: String = "",
    val playerWestScore: String = "",
    val playerNorthScore: String = "",
    val playerEastPoints: String = "",
    val playerSouthPoints: String = "",
    val playerWestPoints: String = "",
    val playerNorthPoints: String = "",
    val manualPlayerEastScore: String = "",
    val manualPlayerSouthScore: String = "",
    val manualPlayerWestScore: String = "",
    val manualPlayerNorthScore: String = "",
    val manualPlayerEastPoints: String = "",
    val manualPlayerSouthPoints: String = "",
    val manualPlayerWestPoints: String = "",
    val manualPlayerNorthPoints: String = "",
    val isCompleted: Boolean = false,
    val useTotalsOnly: Boolean = false,
    val usePointsCalculation: Boolean = true,
)

@Serializable
data class TableHandDto(
    val handId: Int,
    val playerWinnerId: String = "",
    val playerLooserId: String = "",
    val handScore: String = "",
    val isChickenHand: Boolean = false,
    val playerEastPenalty: String = "",
    val playerSouthPenalty: String = "",
    val playerWestPenalty: String = "",
    val playerNorthPenalty: String = "",
)

@Serializable
data class TableWithHandsResponseDto(
    val table: TableStateDto,
    val hands: List<TableHandDto>,
)

@Serializable
data class TablePatchRequestDto(
    val isCompleted: Boolean? = null,
    val useTotalsOnly: Boolean? = null,
    val usePointsCalculation: Boolean? = null,
    val playerEastId: String? = null,
    val playerSouthId: String? = null,
    val playerWestId: String? = null,
    val playerNorthId: String? = null,
    val playerEastScore: String? = null,
    val playerSouthScore: String? = null,
    val playerWestScore: String? = null,
    val playerNorthScore: String? = null,
    val playerEastPoints: String? = null,
    val playerSouthPoints: String? = null,
    val playerWestPoints: String? = null,
    val playerNorthPoints: String? = null,
    val manualPlayerEastScore: String? = null,
    val manualPlayerSouthScore: String? = null,
    val manualPlayerWestScore: String? = null,
    val manualPlayerNorthScore: String? = null,
    val manualPlayerEastPoints: String? = null,
    val manualPlayerSouthPoints: String? = null,
    val manualPlayerWestPoints: String? = null,
    val manualPlayerNorthPoints: String? = null,
)

@Serializable
data class HandPatchRequestDto(
    val playerWinnerId: String? = null,
    val playerLooserId: String? = null,
    val handScore: String? = null,
    val isChickenHand: Boolean? = null,
    val playerEastPenalty: String? = null,
    val playerSouthPenalty: String? = null,
    val playerWestPenalty: String? = null,
    val playerNorthPenalty: String? = null,
)
