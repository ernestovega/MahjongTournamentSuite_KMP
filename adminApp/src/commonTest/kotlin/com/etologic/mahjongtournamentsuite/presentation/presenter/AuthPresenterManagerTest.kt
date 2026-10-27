package com.etologic.mahjongtournamentsuite.presentation.presenter

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AuthPresenterManagerTest {
    private fun presenter(profile: AppResult<UserProfile>) =
        AuthPresenter(ProfileAuthRepository(profile), Logger.withTag("AuthPresenterManagerTest"))

    private fun profileWith(vararg roles: GlobalUserRole) =
        AppResult.Success(UserProfile("uid", "user@example.test", roles = roles.toSet()))

    @Test
    fun anAdminIsAManager() = runTest {
        assertEquals(AppResult.Success(true), presenter(profileWith(GlobalUserRole.ADMIN)).isManager())
    }

    @Test
    fun anEditorIsAManager() = runTest {
        assertEquals(AppResult.Success(true), presenter(profileWith(GlobalUserRole.EDITOR)).isManager())
    }

    @Test
    fun anEditorWhoIsAlsoAPlayerIsAManager() = runTest {
        val result = presenter(profileWith(GlobalUserRole.PLAYER, GlobalUserRole.EDITOR)).isManager()

        assertEquals(AppResult.Success(true), result)
    }

    @Test
    fun aPlayerOnlyAccountIsNotAManager() = runTest {
        assertEquals(AppResult.Success(false), presenter(profileWith(GlobalUserRole.PLAYER)).isManager())
    }

    @Test
    fun anAccountWithNoRoleIsNotAManager() = runTest {
        assertEquals(AppResult.Success(false), presenter(profileWith()).isManager())
    }

    @Test
    fun aProfileThatCannotLoadIsAFailureAndNeverAManager() = runTest {
        val result = presenter(AppResult.Failure(AppError.Unexpected("offline"))).isManager()

        assertIs<AppResult.Failure>(result)
    }
}

private class ProfileAuthRepository(private val profile: AppResult<UserProfile>) : AuthRepository {
    override suspend fun currentSession(): AuthSession? = AuthSession("uid", "id-token", "refresh-token")
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = unused()
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = unused()
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = unused()
    override suspend fun signOut() = Unit
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = profile
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> = unused()
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = unused()
    override suspend fun removePhoto(): AppResult<UserProfile> = unused()

    private fun <T> unused(): AppResult<T> = AppResult.Failure(AppError.Unexpected("Unused test repository method"))
}
