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
    ) : AppError
    /** The server refused the file of a player import. The screen writes the reason in the language of the user. */
    data class ImportFile(val code: ImportFileErrorCode, val args: List<String> = emptyList()) : AppError
    data class Unexpected(val message: String?) : AppError
}
