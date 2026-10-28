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
data class SetUserDisabledRequestDto(
    val disabled: Boolean,
)

@Serializable
data class UsagePeriodDto(
    val activeUsers: Int = 0,
    val saves: Int = 0,
    val handChanges: Int = 0,
    val playerSaves: Int = 0,
    val editorSaves: Int = 0,
    val actions: Int = 0,
    val activeTournaments: Int = 0,
    val tournamentsCreated: Int = 0,
    val newAccounts: Int = 0,
    val conflicts: Int = 0,
    val resets: Int = 0,
)

@Serializable
data class UsageAccountsDto(
    val total: Int = 0,
    val admins: Int = 0,
    val editors: Int = 0,
    val players: Int = 0,
    val disabled: Int = 0,
    val active: Int = 0,
    val visitors: Int = 0,
    val dormant: Int = 0,
    val neverSignedIn: Int = 0,
)

@Serializable
data class DailyUsageDto(
    val date: String = "",
    val saves: Int = 0,
    val actions: Int = 0,
    val activeUsers: Int = 0,
)

@Serializable
data class UserUsageDto(
    val uid: String,
    val name: String? = null,
    val role: String = "PLAYER",
    val status: String = "NEVER",
    val saves: Int = 0,
    val handChanges: Int = 0,
    val actions: Int = 0,
    val tournaments: Int = 0,
    val activeDays: Int = 0,
    val lastActiveDaysAgo: Int? = null,
    val lastSeenDaysAgo: Int? = null,
    val createdDaysAgo: Int? = null,
)

@Serializable
data class FeatureUsageDto(
    val key: String,
    val label: String = "",
    val group: String = "",
    val scope: String = "PERIOD",
    val uses: Int = 0,
    val users: Int? = null,
    val adoption: Double? = null,
    val lastUsedDaysAgo: Int? = null,
)

@Serializable
data class FunnelStepDto(
    val key: String,
    val label: String = "",
    val count: Int = 0,
)

@Serializable
data class CountryUsageDto(
    val country: String = "",
    val tournaments: Int = 0,
    val players: Int = 0,
    val saves: Int = 0,
)

@Serializable
data class TournamentUsageDto(
    val tournamentId: String,
    val name: String = "",
    val country: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "DONE",
    val tables: Int = 0,
    val completedTables: Int = 0,
    val handsTables: Int = 0,
    val totalsOnlyTables: Int = 0,
    val manualTotalsTables: Int = 0,
    val saves: Int = 0,
    val savesInPeriod: Int = 0,
    val playerSaves: Int = 0,
    val editorSaves: Int = 0,
    val resets: Int = 0,
    val conflicts: Int = 0,
    val tablesTouched: Int = 0,
    val savesPerTouchedTable: Double? = null,
    val activeHours: Double? = null,
    val lastActivityDaysAgo: Int? = null,
    val slots: Int = 0,
    val assignedSlots: Int = 0,
    val slotsWithAccount: Int = 0,
    val slotsWithPassword: Int = 0,
)

@Serializable
data class UsageReportDto(
    val generatedAt: String = "",
    val periodDays: Int = 30,
    val current: UsagePeriodDto = UsagePeriodDto(),
    val previous: UsagePeriodDto = UsagePeriodDto(),
    val accounts: UsageAccountsDto = UsageAccountsDto(),
    val daily: List<DailyUsageDto> = emptyList(),
    val heatmap: List<List<Int>> = emptyList(),
    val users: List<UserUsageDto> = emptyList(),
    val features: List<FeatureUsageDto> = emptyList(),
    val funnel: List<FunnelStepDto> = emptyList(),
    val countries: List<CountryUsageDto> = emptyList(),
    val tournaments: List<TournamentUsageDto> = emptyList(),
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
