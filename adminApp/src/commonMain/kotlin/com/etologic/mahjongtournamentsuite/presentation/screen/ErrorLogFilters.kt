package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroup
import com.etologic.mahjongtournamentsuite.presentation.util.matchesSearch

enum class ErrorSort { LATEST, FREQUENT }

/** The choices of the error log screen. A null value means that the filter is off. */
data class ErrorFilters(
    val query: String = "",
    /** Keeps the problems seen in this many hours. The server applies it, so the screen loads only that period. Null keeps all. */
    val periodHours: Int? = null,
    val app: String? = null,
    val platform: String? = null,
    val kind: String? = null,
    /** Hides the problems of development and debug builds. A problem with no known environment stays. */
    val productionOnly: Boolean = true,
    val sort: ErrorSort = ErrorSort.LATEST,
)

fun List<ErrorGroup>.applyFilters(filters: ErrorFilters, nowMillis: Long): List<ErrorGroup> {
    val since = filters.periodHours?.let { nowMillis - it * MILLIS_PER_HOUR }
    val matching = filter { group ->
        (since == null || group.lastSeen >= since) &&
            (filters.app == null || (group.apps[filters.app] ?: 0) > 0) &&
            (filters.platform == null || (group.platforms[filters.platform] ?: 0) > 0) &&
            (filters.kind == null || group.lastKind == filters.kind) &&
            (!filters.productionOnly || group.lastEnv.isBlank() || group.lastEnv == "prod" || group.lastEnv == "unknown") &&
            (filters.query.isBlank() || listOf(group.errorType, group.area, group.message).any { it.matchesSearch(filters.query) })
    }
    return when (filters.sort) {
        ErrorSort.LATEST -> matching.sortedByDescending { it.lastSeen }
        ErrorSort.FREQUENT -> matching.sortedWith(compareByDescending<ErrorGroup> { it.count }.thenByDescending { it.lastSeen })
    }
}

private const val MILLIS_PER_HOUR = 3_600_000L
