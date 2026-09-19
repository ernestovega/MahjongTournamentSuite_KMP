package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials

interface AuthRepository {
    suspend fun currentSession(): AuthSession?

    suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<AuthSession>

    suspend fun requestPasswordReset(email: String): AppResult<Unit>

    suspend fun savedCredentials(): SavedCredentials?

    suspend fun clearSavedCredentials()

    suspend fun refreshSession(): AppResult<AuthSession>

    suspend fun signOut()

    suspend fun getMe(): AppResult<UserProfile>
}
