package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.SupportMessageRequestDto
import com.etologic.mahjongtournamentsuite.data.platform.PlatformNameProvider
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.SupportMessage
import com.etologic.mahjongtournamentsuite.domain.repository.SupportRepository

class DefaultSupportRepository(
    private val backendApi: FunctionsBackendApi,
    private val platformNameProvider: PlatformNameProvider,
    private val logger: Logger,
) : SupportRepository {
    override suspend fun send(message: SupportMessage): AppResult<Unit> = runCatching {
        backendApi.sendSupportMessage(
            SupportMessageRequestDto(
                name = message.name.trim(),
                email = message.email.trim(),
                message = message.message.trim(),
                app = message.app,
                appVersion = message.appVersion,
                platform = platformNameProvider.platformName(),
            ),
        )
        Unit
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Sending the support message failed." }
            AppResult.Failure(throwable.toRepositoryAppError())
        },
    )
}
