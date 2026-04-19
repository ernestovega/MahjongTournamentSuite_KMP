package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.BackendHttpException
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInRequestDto
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

class DefaultAuthRepository(
    private val backendApi: FunctionsBackendApi,
    private val sessionStore: AuthSessionStore,
    private val logger: Logger,
) : AuthRepository {
    private var loadedFromStore: Boolean = false
    private var session: AuthSession? = null

    private suspend fun ensureLoadedFromStore() {
        if (loadedFromStore) return
        loadedFromStore = true
        session = sessionStore.load()?.toDomain()
    }

    override suspend fun currentSession(): AuthSession? {
        ensureLoadedFromStore()
        return session
    }

    override suspend fun signIn(
        identifier: String,
        password: String,
    ): AppResult<AuthSession> = runCatching {
        val response = backendApi.signIn(
            SignInRequestDto(
                identifier = identifier,
                password = password,
            ),
        )

        AuthSession(
            uid = response.uid,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
        )
    }.fold(
        onSuccess = { newSession ->
            session = newSession
            sessionStore.save(newSession.toStored())
            AppResult.Success(newSession)
        },
        onFailure = { throwable ->
            logger.w(throwable) { "Sign in failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun refreshSession(): AppResult<AuthSession> = runCatching {
        ensureLoadedFromStore()
        val current = session ?: error("No active session")
        val response = backendApi.refresh(
            RefreshRequestDto(refreshToken = current.refreshToken),
        )

        current.copy(
            idToken = response.idToken,
            refreshToken = response.refreshToken,
        )
    }.fold(
        onSuccess = { newSession ->
            session = newSession
            sessionStore.save(newSession.toStored())
            AppResult.Success(newSession)
        },
        onFailure = { throwable ->
            logger.w(throwable) { "Session refresh failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun signOut() {
        ensureLoadedFromStore()
        session = null
        sessionStore.save(null)
    }

    override suspend fun getMe(): AppResult<UserProfile> = runCatching {
        val me = withFreshIdToken { idToken ->
            backendApi.me(idToken = idToken)
        }

        UserProfile(
            uid = me.uid,
            email = me.email,
            emaId = me.emaId,
            contactEmail = me.contactEmail,
        )
    }.fold(
        onSuccess = { profile -> AppResult.Success(profile) },
        onFailure = { throwable ->
            logger.w(throwable) { "Loading profile failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T> withFreshIdToken(
        block: suspend (String) -> T,
    ): T {
        val current = currentSession() ?: error("No active session")

        return try {
            block(current.idToken)
        } catch (e: BackendHttpException) {
            if (e.status != HttpStatusCode.Unauthorized) throw e

            when (val refreshed = refreshSession()) {
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

private fun StoredAuthSession.toDomain(): AuthSession = AuthSession(
    uid = uid,
    idToken = idToken,
    refreshToken = refreshToken,
)

private fun AuthSession.toStored(): StoredAuthSession = StoredAuthSession(
    uid = uid,
    idToken = idToken,
    refreshToken = refreshToken,
)
