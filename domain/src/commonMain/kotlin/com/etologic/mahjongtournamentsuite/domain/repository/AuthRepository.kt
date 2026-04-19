package com.etologic.mahjongtournamentsuite.domain.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile

interface AuthRepository {
    suspend fun currentSession(): AuthSession?

    suspend fun signIn(
        identifier: String,
        password: String,
    ): AppResult<AuthSession>

    suspend fun refreshSession(): AppResult<AuthSession>

    suspend fun signOut()

    suspend fun getMe(): AppResult<UserProfile>
}
