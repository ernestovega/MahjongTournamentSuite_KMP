package com.etologic.mahjongtournamentsuite.domain.model

/** How people use the apps. Only administrators can load it. */
data class UsageReport(
    val generatedAt: String,
    val totals: UsageTotals,
    val users: List<UserUsage>,
    val tournaments: List<TournamentUsage>,
    /** Saves by hour of the day in UTC, from 0 to 23. */
    val savesByHour: List<Int>,
)

data class UsageTotals(
    val tournaments: Int,
    val saves: Int,
    val playerSaves: Int,
    val editorSaves: Int,
    val resets: Int,
    val conflicts: Int,
    val handChanges: Int,
    /** Share of the saves that came from players, from 0 to 1. */
    val playerShare: Double?,
    /** Conflicts divided by saves plus conflicts. */
    val conflictRate: Double?,
)

data class UserUsage(
    val uid: String,
    val name: String?,
    /** `player` or `editor`. */
    val source: String,
    val saves: Int,
    val handChanges: Int,
)

data class TournamentUsage(
    val tournamentId: String,
    val name: String,
    val tables: Int,
    val completedTables: Int,
    val handsTables: Int,
    val totalsOnlyTables: Int,
    val manualTotalsTables: Int,
    val saves: Int,
    val playerSaves: Int,
    val editorSaves: Int,
    val resets: Int,
    val conflicts: Int,
    val tablesTouched: Int,
    val savesPerTouchedTable: Double?,
    val activeHours: Double?,
    val slots: Int,
    val assignedSlots: Int,
    val slotsWithAccount: Int,
    val invitationsAccepted: Int,
    val invitationsPending: Int,
)
