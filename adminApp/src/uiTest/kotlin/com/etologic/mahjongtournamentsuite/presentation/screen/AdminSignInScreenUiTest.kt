package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
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
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.SupportRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.AuthPresenter
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The sign-in of the manager app: who may enter, what a failure says, and the password reset. */
@OptIn(ExperimentalTestApi::class)
class AdminSignInScreenUiTest {
    private val clock = TestTimeSource()

    // The screen opens the tournaments on the main dispatcher, which a test has only when it sets one.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Auth(
        var session: AuthSession? = null,
        val roles: Set<GlobalUserRole> = setOf(GlobalUserRole.EDITOR),
        val profileFails: Boolean = false,
        val signInFails: Boolean = false,
        val resetError: AppError? = null,
    ) : AuthRepository {
        val resets = mutableListOf<String>()
        var signOuts = 0

        override suspend fun currentSession() = session
        override suspend fun signIn(email: String, password: String): AppResult<AuthSession> {
            if (signInFails) return AppResult.Failure(AppError.Unexpected("Wrong password"))
            return AuthSession("u1", "token", "refresh").also { session = it }.let { AppResult.Success(it) }
        }
        override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
            resets += email
            return resetError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
        }
        override suspend fun savedCredentials(): SavedCredentials? = null
        override suspend fun clearSavedCredentials() = Unit
        override suspend fun refreshSession(): AppResult<AuthSession> = error("unused")
        override suspend fun signOut() {
            signOuts += 1
            session = null
        }
        override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> =
            if (profileFails) AppResult.Failure(AppError.Network) else AppResult.Success(UserProfile("u1", "a@x.es", roles = roles))
        override suspend fun updateAlias(alias: String): AppResult<UserProfile> = error("unused")
        override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = error("unused")
        override suspend fun removePhoto(): AppResult<UserProfile> = error("unused")

        override suspend fun setPublicPhoto(visible: Boolean): AppResult<UserProfile> = error("unused")
        override suspend fun deleteAccount(): AppResult<Unit> = error("unused")
    }

    private fun ComposeUiTest.show(auth: Auth) {
        setContent {
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { AuthPresenter(auth, Logger.withTag("ui-test")) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {}), LocalPressTimeSource provides clock) {
                    MtsTheme(useDarkTheme = false) {
                        val nav = rememberNavController()
                        NavHost(nav, startDestination = SignInRoute) {
                            composable<SignInRoute> { SignInScreen(nav) }
                            composable<TournamentsRoute> { Text("TOURNAMENTS OPEN") }
                            composable<SupportRoute> { Text("SUPPORT OPEN") }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.shown(text: String) = onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) { shown(text) }

    private fun ComposeUiTest.type(label: String, text: String) = onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)

    private fun ComposeUiTest.click(text: String) {
        clock += 1.seconds
        onAllNodes(hasText(text, ignoreCase = true) and hasClickAction() and !hasSetTextAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeUiTest.signIn() {
        type("Email", "a@x.es")
        type("Password", "secret")
        click("Sign in")
    }

    @Test
    fun aSignedInManagerGoesStraightToTheTournaments() = runComposeUiTest {
        show(Auth(session = AuthSession("u1", "t", "r")))
        waitFor("TOURNAMENTS OPEN")
    }

    @Test
    fun aManagerSignsInAndOpensTheTournaments() = runComposeUiTest {
        show(Auth())
        signIn()
        waitFor("TOURNAMENTS OPEN")
    }

    @Test
    fun aPlayerAccountIsSignedOutAgain() = runComposeUiTest {
        val auth = Auth(roles = setOf(GlobalUserRole.PLAYER))
        show(auth)
        signIn()
        waitFor("OK")
        assertEquals(1, auth.signOuts)
        click("OK")
    }

    @Test
    fun aProfileThatDoesNotLoadSignsOut() = runComposeUiTest {
        val auth = Auth(profileFails = true)
        show(auth)
        signIn()
        waitFor("OK")
        assertEquals(1, auth.signOuts)
        click("OK")
    }

    @Test
    fun aWrongPasswordSaysSo() = runComposeUiTest {
        show(Auth(signInFails = true))
        signIn()
        waitFor("OK")
        click("OK")
    }

    @Test
    fun thePasswordResetChecksTheEmailAsksAndSends() = runComposeUiTest {
        val auth = Auth()
        show(auth)
        click("Forgot password?")
        waitFor("Enter your email address first.")
        click("OK")
        type("Email", "not-an-email")
        click("Forgot password?")
        waitFor("does not look right")
        click("OK")
        type("Email", "a@x.es")
        click("Forgot password?")
        waitFor("Yes, send it")
        click("Cancel")
        click("Forgot password?")
        waitFor("Yes, send it")
        click("Yes, send it")
        waitFor("we sent password reset instructions")
        assertEquals(listOf("a@x.es"), auth.resets)
    }

    @Test
    fun aResetWithoutNetworkSaysSo() = runComposeUiTest {
        val auth = Auth(resetError = AppError.Network)
        show(auth)
        type("Email", "a@x.es")
        click("Forgot password?")
        waitFor("Yes, send it")
        click("Yes, send it")
        waitFor("OK")
        assertEquals(listOf("a@x.es"), auth.resets)
    }
}
