package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.platform.LocalPickedSpreadsheetForTests
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerImportPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayersPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
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

/**
 * The menu of each player. Invitations and reminders go out from the Invitations dialog only, so the menu offers the edit
 * of the email when there is no account, and the edit of the account (admins) when there is one.
 */
@OptIn(ExperimentalTestApi::class)
class PlayersScreenAccountsUiTest {
    private val withAccount = TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = "11", accountUid = "u1", hasAccount = true, hasEmail = true)
    private val withoutAccount = TournamentPlayer(id = 2, name = "P2", team = 0, assignedEmaId = "22", hasEmail = true)
    private val empty = TournamentPlayer(id = 3, name = "P3", team = 0)

    private class World(role: GlobalUserRole, players: List<TournamentPlayer>) {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
            this.players = players
        }
        val admin = FakeAdminRepository(role)
        val accounts = FakePlayerAccountRepository()
        val store = AppMemoryStore()
    }

    private fun ComposeUiTest.show(world: World, waitForMenus: Boolean = true, openedUsers: MutableList<String?>? = null) {
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
                            single<PlayerRepository> { FakePlayerRepository() }
                            single<PlayerAccountRepository> { world.accounts }
                            single { world.store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            single<PlayerImportRepository> { NoImportRepository() }
                            factory { PlayerImportPresenter(get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                // The file chooser of the import is cancelled.
                CompositionLocalProvider(LocalThemeController provides themeController, LocalPickedSpreadsheetForTests provides { null }) {
                    MtsTheme(useDarkTheme = false) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = "players") {
                            composable("players") { PlayersScreen(navController, "t1") }
                            composable<UsersRoute> { entry -> openedUsers?.add(entry.toRoute<UsersRoute>().editUid) }
                        }
                    }
                }
            }
        }
        if (waitForMenus) {
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Player actions").fetchSemanticsNodes().isNotEmpty() }
        } else {
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /** Opens the menu of the row at [index]. The rows are in the order of the player number. */
    private fun ComposeUiTest.openMenu(index: Int) {
        onAllNodesWithContentDescription("Player actions")[index].performClick()
        waitForIdle()
    }

    @Test
    fun theRowsShowNoAccountStateAndTheMenuSendsNoEmails() = runComposeUiTest {
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(withAccount, withoutAccount)))

        onAllNodesWithContentDescription("Has account").assertCountEquals(0)
        onAllNodesWithContentDescription("No account").assertCountEquals(0)
        openMenu(0)
        onAllNodesWithText("Remind").assertCountEquals(0)
        onAllNodesWithText("Invite").assertCountEquals(0)
    }

    @Test
    fun aPlayerWithNoAccountOffersEditEmailButNoInvite() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(withoutAccount)))

        openMenu(0)

        onAllNodesWithText("Edit email").assertCountEquals(1)
        onAllNodesWithText("Invite").assertCountEquals(0)
    }

    @Test
    fun anAdminSeesTheEditOptionForAPlayerWithAnAccountAndNotTheInviteOption() = runComposeUiTest {
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(withAccount, withoutAccount)))

        openMenu(0)

        onAllNodesWithText("Edit account").assertCountEquals(1)
        onAllNodesWithText("Invite").assertCountEquals(0)
    }

    @Test
    fun anEditorSeesNeitherTheEditOptionNorTheInviteOptionForAPlayerWithAnAccount() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(withAccount, withoutAccount)))

        openMenu(0)

        onAllNodesWithText("Edit account").assertCountEquals(0)
        onAllNodesWithText("Invite").assertCountEquals(0)
    }

    @Test
    fun anEmptySlotHasNoAccountOptionAtAll() = runComposeUiTest {
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(empty)), waitForMenus = false)

        onAllNodesWithContentDescription("Player actions").assertCountEquals(0)
        onAllNodesWithText("Invite").assertCountEquals(0)
        onAllNodesWithText("Edit account").assertCountEquals(0)
    }

    @Test
    fun editEmailIsOfferedToAnEditorAndSavesWithoutInviting() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, listOf(withoutAccount))
        show(world)

        openMenu(0)
        onNodeWithText("Edit email").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("The app does not show saved emails to editors. The saved email stays until you save a new one.").assertExists()
        onNodeWithText("Email").performTextInput("new@example.test")
        onNodeWithText("Save").performClick()
        waitForIdle()

        assertEquals(listOf(listOf(PlayerEmailItem(2, "new@example.test"))), world.accounts.savedEmails)
        assertEquals(emptyList(), world.accounts.sent)
    }

    @Test
    fun anAdminSeesTheSavedEmailInTheDialogAndCanRemoveIt() = runComposeUiTest {
        val saved = withoutAccount.copy(email = "saved@example.test")
        val world = World(GlobalUserRole.SUPER_ADMIN, listOf(saved))
        show(world)

        openMenu(0)
        onNodeWithText("Edit email").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("saved@example.test", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("The app does not show saved emails", substring = true).assertCountEquals(0)
        onNodeWithText("Remove email").performClick()
        waitForIdle()

        assertEquals(listOf(listOf(PlayerEmailItem(2, ""))), world.accounts.savedEmails)
    }

    @Test
    fun aPlayerWithAnAccountOffersNoEditEmail() = runComposeUiTest {
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(withAccount)))

        openMenu(0)

        onAllNodesWithText("Edit email").assertCountEquals(0)
    }

    @Test
    fun editAccountOpensTheAccountInTheUsers() = runComposeUiTest {
        val opened = mutableListOf<String?>()
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(withAccount, withoutAccount)), openedUsers = opened)

        openMenu(0)
        onNodeWithText("Edit account").performClick()
        waitUntil(timeoutMillis = 5_000) { opened.isNotEmpty() }

        assertEquals(listOf<String?>("u1"), opened)
    }

    @Test
    fun aCancelledImportLeavesThePlayers() = runComposeUiTest {
        show(World(GlobalUserRole.SUPER_ADMIN, listOf(withAccount, withoutAccount)))

        onAllNodes(hasText("Import") and hasClickAction()).onFirst().performClick()
        waitForIdle()

        onAllNodesWithContentDescription("Player actions").assertCountEquals(2)
    }
}

internal class NoImportRepository : PlayerImportRepository {
    override suspend fun export(tournamentId: String) = error("Not used.")
    override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
    override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
}
