package com.etologic.mahjongtournamentsuite.domain.model

/** How people use the apps in the last [periodDays] days. Only administrators can load it. */
data class UsageReport(
    val generatedAt: String,
    val periodDays: Int,
    val current: UsagePeriod,
    /** The period of the same length before the current one. */
    val previous: UsagePeriod,
    val accounts: UsageAccounts,
    val daily: List<DailyUsage>,
    /** Table saves by day of the week (0 is Monday) and hour of the day, in the time zone of the tournament. */
    val heatmap: List<List<Int>>,
    val users: List<UserUsage>,
    val features: List<FeatureUsage>,
    val funnel: List<FunnelStep>,
    val countries: List<CountryUsage>,
    val tournaments: List<TournamentUsage>,
)

data class UsagePeriod(
    val activeUsers: Int,
    val saves: Int,
    val handChanges: Int,
    val playerSaves: Int,
    val editorSaves: Int,
    /** Changes that are not table saves, like settings, players and invitations. */
    val actions: Int,
    val activeTournaments: Int,
    val tournamentsCreated: Int,
    val newAccounts: Int,
    val conflicts: Int,
    val resets: Int,
) {
    /** Share of the saves that came from players, from 0 to 1. */
    val playerShare: Double? get() = if (saves > 0) playerSaves.toDouble() / saves else null

    /** Conflicts divided by saves plus conflicts. */
    val conflictRate: Double? get() = if (saves + conflicts > 0) conflicts.toDouble() / (saves + conflicts) else null
}

data class UsageAccounts(
    val total: Int,
    val admins: Int,
    val editors: Int,
    val players: Int,
    val disabled: Int,
    val active: Int,
    val visitors: Int,
    val dormant: Int,
    val neverSignedIn: Int,
)

data class DailyUsage(
    /** The day as `yyyy-MM-dd`, in UTC. */
    val date: String,
    val saves: Int,
    val actions: Int,
    val activeUsers: Int,
)

enum class UserStatus {
    /** Did work in the period. */
    ACTIVE,

    /** Signed in during the period and did no work. */
    VISITOR,

    /** Signed in before the period only. */
    DORMANT,

    /** Never signed in. */
    NEVER,

    /** Cannot sign in. */
    DISABLED,
}

data class UserUsage(
    val uid: String,
    val name: String?,
    /** `ADMIN`, `EDITOR` or `PLAYER`. */
    val role: String,
    val status: UserStatus,
    val saves: Int,
    val handChanges: Int,
    val actions: Int,
    val tournaments: Int,
    val activeDays: Int,
    val lastActiveDaysAgo: Int?,
    val lastSeenDaysAgo: Int?,
    val createdDaysAgo: Int?,
)

data class FeatureUsage(
    val key: String,
    val label: String,
    val group: String,
    /** True when [uses] counts the period. False when it counts tournaments or tables over all time. */
    val isPeriod: Boolean,
    val uses: Int,
    val users: Int?,
    /** Share from 0 to 1 of the tournaments or tables that use the feature. */
    val adoption: Double?,
    val lastUsedDaysAgo: Int?,
)

data class FunnelStep(
    val key: String,
    val label: String,
    val count: Int,
)

data class CountryUsage(
    val country: String,
    val tournaments: Int,
    val players: Int,
    val saves: Int,
)

enum class TournamentUsageStatus { UPCOMING, LIVE, DONE }

data class TournamentUsage(
    val tournamentId: String,
    val name: String,
    val country: String,
    val startDate: String,
    val endDate: String,
    val status: TournamentUsageStatus,
    val tables: Int,
    val completedTables: Int,
    val handsTables: Int,
    val totalsOnlyTables: Int,
    val manualTotalsTables: Int,
    val saves: Int,
    val savesInPeriod: Int,
    val playerSaves: Int,
    val editorSaves: Int,
    val resets: Int,
    val conflicts: Int,
    val tablesTouched: Int,
    val savesPerTouchedTable: Double?,
    val activeHours: Double?,
    val lastActivityDaysAgo: Int?,
    val slots: Int,
    val assignedSlots: Int,
    val slotsWithAccount: Int,
    val invitationsSent: Int,
    val invitationsAccepted: Int,
    val invitationsPending: Int,
)
