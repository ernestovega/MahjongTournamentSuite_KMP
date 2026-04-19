package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

class DefaultAdminRepository(
    private val backendApi: FunctionsBackendApi,
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : AdminRepository {
    override suspend fun whoAmI(): AppResult<AdminStatus> = runCatching {
        withFreshIdToken { idToken ->
            val status = backendApi.whoAmI(idToken)
            AdminStatus(
                uid = status.uid,
                isSuperadmin = status.superadmin,
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "WhoAmI failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun lookupUser(identifier: String): AppResult<UserProfile> = runCatching {
        withFreshIdToken { idToken ->
            val user = backendApi.lookupUser(
                idToken = idToken,
                identifier = identifier,
            )
            UserProfile(
                uid = user.uid,
                email = user.email,
                emaId = user.emaId,
                contactEmail = user.contactEmail,
            )
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            logger.w(throwable) { "Lookup user failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T> withFreshIdToken(
        block: suspend (String) -> T,
    ): T {
        val session = authRepository.currentSession() ?: error("No active session")

        return try {
            block(session.idToken)
        } catch (e: BackendHttpException) {
            if (e.status != HttpStatusCode.Unauthorized) throw e

            when (val refreshed = authRepository.refreshSession()) {
                is AppResult.Success -> block(refreshed.value.idToken)
                is AppResult.Failure -> throw e
            }
        }
    }
}

private fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is HttpRequestTimeoutException,
    -> AppError.Unexpected("Request timed out contacting the backend. Check VPN/firewall and try again.")
    is BackendHttpException -> AppError.Unexpected("Backend error ${status.value}: ${responseBody.limitForUi()}")
    else -> {
        val kind = this::class.simpleName ?: "Error"
        val details = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        AppError.Unexpected("$kind$details")
    }
}

private fun String.limitForUi(limit: Int = 10_000): String {
    val trimmed = trim()
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…(truncated)"
}
