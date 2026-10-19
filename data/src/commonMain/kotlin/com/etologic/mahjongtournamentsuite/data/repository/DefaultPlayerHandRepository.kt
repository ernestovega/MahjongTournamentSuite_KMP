package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandPreviewDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.HandRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PenaltiesRequestDto
import com.etologic.mahjongtournamentsuite.domain.model.PenaltyEntry
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.HandPreview
import com.etologic.mahjongtournamentsuite.domain.model.HandProposal
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerHandRepository
import io.ktor.http.HttpStatusCode

class DefaultPlayerHandRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : PlayerHandRepository {
    override suspend fun addPenalties(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        penalties: List<PenaltyEntry>,
        expectedVersion: Long,
    ): AppResult<Unit> = request("Adding penalties") { token ->
        backendApi.addPenalties(
            idToken = token,
            tournamentId = tournamentId,
            roundId = roundId,
            tableId = tableId,
            request = PenaltiesRequestDto(
                playerEastPenalty = penalties.penaltyText(0),
                playerSouthPenalty = penalties.penaltyText(1),
                playerWestPenalty = penalties.penaltyText(2),
                playerNorthPenalty = penalties.penaltyText(3),
                expectedVersion = expectedVersion,
            ),
        )
        cache.markStale("tournament:$tournamentId:tables")
    }

    override suspend fun previewHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
    ): AppResult<HandPreview> = request("Previewing hand") { token ->
        backendApi.previewHand(token, tournamentId, roundId, tableId, proposal.toDto(null)).toDomain()
    }

    override suspend fun recordHand(
        tournamentId: String,
        roundId: Int,
        tableId: Int,
        proposal: HandProposal,
        expectedVersion: Long,
    ): AppResult<HandPreview> = request("Recording hand") { token ->
        backendApi.recordHand(token, tournamentId, roundId, tableId, proposal.toDto(expectedVersion)).toDomain()
            .also { cache.markStale("tournament:$tournamentId:tables") }
    }

    private suspend fun <T : Any> request(action: String, block: suspend (String) -> T): AppResult<T> =
        runCatching { authRepository.withFreshIdToken(block) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { throwable ->
                logger.w(throwable) { "$action failed." }
                // A 409 means that the table is not what the player saw. The app must load it again.
                val conflict = (throwable as? BackendHttpException)
                    ?.takeIf { it.status == HttpStatusCode.Conflict }
                    ?.let { AppError.Conflict(message = it.responseBody.backendMessage()) }
                AppResult.Failure(conflict ?: throwable.toRepositoryAppError())
            },
        )
}

private fun HandProposal.toDto(expectedVersion: Long?) = HandRequestDto(
    playerWinnerId = winnerSlotId?.toString().orEmpty(),
    playerLooserId = loserSlotId?.toString().orEmpty(),
    handScore = score.toString(),
    isChickenHand = isChickenHand,
    isDraw = winnerSlotId == null,
    isLastHand = isLastHand,
    expectedVersion = expectedVersion,
)

/** The penalties of a seat are joined with ";", one part for each penalty. No penalty is an empty text. */
private fun List<PenaltyEntry>.penaltyText(seat: Int): String {
    val parts = map { it.seatValues()[seat].takeIf { value -> value != 0 }?.toString().orEmpty() }
    return if (parts.all { it.isEmpty() }) "" else parts.joinToString(";")
}

private fun HandPreviewDto.toDomain() = HandPreview(
    handId = handId,
    seatSlotIds = seats.mapNotNull { it.toIntOrNull() },
    handScores = handScores,
    tableScores = tableScores,
    tablePoints = tablePoints,
    tableVersion = tableVersion,
)

/** Reads the `message` of a backend error body. It falls back to a fixed text. */
private fun String.backendMessage(): String =
    Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: "The table changed on the server."
