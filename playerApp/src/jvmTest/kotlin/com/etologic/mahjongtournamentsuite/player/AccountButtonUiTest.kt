package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.SavedCredentials
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalSuiteAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class SessionAuthRepository(
    private val session: AuthSession?,
    private val roles: Set<GlobalUserRole> = setOf(GlobalUserRole.PLAYER),
) : AuthRepository {
    override suspend fun currentSession(): AuthSession? = session
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", roles = roles))
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = AppResult.Failure(AppError.Network)
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
    override suspend fun signOut() = Unit
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun removePhoto(): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
}

/** Everything can be read without an account, so the top bar offers the sign-in. With an account it offers the profile. */
@OptIn(ExperimentalTestApi::class)
class AccountButtonUiTest {
    @AfterTest
    fun stopKoinContext() {
        stopKoin()
    }

    private val opened = mutableListOf<SuiteApp>()

    private fun ComposeUiTest.show(session: AuthSession?, roles: Set<GlobalUserRole> = setOf(GlobalUserRole.PLAYER)) = setContent {
        MtsTheme(useDarkTheme = false) {
            KoinApplication(application = {
                modules(module { single { PlayerSessionPresenter(SessionAuthRepository(session, roles), Logger.withTag("ui-test")) } })
            }) {
                CompositionLocalProvider(LocalSuiteAppSwitcher provides { app: SuiteApp -> opened += app }) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = TournamentsRoute) {
                        composable<TournamentsRoute> {
                            val actions = rememberAccountActions(navController)
                            actions.Buttons()
                            actions.Dialogs()
                        }
                        composable<SignInRoute> { Text("The sign-in screen") }
                    }
                }
            }
        }
    }

    @Test
    fun aGuestSeesSignInAndItOpensTheSignInScreen() = runComposeUiTest {
        show(session = null)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Sign in")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Profile").assertDoesNotExist()
        onNodeWithText("Sign in").performClick()
        waitForIdle()
        onNodeWithText("The sign-in screen").assertExists()
    }

    @Test
    fun aSignedInPlayerSeesTheProfileInstead() = runComposeUiTest {
        show(session = AuthSession(uid = "u1", idToken = "id", refreshToken = "refresh"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Profile")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test
    fun aPlayerDoesNotSeeTheManagementButton() = runComposeUiTest {
        show(session = SignedIn)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Profile")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        onNodeWithText("Management").assertDoesNotExist()
    }

    @Test
    fun anEditorSeesTheManagementButtonAndItOpensTheAdminApp() = runComposeUiTest {
        show(session = SignedIn, roles = setOf(GlobalUserRole.EDITOR, GlobalUserRole.PLAYER))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(androidx.compose.ui.test.hasText("Management")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Management").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.ADMIN), opened)
    }
}

private val SignedIn = AuthSession(uid = "u1", idToken = "id", refreshToken = "refresh")
