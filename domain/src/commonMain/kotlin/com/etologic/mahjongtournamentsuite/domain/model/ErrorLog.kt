package com.etologic.mahjongtournamentsuite.domain.model

/** One problem of the error log. Equal errors of all apps and platforms share one group. */
data class ErrorGroup(
    val id: String,
    val errorType: String,
    val area: String,
    val message: String,
    /** How many times the problem happened since [firstSeen]. */
    val count: Int,
    /** Milliseconds since the epoch. */
    val firstSeen: Long,
    val lastSeen: Long,
    /** `crash` or `handled`. */
    val lastKind: String,
    /** `admin` or `player`. */
    val lastApp: String,
    /** `android`, `web` or `desktop`. */
    val lastPlatform: String,
    val lastVersion: String,
    /** `prod`, `dev`, `debug` or `unknown`. */
    val lastEnv: String,
    val platforms: Map<String, Int>,
    val apps: Map<String, Int>,
)

/** Where the next page of the error log starts. The server gives it, and the app sends it back unchanged. */
data class ErrorLogCursor(val at: Long, val id: String)

/** One page of problems, newest first. [nextCursor] is null on the last page. */
data class ErrorGroupsPage(
    val groups: List<ErrorGroup>,
    val nextCursor: ErrorLogCursor?,
)

/** A group with its sample stack trace and one page of its newest reports. [nextReportCursor] is null on the last page. */
data class ErrorGroupDetail(
    val group: ErrorGroup,
    val stackTrace: String,
    val reports: List<ErrorReportEntry>,
    val nextReportCursor: ErrorLogCursor?,
)

data class ErrorReportEntry(
    val reportId: String,
    val createdAt: Long,
    val app: String,
    val platform: String,
    val kind: String,
    val env: String,
    val role: String?,
    val appVersion: String,
    val reason: String,
    val httpStatus: Int?,
    val message: String,
    val language: String,
)
