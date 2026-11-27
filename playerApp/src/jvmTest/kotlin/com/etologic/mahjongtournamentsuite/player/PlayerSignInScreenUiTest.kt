package com.etologic.mahjongtournamentsuite.player

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalSuiteAppSwitcher
import com.etologic.mahjongtournamentsuite.presentation.platform.SuiteApp
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private class SignInAuthRepository(
    private val roles: Set<GlobalUserRole>,
    private val resetAnswer: AppResult<Unit>,
) : AuthRepository {
    var session: AuthSession? = null
    val resets = mutableListOf<String>()
    override suspend fun currentSession(): AuthSession? = session
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = AppResult.Success(UserProfile(uid = "u1", email = "p@x.es", roles = roles))
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> =
        if (password == "right") AppResult.Success(AuthSession("u1", "id", "r").also { session = it }) else AppResult.Failure(AppError.Unexpected("Wrong password"))
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        resets += email
        return resetAnswer
    }
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
    override suspend fun signOut() = Unit
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun removePhoto(): AppResult<UserProfile> = AppResult.Failure(AppError.Network)

    override suspend fun setPublicPhoto(visible: Boolean): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun deleteAccount(): AppResult<Unit> = AppResult.Failure(AppError.Network)
}

/** The sign-in of the player app: a wrong password, a good one, the choice of app for an editor and the password reset. */
@OptIn(ExperimentalTestApi::class)
class PlayerSignInScreenUiTest {
    private val original = Locale.getDefault()

    // The buttons ignore a second press within 500 ms of real time: the test moves this clock before each press.
    private val clock = TestTimeSource()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
        Dispatchers.setMain(Dispatchers.Unconfined)
        // Another test class can leave its Koin running: these screens need their own fake account.
        stopKoin()
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(original)
        Dispatchers.resetMain()
        stopKoin()
    }

    private val opened = mutableListOf<SuiteApp>()

    private fun ComposeUiTest.show(auth: SignInAuthRepository) = setContent {
        MtsTheme(useDarkTheme = false) {
            KoinApplication(application = {
                modules(module { single { PlayerSessionPresenter(auth, Logger.withTag("ui-test")) } })
            }) {
                CompositionLocalProvider(
                    LocalPressTimeSource provides clock,
                    LocalSuiteAppSwitcher provides { app: SuiteApp -> opened += app },
                    LocalThemeController provides ThemeController(ThemePreference.Light, false, {}),
                ) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = SignInRoute(openTournamentId = "t-1")) {
                        composable<SignInRoute> { SignInScreen(navController, openTournamentId = "t-1") }
                        composable<TournamentsRoute> { Text("The tournaments") }
                        composable<TournamentRoute> { Text("The tournament") }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.type(label: String, text: String) = onNode(hasSetTextAction() and hasText(label)).performTextInput(text)

    private fun ComposeUiTest.click(text: String) {
        clock += 1.seconds
        onNode(hasText(text, ignoreCase = true) and hasClickAction()).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aWrongPasswordShowsTheErrorAndAGoodOneOpensTheTournament() = runComposeUiTest {
        val auth = SignInAuthRepository(setOf(GlobalUserRole.PLAYER), AppResult.Success(Unit))
        show(auth)
        type("Email", "p@x.es")
        type("Password", "wrong")
        click("Sign in")
        waitFor("OK")
        click("OK")
        onNodeWithText("Something went wrong", substring = true).assertDoesNotExist()
        assertEquals(null, auth.session)
        mainClock.advanceTimeBy(700)
        onNode(hasSetTextAction() and hasText("Password")).performTextReplacement("right")
        click("Sign in")
        waitFor("The tournament")
        assertEquals("u1", auth.session?.uid)
    }

    @Test
    fun aCancelledResetSendsNothing() = runComposeUiTest {
        val auth = SignInAuthRepository(setOf(GlobalUserRole.PLAYER), AppResult.Success(Unit))
        show(auth)
        type("Email", "p@x.es")
        click("Forgot password?")
        waitFor("Yes, send it")
        click("Cancel")
        assertEquals(emptyList(), auth.resets)
    }

    @Test
    fun anEditorChoosesTheAdminApp() = runComposeUiTest {
        show(SignInAuthRepository(setOf(GlobalUserRole.EDITOR), AppResult.Success(Unit)))
        type("Email", "p@x.es")
        type("Password", "right")
        click("Sign in")
        waitFor("What do you want to open?")
        onNodeWithText("Manager app").performClick()
        waitForIdle()
        assertEquals(listOf(SuiteApp.ADMIN), opened)
    }

    @Test
    fun anEditorCanStayInThePlayerApp() = runComposeUiTest {
        show(SignInAuthRepository(setOf(GlobalUserRole.EDITOR), AppResult.Success(Unit)))
        type("Email", "p@x.es")
        type("Password", "right")
        click("Sign in")
        waitFor("What do you want to open?")
        onNodeWithText("Player app").performClick()
        waitFor("The tournament")
    }

    @Test
    fun thePasswordResetAsksForAGoodEmailAndConfirms() = runComposeUiTest {
        val auth = SignInAuthRepository(setOf(GlobalUserRole.PLAYER), AppResult.Success(Unit))
        show(auth)
        click("Forgot password?")
        waitFor("Enter your email address first.")
        click("OK")
        type("Email", "not-an-email")
        mainClock.advanceTimeBy(700)
        click("Forgot password?")
        waitFor("does not look right")
        click("OK")
        type("Email", "@x.es")
        mainClock.advanceTimeBy(700)
        click("Forgot password?")
        waitFor("Yes, send it")
        click("Yes, send it")
        waitFor("we sent password reset instructions")
        assertEquals(listOf("not-an-email@x.es"), auth.resets)
    }

    @Test
    fun aResetWithoutInternetSaysSo() = runComposeUiTest {
        val auth = SignInAuthRepository(setOf(GlobalUserRole.PLAYER), AppResult.Failure(AppError.Network))
        show(auth)
        type("Email", "p@x.es")
        click("Forgot password?")
        click("Yes, send it")
        waitFor("OK")
        assertEquals(1, auth.resets.size)
    }
}
