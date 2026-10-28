package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountActionResult
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerAccountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailFailure
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailItem
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
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

/** The account column and the menu of each player: invite when there is no account, edit the account (admins) when there is one. */
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

    private fun ComposeUiTest.show(world: World, waitForMenus: Boolean = true) {
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
                CompositionLocalProvider(LocalThemeController provides themeController) {
                    MtsTheme(useDarkTheme = false) { PlayersScreen(rememberNavController(), "t1") }
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
    fun theAccountColumnShowsACheckOrACrossAndNoText() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(withAccount, withoutAccount)))

        onAllNodesWithContentDescription("Has account").assertCountEquals(1)
        onAllNodesWithContentDescription("No account").assertCountEquals(1)
        onAllNodesWithText("Has account").assertCountEquals(0)
        onAllNodesWithText("No account").assertCountEquals(0)
    }

    @Test
    fun aPlayerWithNoAccountOffersTheInviteOptionAndNotTheEditOption() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(withAccount, withoutAccount)))

        openMenu(1)

        onAllNodesWithText("Invite").assertCountEquals(1)
        onAllNodesWithText("Edit account").assertCountEquals(0)
    }

    @Test
    fun aPlayerWithAnAccountOffersSendReminderToAnAdmin() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(withAccount)))

        openMenu(0)

        onAllNodesWithText("Remind").assertCountEquals(1)
    }

    @Test
    fun aPlayerWithAnAccountOffersSendReminderToAnEditorToo() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(withAccount)))

        openMenu(0)

        onAllNodesWithText("Remind").assertCountEquals(1)
    }

    @Test
    fun sendReminderSendsToThePlayerAndShowsTheResult() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, listOf(withAccount))
        world.accounts.results[1] = PlayerAccountActionResult(1, PlayerAccountOutcome.REMINDED)
        show(world)

        openMenu(0)
        onNodeWithText("Remind").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("The reminder was sent.").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf(listOf(1)), world.accounts.sent)
    }

    @Test
    fun aPlayerWithNoAccountOffersNoReminder() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(withoutAccount)))

        openMenu(0)

        onAllNodesWithText("Remind").assertCountEquals(0)
    }

    @Test
    fun anAdminSeesTheEditOptionForAPlayerWithAnAccountAndNotTheInviteOption() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(withAccount, withoutAccount)))

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
    fun anEditorCanInviteAPlayerWithNoAccount() = runComposeUiTest {
        show(World(GlobalUserRole.EDITOR, listOf(withAccount, withoutAccount)))

        openMenu(1)

        onAllNodesWithText("Invite").assertCountEquals(1)
    }

    @Test
    fun anEmptySlotHasNoAccountOptionAtAll() = runComposeUiTest {
        show(World(GlobalUserRole.ADMIN, listOf(empty)), waitForMenus = false)

        onAllNodesWithContentDescription("Player actions").assertCountEquals(0)
        onAllNodesWithText("Invite").assertCountEquals(0)
        onAllNodesWithText("Edit account").assertCountEquals(0)
    }

    private val noEmail = TournamentPlayer(id = 4, name = "P4", team = 0, assignedEmaId = "44", hasEmail = false)

    @Test
    fun invitingAPlayerWithNoEmailAsksForTheEmailThenSavesItAndInvites() = runComposeUiTest {
        val world = World(GlobalUserRole.EDITOR, listOf(noEmail))
        show(world)

        openMenu(0)
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Invite").assertIsNotEnabled()
        onNodeWithText("Email").performTextInput("p4@example.test")
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("The account was created and the email to choose a password was sent.").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(listOf(listOf(PlayerEmailItem(4, "p4@example.test"))), world.accounts.savedEmails)
        assertEquals(listOf(listOf(4)), world.accounts.sent)
    }

    @Test
    fun aRefusedEmailShowsTheReasonAndSendsNothing() = runComposeUiTest {
        val world = World(GlobalUserRole.ADMIN, listOf(noEmail))
        world.accounts.emailResults[4] = PlayerEmailResult(4, PlayerEmailOutcome.FAILED, PlayerEmailFailure.EMAIL_IN_USE)
        show(world)

        openMenu(0)
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Email").performTextInput("taken@example.test")
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Another account or another player already uses this email.").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(emptyList(), world.accounts.sent)
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
        val world = World(GlobalUserRole.ADMIN, listOf(saved))
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
        show(World(GlobalUserRole.ADMIN, listOf(withAccount)))

        openMenu(0)

        onAllNodesWithText("Edit email").assertCountEquals(0)
    }

    @Test
    fun invitingSendsTheOnePlayerAndShowsTheResult() = runComposeUiTest {
        val world = World(GlobalUserRole.ADMIN, listOf(withAccount, withoutAccount))
        show(world)

        openMenu(1)
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("The account was created and the email to choose a password was sent.").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(listOf(listOf(2)), world.accounts.sent)
    }

    @Test
    fun aFailedInviteShowsTheReason() = runComposeUiTest {
        val world = World(GlobalUserRole.ADMIN, listOf(withoutAccount))
        world.accounts.results[2] = PlayerAccountActionResult(2, PlayerAccountOutcome.FAILED, PlayerAccountFailure.EMAIL_IN_USE)
        show(world)

        openMenu(0)
        onNodeWithText("Invite").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Another account or another player already uses this email.").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class NoImportRepository : PlayerImportRepository {
    override suspend fun export(tournamentId: String) = error("Not used.")
    override suspend fun preview(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
    override suspend fun apply(tournamentId: String, fileName: String, bytes: ByteArray) = error("Not used.")
}
