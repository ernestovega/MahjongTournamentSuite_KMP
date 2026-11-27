package com.etologic.mahjongtournamentsuite.player

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.telemetry.Telemetry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The account of the player: who is signed in, the roles, the profile changes, the sign-out and the deletion. */
class PlayerSessionPresenterTest {

    private class RecordingTelemetry : Telemetry {
        val events = mutableListOf<String>()
        val properties = mutableListOf<Pair<String, String?>>()
        override fun event(name: String, params: Map<String, Any>) {
            events += name
        }
        override fun screen(name: String) = Unit
        override fun error(throwable: Throwable, area: String) = Unit
        override fun userProperty(name: String, value: String?) {
            properties += name to value
        }
        override val requiresConsent: Boolean = false
        override fun hasConsent(): Boolean = true
        override fun setConsent(granted: Boolean) = Unit
    }

    private class FakeAuth(
        var session: AuthSession? = AuthSession("u1", "id", "refresh"),
        var me: AppResult<UserProfile> = AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", emaId = "10000001", roles = setOf(GlobalUserRole.PLAYER))),
        var answer: AppResult<UserProfile> = AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", alias = "New")),
        var unitAnswer: AppResult<Unit> = AppResult.Success(Unit),
    ) : AuthRepository {
        var signedOut = false
        var resetFor: String? = null
        override suspend fun currentSession(): AuthSession? = session
        override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = me
        override suspend fun signIn(email: String, password: String): AppResult<AuthSession> =
            if (password == "ok") AppResult.Success(AuthSession("u1", "id", "r")) else AppResult.Failure(AppError.Network)
        override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
            resetFor = email
            return unitAnswer
        }
        override suspend fun savedCredentials(): SavedCredentials? = null
        override suspend fun clearSavedCredentials() = Unit
        override suspend fun refreshSession(): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
        override suspend fun signOut() {
            signedOut = true
        }
        override suspend fun updateAlias(alias: String): AppResult<UserProfile> = answer
        override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = answer
        override suspend fun removePhoto(): AppResult<UserProfile> = answer

        override suspend fun setPublicPhoto(visible: Boolean): AppResult<UserProfile> = answer
        override suspend fun deleteAccount(): AppResult<Unit> = unitAnswer
    }

    private val telemetry = RecordingTelemetry()
    private fun presenter(auth: FakeAuth) = PlayerSessionPresenter(auth, Logger.withTag("test"), telemetry)

    @Test
    fun withoutASessionThereIsNoPlayerAndNoAdmin() = runTest {
        val auth = FakeAuth(session = null)
        assertFalse(presenter(auth).hasSession())
        assertNull(presenter(auth).myEmaId())
        assertFalse(presenter(auth).canManage())
    }

    @Test
    fun theEmaIdAndTheRolesComeFromTheAccount() = runTest {
        val auth = FakeAuth()
        assertEquals("10000001", presenter(auth).myEmaId())
        assertFalse(presenter(auth).canManage())

        auth.me = AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", emaId = " ", roles = setOf(GlobalUserRole.PLAYER, GlobalUserRole.EDITOR)))
        assertNull(presenter(auth).myEmaId())
        assertTrue(presenter(auth).canManage())

        auth.me = AppResult.Failure(AppError.Network)
        assertNull(presenter(auth).myEmaId())
        assertFalse(presenter(auth).canManage())
        assertNull(presenter(auth).cachedProfile())
        assertIs<AppResult.Failure>(presenter(auth).loadProfile())
    }

    @Test
    fun theSignInAndThePasswordResetAnswerAsTheServer() = runTest {
        val auth = FakeAuth()
        assertIs<AppResult.Success<Unit>>(presenter(auth).signIn(" p@x.es ", "ok"))
        assertIs<AppResult.Failure>(presenter(auth).signIn("p@x.es", "bad"))
        assertIs<AppResult.Success<Unit>>(presenter(auth).resetPassword(" p@x.es "))
        assertEquals("p@x.es", auth.resetFor)
    }

    @Test
    fun theProfileLoadsAndChanges() = runTest {
        val auth = FakeAuth()
        val presenter = presenter(auth)
        assertEquals("p@x.es", presenter.cachedProfile()?.email)
        assertIs<AppResult.Success<UserProfile>>(presenter.loadProfile())
        assertEquals("New", (presenter.updateAlias("New") as AppResult.Success).value.alias)
        assertIs<AppResult.Success<UserProfile>>(presenter.updatePhoto("image/png", byteArrayOf(1)))
        assertIs<AppResult.Success<UserProfile>>(presenter.removePhoto())
        assertTrue(telemetry.properties.isNotEmpty())
    }

    @Test
    fun aDeletedAccountSignsOutAndAFailedOneDoesNot() = runTest {
        val auth = FakeAuth()
        assertIs<AppResult.Success<Unit>>(presenter(auth).deleteAccount())
        assertEquals(listOf<String?>(null), telemetry.properties.map { it.second })

        telemetry.properties.clear()
        auth.unitAnswer = AppResult.Failure(AppError.Network)
        assertIs<AppResult.Failure>(presenter(auth).deleteAccount())
        assertTrue(telemetry.properties.isEmpty())
    }

    @Test
    fun theSignOutForgetsTheRole() = runTest {
        val auth = FakeAuth()
        presenter(auth).signOut()
        assertTrue(auth.signedOut)
        assertEquals(listOf<String?>(null), telemetry.properties.map { it.second })
    }
}
