package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

/** The body of a 409 of a table save: the versions and the table as the server has it now. */
@Serializable
internal data class TableConflictResponseDto(
    val details: TableConflictDetailsDto? = null,
)

@Serializable
internal data class TableConflictDetailsDto(
    val expectedVersion: Long? = null,
    val currentVersion: Long? = null,
    val current: TableWithHandsResponseDto? = null,
)
