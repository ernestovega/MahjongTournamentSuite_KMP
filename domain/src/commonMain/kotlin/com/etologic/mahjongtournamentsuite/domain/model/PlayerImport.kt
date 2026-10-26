package com.etologic.mahjongtournamentsuite.domain.model

/** What an import does with one row of the file. */
enum class ImportRowStatus {
    /** The row adds a player, an email or a team name. */
    NEW,

    /** The row puts another player in a slot that has a player. The editor must accept it. */
    REPLACE,

    /** The slot holds this data already. */
    UNCHANGED,

    /** The row has an error and the import skips it. */
    ERROR,
}

enum class ImportIssueSeverity { ERROR, WARNING }

enum class ImportIssueCode {
    INVALID_ID,
    UNKNOWN_SLOT,
    DUPLICATE_SLOT,
    MISSING_EMA_ID,
    INVALID_EMA_ID,
    UNKNOWN_EMA_ID,
    DUPLICATE_EMA_ID,
    EMA_ASSIGNED_ELSEWHERE,
    INVALID_EMAIL,
    TEAM_NAME_TOO_LONG,
    TEAM_NAME_CONFLICT,
    TEAM_NAME_IN_USE,
    REPLACES_PLAYER,
    EMAIL_CHANGES,
    EMAIL_IGNORED_LINKED,
    TEAM_IGNORED,

    /** A code that this version of the app does not know. */
    UNKNOWN,
}

data class ImportIssue(
    val code: ImportIssueCode,
    val severity: ImportIssueSeverity,
    /** Values for the text of the issue, such as the old and the new email. */
    val args: List<String>,
)

/** What the slot of a row holds before the import. */
data class ImportSlotState(
    val emaId: String?,
    val nonMemberName: String?,
    val email: String?,
    val teamName: String?,
)

data class ImportRowPlan(
    /** The line of the file, counted from 1. */
    val row: Int,
    val slotId: Int?,
    val emaId: String?,
    val email: String?,
    val team: String?,
    val status: ImportRowStatus,
    val issues: List<ImportIssue>,
    val current: ImportSlotState?,
)

data class PlayerImportPreview(
    val rows: List<ImportRowPlan>,
    /** Slots that the file does not name. They stay as they are. */
    val untouchedSlotIds: List<Int>,
    val isTeams: Boolean,
    /** True when a table has data, so a replaced player changes results that exist already. */
    val tournamentStarted: Boolean,
    val hasEmailColumn: Boolean,
    val hasTeamColumn: Boolean,
) {
    val replaceCount: Int get() = rows.count { it.status == ImportRowStatus.REPLACE }
    val newCount: Int get() = rows.count { it.status == ImportRowStatus.NEW }
    val errorCount: Int get() = rows.count { it.status == ImportRowStatus.ERROR }
    val unchangedCount: Int get() = rows.count { it.status == ImportRowStatus.UNCHANGED }
}

data class PlayerImportFailure(
    val row: Int?,
    val slotId: Int?,
    val message: String,
)

data class PlayerImportResult(
    val assigned: Int,
    val emailsSaved: Int,
    val teamsRenamed: Int,
    val skipped: Int,
    val failures: List<PlayerImportFailure>,
)

/** An Excel file with the current players of a tournament, in the format of the import file. */
class PlayerExportFile(
    val fileName: String,
    val bytes: ByteArray,
)
