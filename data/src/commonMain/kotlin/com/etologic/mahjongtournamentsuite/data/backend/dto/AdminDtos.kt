package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIResponseDto(
    val uid: String,
    val role: GlobalUserRoleDto,
    val roles: List<GlobalUserRoleDto> = emptyList(),
)

@Serializable
enum class GlobalUserRoleDto {
    EDITOR,
    ADMIN,
    PLAYER,
}

@Serializable
data class TournamentAssignmentDto(
    val tournamentId: String,
    val tournamentName: String = "",
)

@Serializable
data class ManagedUserDto(
    val uid: String,
    val email: String,
    val alias: String = "",
    val emaId: String? = null,
    val photoUrl: String? = null,
    val hasOwnPhoto: Boolean = false,
    val role: GlobalUserRoleDto,
    val roles: List<GlobalUserRoleDto> = emptyList(),
    val disabled: Boolean,
    val tournamentAssignments: List<TournamentAssignmentDto> = emptyList(),
)

@Serializable
data class ManagedUsersResponseDto(
    val users: List<ManagedUserDto>,
)

@Serializable
data class SaveManagedUserRequestDto(
    val email: String,
    val alias: String = "",
    val role: GlobalUserRoleDto,
    val tournamentAssignments: List<TournamentAssignmentDto>,
    /** The EMA ID of the account. An empty text clears it. */
    val emaId: String = "",
)

@Serializable
data class PasswordResetsRequestDto(
    val uids: List<String>,
)

@Serializable
data class PasswordResetResultDto(
    val uid: String,
    val outcome: String,
    val reason: String? = null,
)

@Serializable
data class PasswordResetsResponseDto(
    val results: List<PasswordResetResultDto> = emptyList(),
)

@Serializable
data class SetUserDisabledRequestDto(
    val disabled: Boolean,
)

@Serializable
data class DigestResultDto(
    val emaId: String = "",
    val slotId: Int = 0,
    val roundId: Int = 0,
    val tableId: Int = 0,
    val seat: Int = 0,
    val position: Int = 4,
    val score: Int = 0,
    val points: Double = 0.0,
    val hasHands: Boolean = false,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val selfDraws: Int = 0,
    val dealIns: Int = 0,
    val drawnHands: Int = 0,
    val winningScoreSum: Int = 0,
    val bestHand: Int = 0,
    val chickenHands: Int = 0,
    val dealInScoreSum: Int = 0,
    val lostToSelfDraws: Int = 0,
    val otherWins: Int = 0,
    val penaltyCount: Int = 0,
    val penaltyPoints: Int = 0,
    val maxPenalty: Int = 0,
    val rivals: List<DigestRivalDto> = emptyList(),
    val winRun: HandRunDto = HandRunDto(),
    val noDealInRun: HandRunDto = HandRunDto(),
)

@Serializable
data class HandRunDto(
    val best: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
)

@Serializable
data class DigestRivalDto(
    val opponent: String = "",
    val tablesBehind: Int = 0,
    val discardLosses: Int = 0,
    val selfDrawLosses: Int = 0,
    val othersDiscardWins: Int = 0,
)

@Serializable
data class PersonTournamentDto(
    val tournamentId: String = "",
    val results: List<DigestResultDto> = emptyList(),
)

@Serializable
data class StandingDto(
    val tournamentId: String = "",
    val rank: Int = 1,
    val of: Int = 1,
    val points: Double = 0.0,
    val score: Int = 0,
    val teamRank: Int? = null,
    val teamOf: Int? = null,
    val chickenRank: Int? = null,
    val bestHandRank: Int? = null,
)

@Serializable
data class StandingsResponseDto(
    val standings: List<StandingDto> = emptyList(),
)

@Serializable
data class PersonStatisticsDto(
    val emaId: String = "",
    val tournaments: List<PersonTournamentDto> = emptyList(),
)

@Serializable
data class PlayerTotalsDto(
    val emaId: String = "",
    val tables: Int = 0,
    val points: Double = 0.0,
    val score: Int = 0,
    val tablesWithHands: Int = 0,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val selfDraws: Int = 0,
    val dealIns: Int = 0,
    val penaltyCount: Int = 0,
)

@Serializable
data class StatisticsOverviewDto(
    val tournaments: Int = 0,
    val tablesCompleted: Int = 0,
    val tablesWithHands: Int = 0,
    val handsPlayed: Int = 0,
    val drawnHands: Int = 0,
    val winningHands: Int = 0,
    val selfDraws: Int = 0,
    val winningScoreSum: Int = 0,
    val chickenHands: Int = 0,
    val penaltyCount: Int = 0,
    val highestHand: Int = 0,
    val playerCount: Int = 0,
    val players: List<PlayerTotalsDto> = emptyList(),
)

@Serializable
data class ErrorGroupDto(
    val id: String = "",
    val errorType: String = "",
    val area: String = "",
    val message: String = "",
    val count: Int = 0,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val lastKind: String = "",
    val lastApp: String = "",
    val lastPlatform: String = "",
    val lastVersion: String = "",
    val lastEnv: String = "",
    val platforms: Map<String, Int> = emptyMap(),
    val apps: Map<String, Int> = emptyMap(),
)

@Serializable
data class ErrorLogCursorDto(
    val at: Long = 0,
    val id: String = "",
)

@Serializable
data class ErrorGroupsResponseDto(
    val groups: List<ErrorGroupDto> = emptyList(),
    val nextCursor: ErrorLogCursorDto? = null,
)

@Serializable
data class ErrorReportDto(
    val reportId: String = "",
    val createdAt: Long = 0,
    val app: String = "",
    val platform: String = "",
    val kind: String = "",
    val env: String = "",
    val role: String? = null,
    val appVersion: String = "",
    val reason: String = "",
    val httpStatus: Int? = null,
    val message: String = "",
    val language: String = "",
)

@Serializable
data class ErrorGroupDetailDto(
    val id: String = "",
    val errorType: String = "",
    val area: String = "",
    val message: String = "",
    val stackTrace: String = "",
    val count: Int = 0,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val lastKind: String = "",
    val lastApp: String = "",
    val lastPlatform: String = "",
    val lastVersion: String = "",
    val lastEnv: String = "",
    val platforms: Map<String, Int> = emptyMap(),
    val apps: Map<String, Int> = emptyMap(),
    val reports: List<ErrorReportDto> = emptyList(),
    val nextReportCursor: ErrorLogCursorDto? = null,
)
