package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.UserDeleteResult
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

/** An admin selects accounts in App users and deletes them in one go. */
@OptIn(ExperimentalTestApi::class)
class UsersDeletesUiTest {
    private val ann = ManagedUser("p1", "ann@example.test", "Ann", GlobalUserRole.PLAYER, false, emptyList(), emaId = "11")
    private val bob = ManagedUser("p2", "bob@example.test", "Bob", GlobalUserRole.PLAYER, false, emptyList(), emaId = "22")
    private val cy = ManagedUser("p3", "cy@example.test", "Cy", GlobalUserRole.PLAYER, true, emptyList(), emaId = "33")
    private val me = ManagedUser("me", "me@example.test", "Me", GlobalUserRole.SUPER_ADMIN, false, emptyList())

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

    private fun adminWith(vararg users: ManagedUser, role: GlobalUserRole = GlobalUserRole.SUPER_ADMIN) = FakeAdminRepository(role, users.toList())

    @Test
    fun anAdminSelectsAccountsConfirmsAndTheyLeaveTheList() = runComposeUiTest {
        val admin = adminWith(ann, bob, cy)
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithContentDescription("bob@example.test").performClick()
        onNodeWithText("Delete (2)").performClick()
        onNodeWithText("Delete accounts?").assertExists()
        assertEquals(emptyList(), admin.deleteRequests)
        onAllNodesWithText("Delete (2)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Deleted").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf(listOf("p1", "p2")), admin.deleteRequests)
        onNodeWithText("OK").performClick()
        waitForIdle()
        onAllNodesWithText("ann@example.test").assertCountEquals(0)
        onAllNodesWithText("bob@example.test").assertCountEquals(0)
        onNodeWithText("cy@example.test").assertExists()
        onNodeWithText("0 selected").assertExists()
    }

    @Test
    fun cancellingTheConfirmationDeletesNothing() = runComposeUiTest {
        val admin = adminWith(ann, bob)
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithText("Delete (1)").performClick()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        onAllNodesWithText("Delete accounts?").assertCountEquals(0)
        onNodeWithText("1 selected").assertExists()
        assertEquals(emptyList(), admin.deleteRequests)
    }

    @Test
    fun theOwnAccountIsNeverPartOfTheDelete() = runComposeUiTest {
        val admin = adminWith(ann, me)
        show(admin)

        onNodeWithContentDescription("me@example.test").performClick()
        onNodeWithText("Delete (0)").assertIsNotEnabled()
        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithText("2 selected").assertExists()
        onNodeWithText("Delete (1)").assertIsEnabled().performClick()
        onAllNodesWithText("Delete (1)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { admin.deleteRequests.isNotEmpty() }

        assertEquals(listOf(listOf("p1")), admin.deleteRequests)
    }

    @Test
    fun anAccountTheServerKeepsStaysInTheListWithItsReason() = runComposeUiTest {
        val admin = adminWith(ann, bob).apply {
            deleteResults["p2"] = UserDeleteResult("p2", UserDeleteOutcome.FAILED, UserDeleteFailure.LAST_SUPER_ADMIN)
        }
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithContentDescription("bob@example.test").performClick()
        onNodeWithText("Delete (2)").performClick()
        onAllNodesWithText("Delete (2)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Not deleted").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("It is the last enabled admin.").assertExists()
        onNodeWithText("Deleted").assertExists()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onAllNodesWithText("ann@example.test").assertCountEquals(0)
        onNodeWithText("bob@example.test").assertExists()
        onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun anEditorDoesNotSeeTheDelete() = runComposeUiTest {
        show(adminWith(ann, role = GlobalUserRole.EDITOR))

        onAllNodesWithText("Delete (0)").assertCountEquals(0)
    }

    @Test
    fun aDeleteThatFailsShowsTheErrorAndKeepsTheList() = runComposeUiTest {
        val admin = adminWith(ann, bob).apply { failDeletesFromRequest = 1 }
        show(admin)

        onNodeWithContentDescription("ann@example.test").performClick()
        onNodeWithText("Delete (1)").performClick()
        onAllNodesWithText("Delete (1)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("ann@example.test").assertExists()
    }
}
