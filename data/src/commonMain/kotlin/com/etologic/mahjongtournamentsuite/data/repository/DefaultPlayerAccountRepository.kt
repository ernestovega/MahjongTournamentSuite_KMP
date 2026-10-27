package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountItemDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository

class DefaultPlayerAccountRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : PlayerAccountRepository {
    override suspend fun runAction(
        tournamentId: String,
        action: PlayerAccountAction,
        items: List<PlayerAccountActionItem>,
    ): AppResult<List<PlayerAccountActionResult>> = request("Running player account action") { idToken ->
        val response = backendApi.runPlayerAccountAction(
            idToken = idToken,
            tournamentId = tournamentId,
            action = action.name.lowercase(),
            request = PlayerAccountActionRequestDto(items.map { PlayerAccountItemDto(it.playerId, it.email) }),
        )
        markPlayersStale(tournamentId)
        response.results.map { dto ->
            PlayerAccountActionResult(
                playerId = dto.playerId,
                outcome = PlayerAccountOutcome.entries.firstOrNull { it.name == dto.outcome }
                    ?: PlayerAccountOutcome.FAILED,
                message = dto.message,
            )
        }
    }

    override suspend fun saveEmails(
        tournamentId: String,
        items: List<PlayerAccountActionItem>,
    ): AppResult<List<PlayerEmailResult>> = request("Saving player emails") { idToken ->
        val response = backendApi.savePlayerAccountEmails(
            idToken,
            tournamentId,
            PlayerAccountActionRequestDto(items.map { PlayerAccountItemDto(it.playerId, it.email) }),
        )
        markPlayersStale(tournamentId)
        response.results.map { dto ->
            PlayerEmailResult(
                playerId = dto.playerId,
                outcome = PlayerEmailOutcome.entries.firstOrNull { it.name == dto.outcome } ?: PlayerEmailOutcome.FAILED,
                message = dto.message,
            )
        }
    }

    private suspend fun markPlayersStale(tournamentId: String) {
        cache.markStale("tournament:$tournamentId:players")
    }

    private suspend fun <T : Any> request(action: String, block: suspend (String) -> T): AppResult<T> =
        runCatching { authRepository.withFreshIdToken(block) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { throwable ->
                logger.w(throwable) { "$action failed." }
                AppResult.Failure(throwable.toRepositoryAppError())
            },
        )
}
