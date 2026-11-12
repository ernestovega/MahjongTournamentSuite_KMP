package com.etologic.mahjongtournamentsuite.domain.model

/** Which write log the activity screen reads. */
enum class ActivitySource(val wire: String) {
    ALL("all"),

    /** Only the table saves. */
    TABLES("tables"),

    /** Only the other changes: settings, players, teams, editors, accounts and the player registry. */
    OTHER("other"),
}

/** The filters of the activity screen. Null shows every tournament or every person. */
data class ActivityFilter(
    val tournamentId: String? = null,
    val uid: String? = null,
    val source: ActivitySource = ActivitySource.ALL,
)

/** One value that a write changed. [handId] is null for a field of the table or of the tournament. */
data class ActivityFieldChange(
    val field: String,
    val from: String?,
    val to: String?,
    val handId: Int? = null,
)

/** One write to the database: who, when and what. */
data class ActivityEntry(
    /** Unique across both logs. */
    val id: String,
    /** True for a table save, false for any other change. */
    val isTableSave: Boolean,
    /** Milliseconds since the epoch. */
    val at: Long,
    /** For example `tournament.update`, `player.assign` or `table.save`. */
    val action: String,
    val uid: String?,
    val name: String?,
    /** Where a table save came from: `editor`, `player` or `system`. Null for other changes. */
    val source: String?,
    val tournamentId: String?,
    val tournamentName: String?,
    val target: String?,
    val details: List<Pair<String, String>>,
    val roundId: Int?,
    val tableId: Int?,
    val version: Int?,
    val changes: List<ActivityFieldChange>,
)

/** One page of the write log, newest first. [nextCursor] is null on the last page. The app sends it back unchanged. */
data class ActivityPage(
    val entries: List<ActivityEntry>,
    val nextCursor: String?,
)
