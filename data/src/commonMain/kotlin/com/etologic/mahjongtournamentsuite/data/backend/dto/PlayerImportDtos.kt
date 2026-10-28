package com.etologic.mahjongtournamentsuite.data.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerImportRequestDto(
    val fileName: String,
    val dataBase64: String,
)

@Serializable
data class ImportIssueDto(
    val code: String,
    val severity: String,
    val args: List<String> = emptyList(),
)

@Serializable
data class ImportFileValuesDto(
    val id: String = "",
    val emaId: String = "",
    val email: String = "",
    val team: String = "",
)

@Serializable
data class ImportRowPlanDto(
    val row: Int,
    val file: ImportFileValuesDto = ImportFileValuesDto(),
    val slotId: Int? = null,
    val emaId: String? = null,
    val email: String? = null,
    val team: String? = null,
    val issues: List<ImportIssueDto> = emptyList(),
    val changes: List<String> = emptyList(),
)

@Serializable
data class ImportRemovedPlayerDto(
    val slotId: Int,
    val emaId: String,
)

@Serializable
data class PlayerImportPreviewDto(
    val rows: List<ImportRowPlanDto>,
    val removed: List<ImportRemovedPlayerDto> = emptyList(),
    val valid: Boolean = false,
    val isTeams: Boolean = false,
    val tournamentStarted: Boolean = false,
    val existingPlayers: Int = 0,
    val importedPlayers: Int = 0,
    val hasEmailColumn: Boolean = false,
    val hasTeamColumn: Boolean = false,
)

@Serializable
data class PlayerImportResultDto(
    val players: Int,
    val changedSlots: Int,
    val emptiedSlots: Int,
)

@Serializable
data class PlayerExportDto(
    val fileName: String,
    val dataBase64: String,
)
