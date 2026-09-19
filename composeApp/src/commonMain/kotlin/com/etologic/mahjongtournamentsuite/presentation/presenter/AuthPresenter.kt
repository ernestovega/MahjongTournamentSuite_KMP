package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials

class AuthPresenter(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) {
    suspend fun hasActiveSession(): Boolean = authRepository.currentSession() != null

    suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<AuthSession> {
        logger.i { "Signing in user." }
        return authRepository.signIn(
            email = email,
            password = password,
        )
    }

    suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        logger.i { "Requesting password reset." }
        return authRepository.requestPasswordReset(email)
    }

    suspend fun savedCredentials(): SavedCredentials? = authRepository.savedCredentials()

    suspend fun clearSavedCredentials() = authRepository.clearSavedCredentials()

    suspend fun signOut() {
        logger.i { "Signing out user." }
        authRepository.signOut()
    }

    suspend fun loadProfile(): AppResult<UserProfile> {
        logger.i { "Loading user profile." }
        return authRepository.getMe()
    }
}
