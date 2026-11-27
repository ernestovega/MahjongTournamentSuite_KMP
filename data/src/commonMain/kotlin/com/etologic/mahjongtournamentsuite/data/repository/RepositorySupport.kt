package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ConflictCode
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    is BackendHttpException -> codedConflictOrNull() ?: AppError.Unexpected(
        "Backend error ${status.value}: ${responseBody.limitForUi()}",
    )
    else -> if (isConnectionFailure()) AppError.Network else {
        val kind = this::class.simpleName ?: "Error"
        val details = message?.trim().orEmpty().let { if (it.isBlank()) "" else ": $it" }
        AppError.Unexpected(if (details.isEmpty()) fallbackMessage ?: kind else "$kind$details")
    }
}

/**
 * A 409 with a known `details.code` becomes an [AppError.Conflict] with that code, so the screen writes the reason in the
 * language of the user. Any other error gives null.
 */
internal fun Throwable.codedConflictOrNull(): AppError.Conflict? {
    if (this !is BackendHttpException || status != HttpStatusCode.Conflict) return null
    val body = runCatching { Json.parseToJsonElement(responseBody).jsonObject }.getOrNull() ?: return null
    val details = runCatching { body["details"]?.jsonObject }.getOrNull() ?: return null
    val code = runCatching { details["code"]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull()
        ?.let { name -> ConflictCode.entries.firstOrNull { it.name == name } } ?: return null
    val message = runCatching { body["message"]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()
    val args = when (code) {
        ConflictCode.ASSIGNMENTS_MISSING -> listOfNotNull(runCatching { details["missing"]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull())
        // The round before, its open tables and whether it started: "2", "3, 5", "true".
        ConflictCode.PREVIOUS_ROUND_IN_PLAY, ConflictCode.PREVIOUS_ROUND_OPEN_TABLES -> listOf(
            runCatching { details["roundId"]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty(),
            runCatching { details["tables"]?.let { list -> list.jsonArray.joinToString(", ") { it.jsonPrimitive.content } } }.getOrNull().orEmpty(),
            runCatching { details["started"]?.let { it.jsonPrimitive.contentOrNull } }.getOrNull() ?: "true",
        )
        else -> emptyList()
    }
    return AppError.Conflict(message = message, code = code, args = args)
}

private val connectionFailureNames = listOf("ConnectException", "UnknownHost", "SocketException", "SocketTimeout", "IOException", "NoRouteToHost")

/** True when the request did not reach the server: no network, a refused connection or a failed DNS lookup. */
internal fun Throwable.isConnectionFailure(): Boolean = generateSequence(this) { it.cause }.take(6).any { error ->
    val name = error::class.simpleName.orEmpty()
    connectionFailureNames.any(name::contains) || error.message.orEmpty().contains("Failed to fetch", ignoreCase = true)
}

internal fun String.limitForUi(limit: Int = MAX_UI_ERROR_LENGTH): String {
    val trimmed = trim()
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit) + "…(truncated)"
}

private const val MAX_UI_ERROR_LENGTH = 10_000
