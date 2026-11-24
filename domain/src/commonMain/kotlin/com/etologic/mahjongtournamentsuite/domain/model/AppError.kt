package com.etologic.mahjongtournamentsuite.domain.model

sealed interface AppError {
    data object Network : AppError
    /** The remote record changed after the editor loaded it. */
    data class Conflict(
        val message: String,
        val expectedVersion: Long? = null,
        val currentVersion: Long? = null,
        val currentTable: TableState? = null,
        val currentHands: List<TableHand> = emptyList(),
        /** The reason of the server, when it sent one. The screen shows its translation instead of [message]. */
        val code: ConflictCode? = null,
        /** Details of [code], such as a count. */
        val args: List<String> = emptyList(),
    ) : AppError
    /** The server refused the file of a player import. The screen writes the reason in the language of the user. */
    data class ImportFile(val code: ImportFileErrorCode, val args: List<String> = emptyList()) : AppError
    /** The server refused an action of the registrations of a tournament. [args] holds details, such as EMA numbers. */
    data class Registration(val code: RegistrationErrorCode, val args: List<String> = emptyList()) : AppError
    data class Unexpected(val message: String?) : AppError
}
