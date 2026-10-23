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

    suspend fun getMe(refreshMode: RefreshMode = RefreshMode.IF_CHANGED): AppResult<UserProfile>

    /** Changes the alias of the signed-in account. Returns the profile with the new alias. */
    suspend fun updateAlias(alias: String): AppResult<UserProfile>

    /** Sets the own photo of the signed-in account. Returns the profile with the new photo. */
    suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile>

    /** Removes the own photo. The photo of the EMA player of the account is the photo again. */
    suspend fun removePhoto(): AppResult<UserProfile>
}
