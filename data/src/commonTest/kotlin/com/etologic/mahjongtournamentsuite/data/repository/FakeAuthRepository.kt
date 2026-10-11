package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode

internal class FakeAuthRepository : AuthRepository {
    override suspend fun currentSession(): AuthSession = AuthSession(
        uid = "user-1",
        idToken = "id-token",
        refreshToken = "refresh-token",
    )

    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> =
        error("Not used in this test")

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> =
        error("Not used in this test")

    override suspend fun savedCredentials(): SavedCredentials? = null

    override suspend fun clearSavedCredentials() = Unit

    override suspend fun refreshSession(): AppResult<AuthSession> =
        error("Not used in this test")

    override suspend fun signOut() = Unit

    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> =
        error("Not used in this test")
}
