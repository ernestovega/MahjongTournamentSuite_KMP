package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetFailure
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PasswordResetResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/** An admin selects accounts in App users and sends them the password reset email in one go. */
@OptIn(ExperimentalTestApi::class)
class UsersPasswordResetsUiTest {
    private val ann = ManagedUser("p1", "ann@example.test", "Ann", GlobalUserRole.PLAYER, false, emptyList(), emaId = "11")
    private val bob = ManagedUser("p2", "bob@example.test", "Bob", GlobalUserRole.PLAYER, false, emptyList(), emaId = "22")
    private val cy = ManagedUser("p3", "cy@example.test", "Cy", GlobalUserRole.PLAYER, true, emptyList(), emaId = "33")

    private fun ComposeUiTest.show(admin: FakeAdminRepository) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> {
                                FakeTournamentRepository().apply {
                                    tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
                                }
                            }
                            single<AdminRepository> { admin }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository() }
                            single { AppMemoryStore() }
                            factory { UsersPresenter(get(), get(), get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { GlobalUsersScreen(rememberNavController()) }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("ann@example.test").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun adminWith(vararg users: ManagedUser, role: GlobalUserRole = GlobalUserRole.ADMIN) =
        FakeAdminRepository(role, users.toList())

    @Test
    fun anAdminSelectsAccountsConfirmsAndSeesTheResult() = runComposeUiTest {
        val admin = adminWith(ann, bob, cy)
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithContentDescription("bob@example.test").performClick()
        onNodeWithText("2 selected").assertExists()
        onNodeWithText("Send password reset (2)").performClick()
        onNodeWithText("Send password reset emails?").assertExists()
        onNodeWithText("The app sends an email to 2 accounts. Each person gets a link to choose a new password.").assertExists()
        assertEquals(emptyList(), admin.calls.filter { it.startsWith("reset") })
        onAllNodesWithText("Send password reset (2)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Emails sent: 2. Failed: 0.").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf("reset p1,p2"), admin.calls.filter { it.startsWith("reset") })
        onNodeWithText("OK").performClick()
        onNodeWithText("0 selected").assertExists()
    }

    @Test
    fun cancellingTheConfirmationSendsNothingAndKeepsTheSelection() = runComposeUiTest {
        val admin = adminWith(ann, bob)
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithText("Send password reset (1)").performClick()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        onAllNodesWithText("Send password reset emails?").assertCountEquals(0)
        onNodeWithText("1 selected").assertExists()
        assertEquals(emptyList(), admin.calls.filter { it.startsWith("reset") })
    }

    @Test
    fun selectAllShownSelectsTheShownAccountsAndClearEmptiesTheSelection() = runComposeUiTest {
        show(adminWith(ann, bob, cy))

        onNodeWithText("Select all shown (3)").performClick()
        onNodeWithText("3 selected").assertExists()
        onNodeWithText("Clear").performClick()
        onNodeWithText("0 selected").assertExists()
        onNodeWithText("Send password reset (0)").assertIsNotEnabled()
    }

    @Test
    fun selectAllShownFollowsTheSearchAndTheSendUsesOnlyTheShownAccounts() = runComposeUiTest {
        val admin = adminWith(ann, bob, cy)
        show(admin)

        onNodeWithText("Select all shown (3)").performClick()
        onAllNodesWithText("Search", substring = true).onFirst().performTextInput("bob")
        waitForIdle()
        onNodeWithText("Select all shown (1)").assertExists()
        onNodeWithText("1 selected").assertExists()
        onNodeWithText("Send password reset (1)").performClick()
        onAllNodesWithText("Send password reset (1)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Emails sent: 1. Failed: 0.").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf("reset p2"), admin.calls.filter { it.startsWith("reset") })
    }

    @Test
    fun anAccountThatFailsShowsItsReasonAndTheOthersStillCount() = runComposeUiTest {
        val admin = adminWith(ann, bob, cy)
        admin.resetResults["p3"] = PasswordResetResult("p3", PasswordResetOutcome.FAILED, PasswordResetFailure.DISABLED)
        admin.resetResults["p2"] = PasswordResetResult("p2", PasswordResetOutcome.FAILED, PasswordResetFailure.EMAIL_FAILED)
        show(admin)

        onNodeWithText("Select all shown (3)").performClick()
        onNodeWithText("Send password reset (3)").performClick()
        onAllNodesWithText("Send password reset (3)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Emails sent: 1. Failed: 2.").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("• cy@example.test: The account is disabled.").assertExists()
        onNodeWithText("• bob@example.test: The email could not be sent.").assertExists()
        onAllNodesWithText("• ann@example.test", substring = true).assertCountEquals(0)
    }

    @Test
    fun anEditorSeesNoSelectionAndNoSendButton() = runComposeUiTest {
        show(adminWith(ann, bob, role = GlobalUserRole.EDITOR))

        onAllNodesWithText("Select all shown", substring = true).assertCountEquals(0)
        onAllNodesWithContentDescription("ann@example.test").assertCountEquals(0)
        onAllNodesWithText("Send password reset", substring = true).assertCountEquals(0)
    }

    @Test
    fun theSendButtonIsOnlyOnWhenSomeoneIsSelected() = runComposeUiTest {
        show(adminWith(ann))

        onNodeWithText("Send password reset (0)").assertIsNotEnabled()
        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithText("Send password reset (1)").assertIsEnabled()
    }
}
