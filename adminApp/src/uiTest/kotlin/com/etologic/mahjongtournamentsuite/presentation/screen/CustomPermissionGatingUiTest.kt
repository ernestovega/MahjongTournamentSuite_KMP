package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerBasePresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
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

/**
 * The screens follow the permissions that the server reports, not the role: a custom role or the changes for one
 * person give an editor more than the standard role, or a club admin less. `RoleGatingUiTest` covers the standard
 * roles, and `customPermissionRoutes.integration.test.ts` checks that the server allows the same.
 */
@OptIn(ExperimentalTestApi::class)
class CustomPermissionGatingUiTest {
    private val editorDefaults = Permission.defaultsOf(GlobalUserRole.EDITOR)
    private val clubAdminDefaults = Permission.defaultsOf(GlobalUserRole.CLUB_ADMIN)
    private val player = Player(emaId = "10001", firstName = "Ada", lastName = "Lovelace", country = "GB")

    private class World(role: GlobalUserRole, permissions: Set<Permission>, tournaments: List<Tournament>) {
        val status = AdminStatus(
            "me", role,
            adminOrganizations = if (role == GlobalUserRole.CLUB_ADMIN) setOf("org-es") else emptySet(),
            permissions = permissions,
        )
        val admin = FakeAdminRepository(role, permissions = permissions)
        val server = FakeTournamentRepository().apply { this.tournaments = tournaments }
        val store = AppMemoryStore().apply { adminStatus.value = status }
        val accounts = FakePlayerAccountRepository()
    }

    private fun tournament(id: String, name: String, vararg permissions: Permission) =
        Tournament(id = id, name = name, isTeams = false, numPlayers = 8, numRounds = 1, permissions = setOf(Permission.TOURNAMENT_VIEW, *permissions))

    private fun ComposeUiTest.show(world: World, players: List<Player> = emptyList(), content: @Composable () -> Unit) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.server }
                            single<AdminRepository> { world.admin }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository(players) }
                            single<PlayerAccountRepository> { world.accounts }
                            single { world.store }
                            factory { TournamentsPresenter(get(), get(), get()) }
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

    private fun ComposeUiTest.awaitText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    // ---------------------------------------------------------------- tournament list

    @Test
    fun aClubAdminWithoutCreateTournamentsHasNoNewTournamentButton() = runComposeUiTest {
        val world = World(
            GlobalUserRole.CLUB_ADMIN,
            clubAdminDefaults - Permission.TOURNAMENTS_CREATE,
            listOf(tournament("t1", "Open", Permission.TOURNAMENT_EDIT, Permission.TOURNAMENT_CONFIGURE)),
        )
        show(world) { TournamentsScreen(rememberNavController()) }
        awaitText("Open")

        onAllNodesWithText("New tournament").assertCountEquals(0)
        onAllNodesWithContentDescription("Edit Open").assertCountEquals(1)
    }

    @Test
    fun aClubAdminWithoutConfigureOrDeleteCannotEditItsTournament() = runComposeUiTest {
        val world = World(
            GlobalUserRole.CLUB_ADMIN,
            clubAdminDefaults - Permission.TOURNAMENT_CONFIGURE - Permission.TOURNAMENT_DELETE,
            listOf(tournament("t1", "Open", Permission.TOURNAMENT_EDIT, Permission.TOURNAMENT_ROUND_CONTROL)),
        )
        show(world) { TournamentsScreen(rememberNavController()) }
        awaitText("Open")

        onNodeWithText("New tournament").assertExists()
        onAllNodesWithContentDescription("Edit Open").assertCountEquals(0)
    }

    @Test
    fun anEditorWithConfigureInOneTournamentEditsOnlyThatOne() = runComposeUiTest {
        val world = World(
            GlobalUserRole.EDITOR,
            editorDefaults + Permission.TOURNAMENT_CONFIGURE,
            listOf(
                tournament("t1", "Open", Permission.TOURNAMENT_EDIT, Permission.TOURNAMENT_CONFIGURE),
                tournament("t2", "Cup", Permission.TOURNAMENT_EDIT),
            ),
        )
        show(world) { TournamentsScreen(rememberNavController()) }
        awaitText("Cup")

        onAllNodesWithContentDescription("Edit Open").assertCountEquals(1)
        onAllNodesWithContentDescription("Edit Cup").assertCountEquals(0)
        // An editor never creates tournaments: that permission needs an organization.
        onAllNodesWithText("New tournament").assertCountEquals(0)
    }

    @Test
    fun anEditorWithOnlyTheViewHasNoAccountsScreen() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, setOf(Permission.TOURNAMENT_VIEW), listOf(tournament("t1", "Open")))
        show(world) { TournamentsScreen(rememberNavController()) }
        awaitText("Open")

        onAllNodesWithText("Users").assertCountEquals(0)
        onAllNodesWithContentDescription("Edit Open").assertCountEquals(0)
    }

    // ---------------------------------------------------------------- shared EMA players

    @Test
    fun anEditorWithCreateEmaPlayersAddsThem() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, editorDefaults + Permission.EMA_CREATE, emptyList())
        show(world, listOf(player)) { PlayerBaseScreen(rememberNavController()) }

        onNodeWithText("New player").assertExists()
    }

    @Test
    fun aClubAdminWithoutCreateEmaPlayersAddsNone() = runComposeUiTest {
        val world = World(GlobalUserRole.CLUB_ADMIN, clubAdminDefaults - Permission.EMA_CREATE, emptyList())
        show(world, listOf(player)) { PlayerBaseScreen(rememberNavController()) }
        awaitText("10001")

        onAllNodesWithText("New player").assertCountEquals(0)
    }
}
