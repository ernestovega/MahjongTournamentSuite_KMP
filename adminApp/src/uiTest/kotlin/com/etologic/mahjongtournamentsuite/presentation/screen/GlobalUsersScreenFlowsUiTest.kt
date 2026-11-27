package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequest
import com.etologic.mahjongtournamentsuite.domain.model.AccountRequestKind
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.ManagedUser
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.RolesCatalog
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentAssignment
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** App users: create, change, disable, enable and delete an account, the password reset, the requests of club admins and the errors. */
@OptIn(ExperimentalTestApi::class)
class GlobalUsersScreenFlowsUiTest {
    private val ann = ManagedUser("p1", "ann@example.test", "Ann", GlobalUserRole.PLAYER, false, emptyList(), emaId = "11")
    private val cy = ManagedUser("p3", "cy@example.test", "Cy", GlobalUserRole.PLAYER, true, emptyList(), emaId = "33")
    private val ed = ManagedUser(
        uid = "e1",
        email = "ed@example.test",
        alias = "Ed",
        role = GlobalUserRole.EDITOR,
        disabled = false,
        tournamentAssignments = listOf(TournamentAssignment("t1", "Open")),
        emaId = "44",
        canManage = true,
    )
    private val me = ManagedUser("me", "me@example.test", "Me", GlobalUserRole.SUPER_ADMIN, false, emptyList())

    private class World(
        role: GlobalUserRole = GlobalUserRole.SUPER_ADMIN,
        users: List<ManagedUser>,
        val listFails: Boolean = false,
        /** Every read of the screen fails, also the own access when [statusFails]. */
        val everythingFails: Boolean = false,
        val statusFails: Boolean = false,
    ) {
        val admin = FakeAdminRepository(role, users)
        val resets = mutableListOf<String>()
        var resetFails = false
        val repository: AdminRepository = object : AdminRepository by admin {
            override suspend fun listUsers(refreshMode: RefreshMode) = if (listFails || everythingFails) AppResult.Failure(AppError.Network) else admin.listUsers(refreshMode)
            override suspend fun whoAmI(refreshMode: RefreshMode): AppResult<AdminStatus> = if (statusFails) AppResult.Failure(AppError.Network) else admin.whoAmI(refreshMode)
            override suspend fun listOrganizations(): AppResult<List<Organization>> = if (everythingFails) AppResult.Failure(AppError.Network) else admin.listOrganizations()
            override suspend fun listAccountRequests(): AppResult<List<AccountRequest>> = if (everythingFails) AppResult.Failure(AppError.Network) else admin.listAccountRequests()
            override suspend fun listRoles(): AppResult<RolesCatalog> = AppResult.Success(
                RolesCatalog(
                    standard = GlobalUserRole.entries.filter { it != GlobalUserRole.PLAYER }.associateWith { Permission.defaultsOf(it) },
                    custom = emptyList(),
                    grantable = mapOf(GlobalUserRole.EDITOR to Permission.defaultsOf(GlobalUserRole.EDITOR) + Permission.TOURNAMENT_RESET_TABLE),
                    required = setOf(Permission.TOURNAMENT_VIEW),
                ),
            )
        }
        val auth: AuthRepository = object : AuthRepository by FakeSignedInAuthRepository() {
            override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
                resets += email
                return if (resetFails) AppResult.Failure(AppError.Network) else AppResult.Success(Unit)
            }
        }
    }

    private fun world(vararg users: ManagedUser, role: GlobalUserRole = GlobalUserRole.SUPER_ADMIN) = World(role, users.toList())

    private fun ComposeUiTest.show(world: World, waitFor: String? = "me@example.test") {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> {
                                val server = FakeTournamentRepository().apply {
                                    tournaments = listOf(Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1))
                                }
                                if (!world.everythingFails) {
                                    server
                                } else {
                                    object : TournamentRepository by server {
                                        override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = AppResult.Failure(AppError.Network)
                                    }
                                }
                            }
                            single<AdminRepository> { world.repository }
                            single<AuthRepository> { world.auth }
                            single<PlayerRepository> {
                                if (!world.everythingFails) {
                                    FakePlayerRepository()
                                } else {
                                    object : PlayerRepository by FakePlayerRepository() {
                                        override suspend fun listPlayers(refreshMode: RefreshMode): AppResult<List<Player>> = AppResult.Failure(AppError.Network)
                                    }
                                }
                            }
                            single { AppMemoryStore() }
                            factory { UsersPresenter(get(), get(), get(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalPressTimeSource provides pressClock) {
                    MtsTheme(useDarkTheme = false) { GlobalUsersScreen(rememberNavController()) }
                }
            }
        }
        if (waitFor != null) awaitText(waitFor)
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and hasText(text))
    private fun ComposeUiTest.click(text: String) {
        button(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.open(email: String) {
        onNodeWithText(email).performClick()
        waitForIdle()
        onNodeWithText("Edit account").assertExists()
    }

    private fun ComposeUiTest.lastButton(text: String) {
        onAllNodes(hasClickAction() and hasText(text)).onLast().performClick()
        waitForIdle()
    }

    @Test
    fun aNewPlayerAccountIsCreatedAndTheResetEmailIsAnnounced() = runComposeUiTest {
        val world = world(me, ann)
        show(world)
        click("New user")
        onNode(hasSetTextAction() and hasText("Email")).performTextInput("new@example.test")
        onNode(hasSetTextAction() and hasText("Confirm email")).performTextInput("new@example.test")
        onNodeWithText("Global role").performClick()
        waitForIdle()
        onAllNodesWithText("Player").onLast().performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("EMA ID")).performTextInput("55")
        waitForIdle()
        click("Save and Send password-reset email")

        awaitText("Account created. Firebase sent a password-reset email to new@example.test.")
        assertEquals(listOf("create PLAYER "), world.admin.calls)
        click("OK")
        onNodeWithText("new@example.test").assertExists()
        assertEquals(0, count("Add account"))
    }

    @Test
    fun aRefusedCreationShowsTheErrorAndKeepsTheForm() = runComposeUiTest {
        val world = world(me, ann)
        world.admin.failWrites = true
        show(world)
        click("New user")
        onNode(hasSetTextAction() and hasText("Email")).performTextInput("new@example.test")
        onNode(hasSetTextAction() and hasText("Confirm email")).performTextInput("new@example.test")
        onNodeWithText("Global role").performClick()
        waitForIdle()
        onAllNodesWithText("Player").onLast().performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("EMA ID")).performTextInput("55")
        waitForIdle()
        click("Save and Send password-reset email")
        awaitText("Network request failed.")
        click("OK")
        onNodeWithText("Add account").assertExists()
    }

    @Test
    fun aChangedAccountIsSavedAndTheDialogCloses() = runComposeUiTest {
        val world = world(me, ann)
        show(world)
        open("ann@example.test")
        onNode(hasSetTextAction() and hasText("Alias")).performTextInput(" B")
        waitForIdle()
        click("Save")
        waitUntil(timeoutMillis = 5_000) { "update p1" in world.admin.calls }
        waitForIdle()
        assertEquals(0, count("Edit account"))
    }

    @Test
    fun disablingAsksFirstAndThenSaysSo() = runComposeUiTest {
        val world = world(me, ann)
        show(world)
        open("ann@example.test")
        click("Disable account")
        onNodeWithText("Disable account?").assertExists()
        click("Disable")
        awaitText("ann@example.test is disabled.")
        assertEquals(listOf("disable p1 true"), world.admin.calls)
    }

    @Test
    fun aDisabledAccountIsEnabledAtOnce() = runComposeUiTest {
        val world = world(me, cy)
        show(world)
        open("cy@example.test")
        click("Enable account")
        awaitText("cy@example.test is enabled.")
        assertEquals(listOf("disable p3 false"), world.admin.calls)
    }

    @Test
    fun aRefusedDisableShowsTheError() = runComposeUiTest {
        val world = world(me, ann)
        world.admin.failWrites = true
        show(world)
        open("ann@example.test")
        click("Disable account")
        click("Disable")
        awaitText("Network request failed.")
    }

    @Test
    fun deletingOneAccountAsksFirstAndTakesItOutOfTheList() = runComposeUiTest {
        val world = world(me, ann)
        show(world)
        open("ann@example.test")
        click("Delete account")
        onNodeWithText("Delete account?").assertExists()
        lastButton("Delete")
        awaitText("ann@example.test is deleted.")
        assertEquals(listOf("delete p1"), world.admin.calls)
        click("OK")
        assertEquals(0, count("ann@example.test"))
    }

    @Test
    fun aRefusedDeleteShowsTheError() = runComposeUiTest {
        val world = world(me, ann)
        world.admin.failWrites = true
        show(world)
        open("ann@example.test")
        click("Delete account")
        lastButton("Delete")
        awaitText("Network request failed.")
        assertTrue(count("ann@example.test") > 0)
    }

    @Test
    fun thePasswordResetOfOneAccountIsSentFromItsDialog() = runComposeUiTest {
        val world = world(me, ann)
        show(world)
        open("ann@example.test")
        click("Send password-reset email")
        awaitText("Password-reset email sent to ann@example.test.")
        assertEquals(listOf("ann@example.test"), world.resets)
    }

    @Test
    fun aFailedPasswordResetShowsTheError() = runComposeUiTest {
        val world = world(me, ann).also { it.resetFails = true }
        show(world)
        open("ann@example.test")
        click("Send password-reset email")
        awaitText("Network request failed.")
    }

    @Test
    fun aRequestOfAClubAdminIsApprovedOrRejected() = runComposeUiTest {
        val world = world(me, ann, cy)
        world.admin.accountRequests = listOf(
            AccountRequest("r1", AccountRequestKind.DELETE, "p1", "ann@example.test", "Left", "club@example.test", 0),
            AccountRequest("r2", AccountRequestKind.DISABLE, "p3", "cy@example.test", "Gone", "club@example.test", 0),
        )
        show(world)
        awaitText("club@example.test asks to delete ann@example.test.")
        onNodeWithText("club@example.test asks to disable cy@example.test.").assertExists()

        onAllNodes(hasClickAction() and hasText("Reject"))[1].performClick()
        waitForIdle()
        awaitText("The request was rejected.")
        click("OK")

        click("Approve")
        onNodeWithText("Delete account?").assertExists()
        lastButton("Delete")
        awaitText("ann@example.test is deleted.")
        assertEquals(listOf("resolve r2 false", "resolve r1 true"), world.admin.calls)
    }

    @Test
    fun anApprovedDisableRequestSaysTheAccountIsDisabled() = runComposeUiTest {
        val world = world(me, cy)
        world.admin.accountRequests = listOf(AccountRequest("r2", AccountRequestKind.DISABLE, "p3", "cy@example.test", "Gone", "club@example.test", 0))
        show(world)
        awaitText("asks to disable")
        click("Approve")
        onNodeWithText("Disable account?").assertExists()
        click("Disable")
        awaitText("cy@example.test is disabled.")
    }

    @Test
    fun aFailedLoadOfTheAccountsShowsTheError() = runComposeUiTest {
        show(World(users = listOf(me, ann), listFails = true), waitFor = null)
        awaitText("Network request failed.")
        click("OK")
        assertEquals(0, count("Network request failed."))
    }

    @Test
    fun theRoleFilterShowsOnlyTheAccountsOfTheChosenRoles() = runComposeUiTest {
        show(world(me, ann, ed))
        awaitText("ed@example.test")
        // The role buttons come above the list, so the first "Editor" is the button.
        onAllNodes(hasClickAction() and hasText("Editor"))[0].performClick()
        waitForIdle()
        assertEquals(0, count("ann@example.test"))
        onNodeWithText("ed@example.test").assertExists()
    }

    @Test
    fun whenNothingLoadsTheFirstErrorShows() = runComposeUiTest {
        show(World(users = listOf(me), everythingFails = true), waitFor = "Network request failed.")
    }

    @Test
    fun withoutTheOwnAccessTheScreenSaysSo() = runComposeUiTest {
        show(World(users = listOf(me), everythingFails = true, statusFails = true), waitFor = "Network request failed.")
    }

    private val pressClock = TestTimeSource()

    /** A later press on the clock of the buttons, scrolled into the dialog first. */
    private fun ComposeUiTest.press(text: String, last: Boolean = false) {
        pressClock += 1.seconds
        val nodes = onAllNodes(hasClickAction() and hasText(text) and !hasSetTextAction())
        val node = if (last) nodes.onLast() else nodes[0]
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    @Test
    fun thePermissionsOfAnEditorSaveAsANewRole() = runComposeUiTest {
        val world = world(me, ed)
        show(world)
        open("ed@example.test")
        press("Permissions")
        awaitText("ID cards")
        press("ID cards")
        press("Save as a new role")
        awaitText("Ed")
        press("Save", last = true)
        awaitText("The permissions of Ed are saved.")
        assertEquals(listOf("createRole Ed EDITOR", "permissions e1"), world.admin.calls)
    }

    @Test
    fun cancellingThePermissionsClosesThem() = runComposeUiTest {
        show(world(me, ed))
        open("ed@example.test")
        press("Permissions")
        awaitText("ID cards")
        press("Cancel", last = true)
        assertEquals(0, count("ID cards"))
    }

    @Test
    fun aRequestForAnAccountNotInTheListThatTheServerRefusesShowsTheError() = runComposeUiTest {
        val world = world(me, cy)
        world.admin.accountRequests = listOf(AccountRequest("r9", AccountRequestKind.DISABLE, "p9", "gone@example.test", "Gone", "club@example.test", 0))
        world.admin.failWrites = true
        show(world)
        awaitText("asks to disable gone@example.test")
        click("Approve")
        onNodeWithText("Disable account?").assertExists()
        click("Disable")
        awaitText("Network request failed.")
    }
}
