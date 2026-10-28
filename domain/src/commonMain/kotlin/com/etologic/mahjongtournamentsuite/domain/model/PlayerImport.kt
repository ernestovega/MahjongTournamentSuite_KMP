package com.etologic.mahjongtournamentsuite.domain.model

enum class ImportIssueSeverity { ERROR, WARNING }

enum class ImportIssueCode {
    INVALID_ID,
    UNKNOWN_SLOT,
    DUPLICATE_SLOT,
    MISSING_EMA_ID,
    INVALID_EMA_ID,
    UNKNOWN_EMA_ID,
    DUPLICATE_EMA_ID,
    INVALID_EMAIL,

    /** Two rows have the same email. One email belongs to one account. */
    DUPLICATE_EMAIL,

    /** An account owns the email, and the account has another EMA id or none. */
    EMAIL_OWNED_BY_OTHER_ACCOUNT,
    TEAM_NAME_TOO_LONG,
    TEAM_NAME_CONFLICT,
    TEAM_NAME_IN_USE,
    TEAM_IGNORED,

    /** A code that this version of the app does not know. */
    UNKNOWN,
}

/** Why the server refused the whole file of a player import. */
enum class ImportFileErrorCode {
    NOT_EXCEL,
    TOO_LARGE,
    NO_SHEET,
    SHEET_TOO_LARGE,
    EMPTY,
    MISSING_COLUMNS,
    NO_ROWS,
    TOO_MANY_ROWS,

    /** A table has data, so the people of the slots cannot change. */
    TOURNAMENT_STARTED,

    /** The server found errors when it checked the file again. */
    FILE_HAS_ERRORS,
}

data class ImportIssue(
    val code: ImportIssueCode,
    val severity: ImportIssueSeverity,
    /** Values for the text of the issue, such as the email or the slot number. */
    val args: List<String>,
)

/** The data cells of a row of the file. */
enum class ImportField { ID, PLAYER, EMAIL, TEAM }

/** The cell where the app shows an issue. Every code belongs to one cell. */
val ImportIssueCode.field: ImportField
    get() = when (this) {
        ImportIssueCode.INVALID_ID, ImportIssueCode.UNKNOWN_SLOT, ImportIssueCode.DUPLICATE_SLOT -> ImportField.ID
        ImportIssueCode.MISSING_EMA_ID, ImportIssueCode.INVALID_EMA_ID, ImportIssueCode.UNKNOWN_EMA_ID,
        ImportIssueCode.DUPLICATE_EMA_ID, ImportIssueCode.UNKNOWN -> ImportField.PLAYER
        ImportIssueCode.INVALID_EMAIL, ImportIssueCode.DUPLICATE_EMAIL, ImportIssueCode.EMAIL_OWNED_BY_OTHER_ACCOUNT -> ImportField.EMAIL
        ImportIssueCode.TEAM_NAME_TOO_LONG, ImportIssueCode.TEAM_NAME_CONFLICT, ImportIssueCode.TEAM_NAME_IN_USE,
        ImportIssueCode.TEAM_IGNORED -> ImportField.TEAM
    }

/** The values of a row as the file holds them. The app shows them, also when they are wrong. */
data class ImportFileValues(val id: String = "", val emaId: String = "", val email: String = "", val team: String = "")

data class ImportRowPlan(
    /** The line of the file, counted from 1. */
    val row: Int,
    val slotId: Int?,
    val emaId: String?,
    val email: String?,
    val team: String?,
    val issues: List<ImportIssue>,
    val file: ImportFileValues = ImportFileValues(),
    /** The data (player, email, team) that the import changes in the slot. */
    val changes: Set<ImportField> = emptySet(),
) {
    val hasError: Boolean get() = issues.any { it.severity == ImportIssueSeverity.ERROR }
}

/** A player who sits in a slot now and whom the file leaves out. */
data class ImportRemovedPlayer(val slotId: Int, val emaId: String)

data class PlayerImportPreview(
    val rows: List<ImportRowPlan>,
    /** The players that the import removes. */
    val removed: List<ImportRemovedPlayer> = emptyList(),
    /** True when no row has an error. Only then can the import apply. */
    val valid: Boolean,
    val isTeams: Boolean,
    /** True when a table has data. The import is then refused. */
    val tournamentStarted: Boolean,
    /** How many slots hold a player now. The import overwrites them. */
    val existingPlayers: Int,
    /** How many slots hold a player after the import. */
    val importedPlayers: Int,
    val hasEmailColumn: Boolean,
    val hasTeamColumn: Boolean,
) {
    val errorCount: Int get() = rows.count { it.hasError }
    val canApply: Boolean get() = valid && !tournamentStarted
}

data class PlayerImportResult(
    /** Slots that hold a player after the import. */
    val players: Int,
    /** Slots whose data changed. */
    val changedSlots: Int,
    /** Slots that the file does not list, now empty. */
    val emptiedSlots: Int,
)

/** An Excel file with the current players of a tournament, in the format of the import file. */
class PlayerExportFile(
    val fileName: String,
    val bytes: ByteArray,
)
