package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.AcceptInvitationRequestDto
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository

class DefaultPlayerAccountRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : PlayerAccountRepository {
    override suspend fun acceptInvitation(token: String, password: String?): AppResult<String> = runCatching {
        // A signed-in account sends its token so the backend can match it with the invited account.
        val signedIn = authRepository.currentSession() != null
        val request = AcceptInvitationRequestDto(token = token, password = password)
        val response = if (signedIn) {
            authRepository.withFreshIdToken { idToken -> backendApi.acceptPlayerInvitation(request, idToken) }
        } else {
            backendApi.acceptPlayerInvitation(request, null)
        }
        response.email
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Accepting invitation failed." }
            AppResult.Failure(throwable.toRepositoryAppError())
        },
    )
}
