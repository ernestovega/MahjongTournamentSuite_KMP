package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.backend.dto.RefreshRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.SignInRequestDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.PasswordResetRequestDto
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.session.AuthSessionStore
import com.etologic.mahjongtournamentsuite.data.session.CredentialStore
import com.etologic.mahjongtournamentsuite.data.session.SavedCredentials
import com.etologic.mahjongtournamentsuite.data.session.StoredAuthSession
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

class DefaultAuthRepository(
    private val backendApi: FunctionsBackendApi,
    private val sessionStore: AuthSessionStore,
    private val logger: Logger,
    private val credentialStore: CredentialStore,
    private val cache: RepositoryCache,
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
        email: String,
        password: String,
    ): AppResult<AuthSession> = runCatching {
        val response = backendApi.signIn(
            SignInRequestDto(
                email = email,
                password = password,
                identifier = email,
            ),
        )

        AuthSession(
            uid = response.uid,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
        )
    }.fold(
        onSuccess = { newSession ->
            cache.clear()
            session = newSession
            sessionStore.save(newSession.toStored())
            runCatching { credentialStore.save(SavedCredentials(email = email, password = password)) }
            AppResult.Success(newSession)
        },
        onFailure = { throwable ->
            logger.w(throwable) { "Sign in failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = runCatching {
        backendApi.requestPasswordReset(PasswordResetRequestDto(email = email))
        Unit
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { throwable ->
            logger.w(throwable) { "Password reset request failed." }
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
        cache.clear()
    }

    override suspend fun savedCredentials(): SavedCredentials? = credentialStore.load()

    override suspend fun clearSavedCredentials() = credentialStore.clear()

    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = runCatching {
        val current = currentSession() ?: error("No active session")
        cache.getOrLoad(
            ownerUid = current.uid,
            key = "global:profile",
            refreshMode = refreshMode,
            revision = {
                withFreshToken { token ->
                    backendApi.globalDataVersions(token).resources["users"]?.revision ?: 0
                }
            },
            load = {
                val me = withFreshToken { idToken -> backendApi.me(idToken = idToken) }
                UserProfile(
                    uid = me.uid,
                    email = me.email,
                    alias = me.alias,
                )
            },
        )
    }.fold(
        onSuccess = { profile -> AppResult.Success(profile) },
        onFailure = { throwable ->
            logger.w(throwable) { "Loading profile failed." }
            AppResult.Failure(throwable.toAppError())
        },
    )

    private suspend fun <T> withFreshToken(
        block: suspend (String) -> T,
    ): T = this@DefaultAuthRepository.withFreshIdToken(block)
}

private fun Throwable.toAppError(): AppError = toRepositoryAppError()

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
