package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

internal suspend fun <T> AuthRepository.withFreshIdToken(
    block: suspend (String) -> T,
): T {
    val session = currentSession() ?: error("No active session")

    return try {
        block(session.idToken)
    } catch (error: BackendHttpException) {
        if (error.status != HttpStatusCode.Unauthorized) throw error

        when (val refreshed = refreshSession()) {
            is AppResult.Success -> block(refreshed.value.idToken)
            is AppResult.Failure -> throw error
        }
    }
}

internal fun Throwable.toRepositoryAppError(
    fallbackMessage: String? = null,
): AppError = when (this) {
    is CancellationException -> throw this
    is HttpRequestTimeoutException -> AppError.Unexpected(
        "Request timed out contacting the backend. Check VPN/firewall and try again.",
    )
    is BackendHttpException -> AppError.Unexpected(
        "Backend error ${status.value}: ${responseBody.limitForUi()}",
    )
    else -> {
        val kind = this::class.simpleName ?: "Error"
        val details = message?.trim()?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""
        AppError.Unexpected(if (details.isEmpty()) fallbackMessage ?: kind else "$kind$details")
    }
}

internal fun String.limitForUi(limit: Int = MAX_UI_ERROR_LENGTH): String {
    val trimmed = trim()
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…(truncated)"
}

private const val MAX_UI_ERROR_LENGTH = 10_000
