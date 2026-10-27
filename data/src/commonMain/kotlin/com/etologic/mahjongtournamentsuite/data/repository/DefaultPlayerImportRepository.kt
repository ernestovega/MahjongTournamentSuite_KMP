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
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssue
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowStatus
import com.etologic.mahjongtournamentsuite.domain.model.ImportSlotState
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportFailure
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
        backendApi.previewPlayerImport(idToken, tournamentId, requestOf(fileName, bytes, replaceExisting = false))
            .toDomain()
    }

    override suspend fun apply(
        tournamentId: String,
        fileName: String,
        bytes: ByteArray,
        preview: PlayerImportPreview,
        replaceExisting: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): AppResult<PlayerImportResult> = request("Applying the player import") { idToken ->
        val lines = preview.rows
            .filter { it.status == ImportRowStatus.NEW || (it.status == ImportRowStatus.REPLACE && replaceExisting) }
            .map { it.row }
        var assigned = 0
        val failures = mutableListOf<PlayerImportFailure>()
        suspend fun step(phase: String, rows: List<Int> = emptyList()) = backendApi
            .applyPlayerImport(idToken, tournamentId, requestOf(fileName, bytes, replaceExisting, phase, rows))
            .also { part ->
                assigned += part.assigned
                failures += part.failures.map { PlayerImportFailure(it.row, it.slotId, it.message) }
            }
        try {
            onProgress(0, lines.size)
            // Players that move between slots leave their old slot first, so the batches do not depend on each other.
            step("CLEAR")
            lines.chunked(BATCH_SIZE).forEach { batch ->
                step("ASSIGN", batch)
                onProgress(minOf(lines.size, lines.indexOf(batch.last()) + 1), lines.size)
            }
            val last = step("FINISH")
            PlayerImportResult(
                assigned = assigned,
                emailsSaved = last.emailsSaved,
                emailsRefused = last.emailsRefused,
                teamsRenamed = last.teamsRenamed,
                skipped = last.skipped,
                failures = failures.toList(),
            )
        } finally {
            cache.markStale(
                "tournament:$tournamentId:players",
                "tournament:$tournamentId:teams",
                "global:tournaments",
            )
        }
    }

    private fun requestOf(
        fileName: String,
        bytes: ByteArray,
        replaceExisting: Boolean,
        phase: String? = null,
        rows: List<Int> = emptyList(),
    ) = PlayerImportRequestDto(fileName, Base64.Default.encode(bytes), replaceExisting, phase, rows)

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
    if (this is BackendHttpException && status.value == 400) {
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

/** The server accepts at most this many rows in one ASSIGN request. */
private const val BATCH_SIZE = 25

private fun PlayerImportPreviewDto.toDomain() = PlayerImportPreview(
    rows = rows.map { it.toDomain() },
    untouchedSlotIds = untouchedSlotIds,
    isTeams = isTeams,
    tournamentStarted = tournamentStarted,
    hasEmailColumn = hasEmailColumn,
    hasTeamColumn = hasTeamColumn,
)

private fun ImportRowPlanDto.toDomain() = ImportRowPlan(
    row = row,
    slotId = slotId,
    emaId = emaId,
    email = email,
    team = team,
    status = ImportRowStatus.entries.firstOrNull { it.name == status } ?: ImportRowStatus.ERROR,
    issues = issues.map { issue ->
        ImportIssue(
            code = ImportIssueCode.entries.firstOrNull { it.name == issue.code } ?: ImportIssueCode.UNKNOWN,
            severity = ImportIssueSeverity.entries.firstOrNull { it.name == issue.severity } ?: ImportIssueSeverity.ERROR,
            args = issue.args,
        )
    },
    createsAccount = createsAccount,
    current = current?.let { ImportSlotState(it.emaId, it.email, it.teamName) },
)
