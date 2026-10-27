package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerImportRequestDto(
    val fileName: String,
    val dataBase64: String,
    val replaceExisting: Boolean = false,
    /** CLEAR, ASSIGN or FINISH. Only the apply route reads it. */
    val phase: String? = null,
    /** Lines of the file for ASSIGN. */
    val rows: List<Int> = emptyList(),
)

@Serializable
data class ImportIssueDto(
    val code: String,
    val severity: String,
    val args: List<String> = emptyList(),
)

@Serializable
data class ImportSlotStateDto(
    val emaId: String? = null,
    val email: String? = null,
    val teamName: String? = null,
)

@Serializable
data class ImportRowPlanDto(
    val row: Int,
    val slotId: Int? = null,
    val emaId: String? = null,
    val email: String? = null,
    val team: String? = null,
    val status: String,
    val issues: List<ImportIssueDto> = emptyList(),
    val createsAccount: Boolean = false,
    val current: ImportSlotStateDto? = null,
)

@Serializable
data class PlayerImportPreviewDto(
    val rows: List<ImportRowPlanDto>,
    val untouchedSlotIds: List<Int> = emptyList(),
    val isTeams: Boolean = false,
    val tournamentStarted: Boolean = false,
    val hasEmailColumn: Boolean = false,
    val hasTeamColumn: Boolean = false,
)

@Serializable
data class PlayerImportFailureDto(
    val row: Int? = null,
    val slotId: Int? = null,
    val message: String,
)

@Serializable
data class PlayerImportResultDto(
    val assigned: Int,
    val emailsSaved: Int,
    val emailsRefused: Int = 0,
    val teamsRenamed: Int,
    val skipped: Int,
    val failures: List<PlayerImportFailureDto> = emptyList(),
)

@Serializable
data class PlayerExportDto(
    val fileName: String,
    val dataBase64: String,
)
