package com.etologic.mahjongtournamentsuite.domain.model

/** Identifies one table inside a tournament. */
data class TableKey(
    val roundId: Int,
    val tableId: Int,
) {
    companion object {
        /** Reads the `<roundId>_<tableId>` form of the backend. Returns null for any other text. */
        fun parse(text: String): TableKey? {
            val parts = text.split('_')
            if (parts.size != 2) return null
            val roundId = parts[0].toIntOrNull() ?: return null
            val tableId = parts[1].toIntOrNull() ?: return null
            return TableKey(roundId, tableId)
        }
    }
}

/**
 * The cheap summary that the backend gives in one request. A client compares it with the data it holds
 * and loads only what changed.
 */
data class TournamentManifest(
    /** Counts every table change of the tournament. It is the coarse cache revision. */
    val tablesRevision: Long,
    /** The version of each table that was saved since the backend tracks it. A missing table has no new version. */
    val tableVersions: Map<TableKey, Long>,
    /** Count the changes of the other lists of the tournament. A higher number means the list is old. */
    val playersRevision: Long = 0,
    val teamsRevision: Long = 0,
    val roundsRevision: Long = 0,
    val membersRevision: Long = 0,
    /** Count the changes that all tournaments share: the tournament list, the EMA players, the countries and the accounts. */
    val tournamentsRevision: Long = 0,
    val emaPlayersRevision: Long = 0,
    val countriesRevision: Long = 0,
    val usersRevision: Long = 0,
)
