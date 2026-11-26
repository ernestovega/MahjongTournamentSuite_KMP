package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertNull

private class AccountAuthRepository(
    var session: AuthSession?,
    var me: AppResult<UserProfile>,
    private val roles: Set<GlobalUserRole> = setOf(GlobalUserRole.PLAYER),
) : AuthRepository {
    var aliases = mutableListOf<String>()
    override suspend fun currentSession(): AuthSession? = session
    override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> =
        if (refreshMode == RefreshMode.FORCE) me else (me as? AppResult.Success)?.let { AppResult.Success(it.value.copy(roles = roles)) } ?: me
    override suspend fun signIn(email: String, password: String): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun savedCredentials(): SavedCredentials? = null
    override suspend fun clearSavedCredentials() = Unit
    override suspend fun refreshSession(): AppResult<AuthSession> = AppResult.Failure(AppError.Network)
    override suspend fun signOut() {
        session = null
    }
    override suspend fun updateAlias(alias: String): AppResult<UserProfile> {
        aliases += alias
        return AppResult.Success(profile.copy(alias = alias)).also { me = it }
    }
    override suspend fun updatePhoto(contentType: String, bytes: ByteArray): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun removePhoto(): AppResult<UserProfile> = AppResult.Failure(AppError.Network)
    override suspend fun deleteAccount(): AppResult<Unit> {
        session = null
        return AppResult.Success(Unit)
    }

    companion object {
        val profile = UserProfile(uid = "u1", email = "ana@x.es", alias = "Ana", emaId = " 04-123 ")
    }
}

/** The account buttons of the player app: the profile, its dialog, the sign out, the account delete and the sign in. */
@OptIn(ExperimentalTestApi::class)
class PlayerAccountActionsUiTest {
    private val original = Locale.getDefault()

    // The buttons ignore a second press within 500 ms of real time: the test moves this clock before each press.
    private val clock = TestTimeSource()
    private val opened = mutableListOf<SuiteApp>()

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

    private fun ComposeUiTest.show(auth: AccountAuthRepository) = setContent {
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
                    NavHost(navController, startDestination = TournamentsRoute) {
                        composable<TournamentsRoute> {
                            val account = rememberAccountActions(navController)
                            Column {
                                account.Buttons()
                                account.Dialogs()
                            }
                        }
                        composable<SignInRoute> { Text("The sign-in") }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.click(text: String) {
        clock += 1.seconds
        onNode(hasClickAction() and (hasText(text) or hasContentDescription(text))).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(700)
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun withoutAnAccountTheButtonOpensTheSignIn() = runComposeUiTest {
        show(AccountAuthRepository(session = null, me = AppResult.Failure(AppError.Network)))
        waitFor("Sign in")
        click("Sign in")
        waitFor("The sign-in")
    }

    @Test
    fun theProfileSavesTheAliasAndSignsOut() = runComposeUiTest {
        val auth = AccountAuthRepository(AuthSession("u1", "id", "r"), AppResult.Success(AccountAuthRepository.profile))
        show(auth)
        waitFor("04-123")
        click("Ana")
        waitFor("Alias")
        onNode(hasSetTextAction() and hasText("Alias")).performTextReplacement("Anita")
        click("Save")
        waitUntil(timeoutMillis = 5_000) { auth.aliases == listOf("Anita") }
        if (onAllNodes(hasText("Logout")).fetchSemanticsNodes().isEmpty()) click("Anita")
        click("Logout")
        waitFor("Yes, sign out")
        click("Cancel")
        click("Anita")
        click("Logout")
        click("Yes, sign out")
        waitFor("Sign in")
        assertNull(auth.session)
    }

    @Test
    fun anEditorSeesTheButtonOfTheAdminAppAndCanDeleteTheAccount() = runComposeUiTest {
        val auth = AccountAuthRepository(
            AuthSession("u1", "id", "r"),
            AppResult.Success(AccountAuthRepository.profile.copy(emaId = null)),
            roles = setOf(GlobalUserRole.EDITOR),
        )
        show(auth)
        waitFor("Ana")
        click("Ana")
        click("Delete my account")
        click("Delete")
        waitFor("Sign in")
        assertNull(auth.session)
    }

    @Test
    fun aProfileThatDoesNotLoadShowsTheError() = runComposeUiTest {
        val auth = AccountAuthRepository(AuthSession("u1", "id", "r"), AppResult.Failure(AppError.Network))
        show(auth)
        waitFor("Profile")
        click("Profile")
        waitFor("OK")
        click("OK")
        assertEquals(0, onAllNodes(hasText("OK")).fetchSemanticsNodes().size)
    }
}
