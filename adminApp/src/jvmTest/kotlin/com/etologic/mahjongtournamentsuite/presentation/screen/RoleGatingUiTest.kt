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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.FunctionsBackendApi
import com.etologic.mahjongtournamentsuite.data.network.ApiConfiguration
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.UsersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.VersionPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import io.ktor.client.HttpClient
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each role sees and can do on the screens. The server enforces the same rules, see `rolePermissions.integration.test.ts`. */
@OptIn(ExperimentalTestApi::class)
class RoleGatingUiTest {
    private val boss = ManagedUser("a1", "boss@example.test", "Boss", GlobalUserRole.ADMIN, false, emptyList())
    private val ed = ManagedUser("e1", "ed@example.test", "Ed", GlobalUserRole.EDITOR, false, emptyList())

    private class World(role: GlobalUserRole, users: List<ManagedUser>) {
        val admin = FakeAdminRepository(role, users)
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
        }
        val store = AppMemoryStore()
    }

    private fun ComposeUiTest.show(world: World, players: List<Player> = emptyList(), content: @Composable () -> Unit) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false) {}
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.server }
                            single<AdminRepository> { world.admin }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository(players) }
                            single { world.store }
                            // The version call fails at once and the screen ignores it.
                            single { FunctionsBackendApi(HttpClient(), ApiConfiguration("http://127.0.0.1:1/")) }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { VersionPresenter(get(), get()) }
                            factory { UsersPresenter(get(), get(), get(), get()) }
                            factory { PlayerBasePresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { content() }
                }
            }
        }
        waitForIdle()
    }

    /** The screens load in the background. Wait until the text is there. */
    private fun ComposeUiTest.awaitText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    // ---------------------------------------------------------------- tournament list

    @Test
    fun adminSeesCreateEditAndUserManagementOnTheTournamentList() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, emptyList())) { TournamentsScreen(rememberNavController()) }

        onNodeWithText("New tournament").assertExists()
        onAllNodesWithContentDescription("Edit Open").assertCountEquals(1)
        onNodeWithText("App Users").assertExists()
        onNodeWithText("EMA players").assertExists()
        onAllNodesWithText("Admin", substring = true).assertCountEquals(1)
    }

    @Test
    fun editorCannotCreateOrEditTournaments() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, emptyList())) { TournamentsScreen(rememberNavController()) }

        onAllNodesWithText("New tournament").assertCountEquals(0)
        onAllNodesWithContentDescription("Edit Open").assertCountEquals(0)
        onNodeWithText("App Users").assertExists()
        onNodeWithText("EMA players").assertExists()
        onAllNodesWithText("Editor", substring = true).assertCountEquals(1)
    }

    @Test
    fun theStoreKeepsTheRoleThatTheServerReported() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, emptyList())
        show(world) { TournamentsScreen(rememberNavController()) }

        assertTrue(world.store.adminStatus.value == AdminStatus("me", GlobalUserRole.EDITOR))
    }

    // ---------------------------------------------------------------- shared EMA players

    private val player = Player(emaId = "10001", firstName = "Ada", lastName = "Lovelace", country = "GB")

    @Test
    fun adminCanAddEmaPlayers() = runComposeUiTest {
        val world = World(GlobalUserRole.ADMIN, emptyList()).also { it.store.adminStatus.value = AdminStatus("me", GlobalUserRole.ADMIN) }
        show(world, listOf(player)) { PlayerBaseScreen(rememberNavController()) }

        onNodeWithText("New player").assertExists()
    }

    @Test
    fun editorCanReadButNotAddEmaPlayers() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, emptyList()).also { it.store.adminStatus.value = AdminStatus("me", GlobalUserRole.EDITOR) }
        show(world, listOf(player)) { PlayerBaseScreen(rememberNavController()) }

        onAllNodesWithText("New player").assertCountEquals(0)
        awaitText("10001")
        onAllNodesWithText("10001", substring = true).onFirst().assertExists()
    }

    @Test
    fun editorSeesNoSaveButtonForASelectedEmaPlayer() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, emptyList()).also { it.store.adminStatus.value = AdminStatus("me", GlobalUserRole.EDITOR) }
        show(world, listOf(player)) { PlayerBaseScreen(rememberNavController()) }

        awaitText("10001")
        onAllNodesWithText("10001", substring = true).onFirst().performClick()
        waitForIdle()

        onAllNodesWithText("Save").assertCountEquals(0)
    }

    // ---------------------------------------------------------------- accounts

    @Test
    fun adminCanOpenAdminAndEditorAccounts() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }

        onNodeWithText("New user").assertExists()
        awaitText("boss@example.test")
        onNodeWithText("boss@example.test").performClick()
        waitForIdle()
        onNodeWithText("Save").assertExists()
    }

    @Test
    fun adminCanDisableAnAccountFromItsDialog() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }

        awaitText("ed@example.test")
        onNodeWithText("ed@example.test").performClick()
        waitForIdle()

        onNodeWithText("Disable account").assertExists()
    }

    @Test
    fun editorCanOpenAnotherEditorButNotDisableIt() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }

        awaitText("ed@example.test")
        onNodeWithText("ed@example.test").performClick()
        waitForIdle()

        onNodeWithText("Save").assertExists()
        onAllNodesWithText("Disable account").assertCountEquals(0)
        onNodeWithText("Send password-reset email").assertExists()
    }

    @Test
    fun editorCannotOpenAnAdminAccount() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }

        awaitText("boss@example.test")
        onNodeWithText("boss@example.test").performClick()
        waitForIdle()

        onAllNodesWithText("Save").assertCountEquals(0)
    }

    @Test
    fun editorCanStartCreatingAnAccount() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }

        onNodeWithText("New user").performClick()
        waitForIdle()

        onNodeWithText("Confirm email").assertExists()
    }

    @Test
    fun theRoleChoiceOfAnEditorIsLockedWhileAdminsCanChangeIt() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }
        awaitText("ed@example.test")
        onNodeWithText("ed@example.test").performClick()
        waitForIdle()
        onNodeWithText("Global role").assertIsEnabled()
    }

    @Test
    fun anEditorCannotChangeTheRoleOfAnAccount() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(boss, ed))) { GlobalUsersScreen(rememberNavController()) }
        awaitText("ed@example.test")
        onNodeWithText("ed@example.test").performClick()
        waitForIdle()

        onNodeWithText("Global role").assertIsNotEnabled()
    }
}
