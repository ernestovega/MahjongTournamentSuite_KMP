package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
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

    /**
     * False when the signed-in account has neither the admin role nor the editor role. This app is only for them.
     * When the profile cannot load, this is true: the server refuses every management request of other accounts.
     */
    suspend fun isManager(): Boolean {
        val profile = (authRepository.getMe() as? AppResult.Success)?.value ?: return true
        return profile.roles.any { it == GlobalUserRole.ADMIN || it == GlobalUserRole.EDITOR }
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
