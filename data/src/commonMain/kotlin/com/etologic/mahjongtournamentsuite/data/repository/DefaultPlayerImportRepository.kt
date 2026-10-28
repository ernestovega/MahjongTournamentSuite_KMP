package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.ImportRowPlanDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerImportRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportField
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileValues
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRemovedPlayer
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import kotlin.io.encoding.Base64

class DefaultPlayerImportRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : PlayerImportRepository {
    override suspend fun export(tournamentId: String): AppResult<PlayerExportFile> =
        request("Exporting the players") { idToken ->
            backendApi.exportPlayers(idToken, tournamentId).let { PlayerExportFile(it.fileName, Base64.Default.decode(it.dataBase64)) }
        }

    override suspend fun preview(
        tournamentId: String,
        fileName: String,
        bytes: ByteArray,
    ): AppResult<PlayerImportPreview> = request("Previewing the player import") { idToken ->
        backendApi.previewPlayerImport(idToken, tournamentId, requestOf(fileName, bytes)).toDomain()
    }

    override suspend fun apply(
        tournamentId: String,
        fileName: String,
        bytes: ByteArray,
    ): AppResult<PlayerImportResult> = request("Applying the player import") { idToken ->
        try {
            backendApi.applyPlayerImport(idToken, tournamentId, requestOf(fileName, bytes))
                .let { PlayerImportResult(it.players, it.changedSlots, it.emptiedSlots) }
        } finally {
            cache.markStale(
                "tournament:$tournamentId:players",
                "tournament:$tournamentId:teams",
                "global:tournaments",
            )
        }
    }

    private fun requestOf(fileName: String, bytes: ByteArray) =
        PlayerImportRequestDto(fileName, Base64.Default.encode(bytes))

    private suspend fun <T : Any> request(action: String, block: suspend (String) -> T): AppResult<T> =
        runCatching { authRepository.withFreshIdToken(block) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { throwable ->
                logger.w(throwable) { "$action failed." }
                AppResult.Failure(throwable.toImportAppError())
            },
        )
}

/**
 * A file the server refuses has a `code` in the `details` of the answer, such as the missing columns.
 * The dialog writes the reason in the language of the user.
 */
private fun Throwable.toImportAppError(): AppError {
    if (this is BackendHttpException && (status.value == 400 || status.value == 409)) {
        val details = runCatching { Json.parseToJsonElement(responseBody).jsonObject["details"]?.jsonObject }.getOrNull()
        val code = details?.get("code")?.jsonPrimitive?.contentOrNull
            ?.let { name -> ImportFileErrorCode.entries.firstOrNull { it.name == name } }
        if (code != null) {
            val args = when (code) {
                ImportFileErrorCode.MISSING_COLUMNS ->
                    listOf(details["missing"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content }.orEmpty())
                ImportFileErrorCode.TOO_MANY_ROWS -> listOf(details["max"]?.jsonPrimitive?.content.orEmpty())
                else -> emptyList()
            }
            return AppError.ImportFile(code, args)
        }
    }
    return toRepositoryAppError()
}

private fun PlayerImportPreviewDto.toDomain() = PlayerImportPreview(
    rows = rows.map { it.toDomain() },
    removed = removed.map { ImportRemovedPlayer(it.slotId, it.emaId) },
    valid = valid,
    isTeams = isTeams,
    tournamentStarted = tournamentStarted,
    existingPlayers = existingPlayers,
    importedPlayers = importedPlayers,
    hasEmailColumn = hasEmailColumn,
    hasTeamColumn = hasTeamColumn,
)

private fun ImportRowPlanDto.toDomain() = ImportRowPlan(
    row = row,
    file = ImportFileValues(file.id, file.emaId, file.email, file.team),
    slotId = slotId,
    emaId = emaId,
    email = email,
    team = team,
    issues = issues.map { issue ->
        ImportIssue(
            code = ImportIssueCode.entries.firstOrNull { it.name == issue.code } ?: ImportIssueCode.UNKNOWN,
            severity = ImportIssueSeverity.entries.firstOrNull { it.name == issue.severity } ?: ImportIssueSeverity.ERROR,
            args = issue.args,
        )
    },
    // An unknown name from a newer server is dropped: the cell then shows no change colour.
    changes = changes.mapNotNull { name -> ImportField.entries.firstOrNull { it.name == name } }.toSet(),
)
