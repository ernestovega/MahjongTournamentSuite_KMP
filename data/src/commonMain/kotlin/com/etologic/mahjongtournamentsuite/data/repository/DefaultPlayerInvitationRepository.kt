package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountActionRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PlayerAccountItemDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.InvitationStatus
import com.etologic.mahjongtournamentsuite.domain.model.InvitedPerson
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountAction
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerInvitation
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerInvitationRepository

class DefaultPlayerInvitationRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
    private val cache: RepositoryCache,
) : PlayerInvitationRepository {
    override suspend fun listInvitations(tournamentId: String): AppResult<List<PlayerInvitation>> =
        request("Listing player invitations") { idToken ->
            backendApi.listPlayerInvitations(idToken, tournamentId).invitations.map { dto ->
                PlayerInvitation(
                    invitationId = dto.invitationId,
                    tournamentPlayerId = dto.playerId,
                    email = dto.email,
                    status = InvitationStatus.entries.firstOrNull { it.name == dto.status } ?: InvitationStatus.INVITED,
                    expiresAt = dto.expiresAt,
                )
            }
        }

    override suspend fun listInvitedPeople(): AppResult<List<InvitedPerson>> =
        request("Listing invited people") { idToken ->
            backendApi.listInvitedPeople(idToken).invitations.map { dto ->
                InvitedPerson(
                    invitationId = dto.invitationId,
                    tournamentId = dto.tournamentId,
                    tournamentName = dto.tournamentName,
                    playerId = dto.playerId,
                    emaId = dto.emaId,
                    playerName = dto.playerName,
                    email = dto.email,
                    expiresAt = dto.expiresAt,
                    expired = dto.expired,
                )
            }
        }

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

    override suspend fun saveEmails(tournamentId: String, items: List<PlayerAccountActionItem>): AppResult<Unit> =
        request("Saving player emails") { idToken ->
            backendApi.savePlayerAccountEmails(
                idToken,
                tournamentId,
                PlayerAccountActionRequestDto(items.map { PlayerAccountItemDto(it.playerId, it.email) }),
            )
            markPlayersStale(tournamentId)
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
