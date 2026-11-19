package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIResponseDto(
    val uid: String,
    val role: GlobalUserRoleDto,
    val roles: List<GlobalUserRoleDto> = emptyList(),
    val adminOrganizations: List<String> = emptyList(),
    /** The permissions that some role of the account grants somewhere. Null from an older backend. */
    val permissions: List<String>? = null,
)

@Serializable
enum class GlobalUserRoleDto {
    EDITOR,
    CLUB_ADMIN,
    SUPER_ADMIN,
    PLAYER,
}

@Serializable
data class TournamentAssignmentDto(
    val tournamentId: String,
    val tournamentName: String = "",
    val organizerIds: List<String> = emptyList(),
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
    val adminOrganizations: List<String> = emptyList(),
    val canManage: Boolean = false,
    val hasGrantsOutside: Boolean = false,
    val emailHidden: Boolean = false,
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
    /** The organizations of a club admin, as organization ids. */
    val adminOrganizations: List<String> = emptyList(),
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
data class UserDeletesRequestDto(
    val uids: List<String>,
)

@Serializable
data class UserDeleteResultDto(
    val uid: String,
    val outcome: String,
    val reason: String? = null,
)

@Serializable
data class UserDeletesResponseDto(
    val results: List<UserDeleteResultDto> = emptyList(),
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
    val teamPoints: Double? = null,
    val chickenRank: Int? = null,
    val chickenHands: Int = 0,
    val chickenOf: Int = 0,
    val bestHandRank: Int? = null,
    val bestHand: Int = 0,
    val bestHandOf: Int = 0,
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
data class GroupTablesDto(
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
)

@Serializable
data class GroupPersonDto(
    val emaId: String = "",
    val tournaments: Int = 0,
    val tables: Int = 0,
    val points: Double = 0.0,
    val score: Int = 0,
    val place1: Int = 0,
    val place2: Int = 0,
    val place3: Int = 0,
    val place4: Int = 0,
    val tablesWithHands: Int = 0,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val selfDraws: Int = 0,
    val dealIns: Int = 0,
    val drawnHands: Int = 0,
    val winningScoreSum: Int = 0,
    val chickenHands: Int = 0,
    val dealInScoreSum: Int = 0,
    val lostToSelfDraws: Int = 0,
    val otherWins: Int = 0,
    val penaltyCount: Int = 0,
    val penaltyPoints: Int = 0,
    val bestHand: Int = 0,
    val bestTableScore: Int? = null,
    val mostHandsWonAtATable: Int? = null,
    val maxPenalty: Int = 0,
    val tableWinRun: Int = 0,
    val handWinRun: Int? = null,
    val noDealInRun: Int? = null,
)

@Serializable
data class StatisticsGroupDto(
    val tournamentIds: List<String> = emptyList(),
    val tables: GroupTablesDto = GroupTablesDto(),
    val persons: List<GroupPersonDto> = emptyList(),
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

@Serializable
data class AccountRemovalRequestDto(
    /** DISABLE or DELETE, or null to only take the account out of the organizations of the caller. */
    val kind: String? = null,
    val reason: String = "",
)

@Serializable
data class AccountRemovalResponseDto(
    val user: ManagedUserDto,
)

@Serializable
data class AccountRequestDto(
    val id: String,
    val kind: String,
    val targetUid: String,
    val targetEmail: String = "",
    val reason: String = "",
    val requestedByEmail: String = "",
    val createdAtMs: Long = 0,
)

@Serializable
data class AccountRequestsResponseDto(
    val requests: List<AccountRequestDto> = emptyList(),
)

@Serializable
data class ResolveAccountRequestDto(
    val approve: Boolean,
)

@Serializable
data class ResolveAccountRequestResponseDto(
    val outcome: String = "",
)

@Serializable
data class OrganizationDto(
    val id: String,
    val name: String = "",
    val type: String = "CLUB",
    val country: String = "",
    val primaryColor: String = "#02B16B",
    val logoUrl: String? = null,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
)

@Serializable
data class OrganizationsResponseDto(
    val organizations: List<OrganizationDto> = emptyList(),
)

@Serializable
data class SaveOrganizationRequestDto(
    val name: String,
    val type: String,
    val country: String,
    val primaryColor: String,
    val logoContentType: String? = null,
    val logoDataBase64: String? = null,
    val removeLogo: Boolean = false,
)
