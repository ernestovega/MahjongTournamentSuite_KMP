package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerEmailItemDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerEmailsRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
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
    override suspend fun send(
        tournamentId: String,
        playerIds: List<Int>,
    ): AppResult<List<PlayerAccountActionResult>> = runCatching {
        authRepository.withFreshIdToken { idToken ->
            val response = backendApi.sendPlayerAccounts(
                idToken = idToken,
                tournamentId = tournamentId,
                request = PlayerAccountActionRequestDto(playerIds.map { PlayerAccountItemDto(it) }),
            )
            // New accounts change who has an account, so the player list is stale.
            cache.markStale("tournament:$tournamentId:players")
            response.results.map { dto ->
                PlayerAccountActionResult(
                    playerId = dto.playerId,
                    outcome = PlayerAccountOutcome.entries.firstOrNull { it.name == dto.outcome } ?: PlayerAccountOutcome.FAILED,
                    reason = dto.reason?.let { name ->
                        PlayerAccountFailure.entries.firstOrNull { it.name == name } ?: PlayerAccountFailure.UNEXPECTED
                    },
                    sentAt = dto.sentAt,
                )
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Sending to the players failed." }
            AppResult.Failure(throwable.toRepositoryAppError())
        },
    )

    override suspend fun saveEmails(
        tournamentId: String,
        items: List<PlayerEmailItem>,
    ): AppResult<List<PlayerEmailResult>> = runCatching {
        authRepository.withFreshIdToken { idToken ->
            val response = backendApi.savePlayerEmails(
                idToken = idToken,
                tournamentId = tournamentId,
                request = PlayerEmailsRequestDto(items.map { PlayerEmailItemDto(it.playerId, it.email) }),
            )
            cache.markStale("tournament:$tournamentId:players")
            response.results.map { dto ->
                PlayerEmailResult(
                    playerId = dto.playerId,
                    outcome = PlayerEmailOutcome.entries.firstOrNull { it.name == dto.outcome } ?: PlayerEmailOutcome.FAILED,
                    reason = dto.reason?.let { name -> PlayerEmailFailure.entries.firstOrNull { it.name == name } },
                )
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Saving the player emails failed." }
            AppResult.Failure(throwable.toRepositoryAppError())
        },
    )
}
