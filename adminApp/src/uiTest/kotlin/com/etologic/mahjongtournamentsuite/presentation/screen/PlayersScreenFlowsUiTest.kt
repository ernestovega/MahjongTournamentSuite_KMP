package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailOutcome
import com.etologic.mahjongtournamentsuite.domain.model.PlayerEmailResult
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.BackgroundExports
import com.etologic.mahjongtournamentsuite.presentation.components.LocalBackgroundExports
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
import kotlin.test.assertTrue

/**
 * The players of a tournament: the assignment of EMA players, the search and the country filter, the ID cards and the
 * ID list, the invitations and the email of one player, and the errors of the server.
 */
@OptIn(ExperimentalTestApi::class)
class PlayersScreenFlowsUiTest {
    private val ana = Player(emaId = "01000001", firstName = "Ana", lastName = "Lopez", country = "ES")
    private val ben = Player(emaId = "02000002", firstName = "Ben", lastName = "Smith", country = "GB")
    private val cai = Player(emaId = "03000003", firstName = "Cai", lastName = "Wong", country = "CN")

    private val slotAna = TournamentPlayer(id = 1, name = "P1", team = 0, assignedEmaId = ana.emaId, hasEmail = true)
    private val slotBen = TournamentPlayer(id = 2, name = "P2", team = 0, assignedEmaId = ben.emaId)
    private val emptySlot = TournamentPlayer(id = 3, name = "P3", team = 0)

    private class World(players: List<TournamentPlayer>, registry: List<Player>) {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(
                Tournament(id = "t1", name = "Open Madrid", shortName = "OPEN", isTeams = false, numPlayers = 4, numRounds = 1, eventStartDate = "2026-05-01"),
            )
            this.players = players
            countries = listOf(Country("ES", "Spain"), Country("GB", "United Kingdom"), Country("CN", "China"))
            writesSucceed = true
        }
        val accounts = FakePlayerAccountRepository()
        val base = FakePlayerRepository(registry)
        val store = AppMemoryStore()

        /** The loads that fail besides the knobs of the fake server: "registry" and "countries". */
        var failing: Set<String> = emptySet()
        val tournaments: TournamentRepository = object : TournamentRepository by server {
            override suspend fun listCountries(refreshMode: RefreshMode) =
                if ("countries" in failing) AppResult.Failure(AppError.Network) else server.listCountries(refreshMode)
        }
        val registryRepository: PlayerRepository = object : PlayerRepository by base {
            override suspend fun listPlayers(refreshMode: RefreshMode) =
                if ("registry" in failing) AppResult.Failure(AppError.Network) else base.listPlayers(refreshMode)
        }
        lateinit var exports: BackgroundExports
    }

    private fun world(vararg players: TournamentPlayer) = World(players.toList(), listOf(ana, ben, cai))

    private fun ComposeUiTest.show(world: World, waitForRows: Boolean = true) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val scope = rememberCoroutineScope()
            val exports = remember { BackgroundExports(scope).also { world.exports = it } }
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.tournaments }
                            single<AdminRepository> { FakeAdminRepository(GlobalUserRole.SUPER_ADMIN) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { world.registryRepository }
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
                CompositionLocalProvider(LocalThemeController provides themeController, LocalBackgroundExports provides exports) {
                    MtsTheme(useDarkTheme = false) { PlayersScreen(rememberNavController(), "t1") }
                }
            }
        }
        // The rows show once the players and the EMA registry are in.
        if (waitForRows) {
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Not assigned") or hasText("Lopez", substring = true) or hasText("Smith", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun ComposeUiTest.awaitText(text: String, contentDescription: Boolean = false) = waitUntil(timeoutMillis = 5_000) {
        (if (contentDescription) onAllNodesWithContentDescription(text) else onAllNodesWithText(text, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and (hasText(text) or hasContentDescription(text)))
    private fun ComposeUiTest.lastTextField() = onAllNodes(hasSetTextAction()).let { it[it.fetchSemanticsNodes().lastIndex] }

    /** The Send of the dialog of the missing emails, drawn over the Invitations dialog. */
    private fun ComposeUiTest.sendTheTypedEmails() {
        onAllNodes(hasClickAction() and hasText("Send (1)")).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
    }

    private fun ComposeUiTest.click(text: String) {
        button(text).performClick()
        waitForIdle()
    }

    @Test
    fun anEmptySlotGetsTheEmaPlayerChosenInTheSearch() = runComposeUiTest {
        val world = world(slotAna, emptySlot)
        show(world)
        onNodeWithText("Not assigned").assertExists()

        click("Assign")
        onNodeWithText("ASSIGN EMA PLAYER TO PLAYER 3").assertExists()
        // The search of the dialog, the last text field, finds a player by the name of its country too.
        lastTextField().performTextInput("china")
        waitForIdle()
        assertEquals(0, count("BEN SMITH"))
        onNode(hasClickAction() and hasText(cai.emaId, substring = true)).performClick()

        waitUntil(timeoutMillis = 5_000) { world.server.writes.isNotEmpty() }
        assertEquals(listOf("assign 3 03000003"), world.server.writes)
    }

    @Test
    fun aRefusedAssignmentShowsTheError() = runComposeUiTest {
        val world = world(emptySlot)
        world.server.writesSucceed = false
        show(world)
        click("Assign")
        onNode(hasClickAction() and hasText(ana.emaId, substring = true)).performClick()
        awaitText("Error")
        click("OK")
        assertEquals(0, count("Error"))
    }

    @Test
    fun aFailedLoadShowsTheErrorOfTheServer() = runComposeUiTest {
        val world = world(slotAna)
        world.server.failPlayers = true
        show(world, waitForRows = false)
        awaitText("Network request failed.")
        click("OK")
        assertEquals(0, count("Network request failed."))
    }

    @Test
    fun whenEveryLoadFailsTheFirstErrorShows() = runComposeUiTest {
        val world = world(slotAna)
        world.server.failPlayers = true
        world.server.failTeams = true
        world.server.failTableLists = setOf(null)
        world.failing = setOf("registry", "countries")
        show(world, waitForRows = false)
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedLoadOfTheRegistryShowsItsError() = runComposeUiTest {
        val world = world(slotAna)
        world.failing = setOf("registry", "countries")
        show(world, waitForRows = false)
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedLoadOfTheCountriesShowsItsError() = runComposeUiTest {
        val world = world(slotAna)
        world.failing = setOf("countries")
        world.server.failTeams = true
        show(world, waitForRows = false)
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedLoadOfTheTeamsOrTheTablesShowsItsError() = runComposeUiTest {
        val world = world(slotAna)
        world.server.failTeams = true
        world.server.failTableLists = setOf(null)
        show(world, waitForRows = false)
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedLoadOfTheTablesShowsItsError() = runComposeUiTest {
        val world = world(slotAna)
        world.server.failTableLists = setOf(null)
        show(world, waitForRows = false)
        awaitText("Network request failed.")
    }

    @Test
    fun theSearchFiltersTheRowsAndItsButtonClearsIt() = runComposeUiTest {
        show(world(slotAna, slotBen))
        onAllNodes(hasSetTextAction())[0].performTextInput("nobody")
        waitForIdle()
        onNodeWithText("No tournament players match the current filters.").assertExists()
        click("Clear search")
        onNodeWithText("Ana Lopez").assertExists()
        onNodeWithText("Ben Smith").assertExists()
    }

    @Test
    fun theCountryButtonsShowOnlyThePlayersOfTheChosenCountries() = runComposeUiTest {
        show(world(slotAna, slotBen))
        click("ES (1)")
        onNodeWithText("Ana Lopez").assertExists()
        assertEquals(0, count("Ben Smith"))
        click("GB (1)")
        onNodeWithText("Ben Smith").assertExists()
        click("ES (1)")
        click("GB (1)")
        onNodeWithText("Ana Lopez").assertExists()
        onNodeWithText("Ben Smith").assertExists()
    }

    @Test
    fun theIdCardsAndTheIdListNeedEverySlotAssigned() = runComposeUiTest {
        val world = world(slotAna, emptySlot)
        show(world)
        click("ID cards")
        click("Create PDF")
        onNodeWithText("Assign an EMA player to every tournament slot before creating ID cards.").assertExists()
        click("OK")
        click("ID list")
        onNodeWithText("Assign an EMA player to every tournament slot before creating the ID list.").assertExists()
        assertTrue(world.exports.tasks.isEmpty())
    }

    @Test
    fun theIdCardsAndTheIdListBecomeFilesNamedAfterTheTournament() = runComposeUiTest {
        val world = world(slotAna, slotBen)
        show(world)
        click("ID cards")
        waitForIdle()
        click("Create PDF")
        click("ID list")
        waitUntil(timeoutMillis = 5_000) { world.exports.tasks.size == 2 && world.exports.tasks.all { it.status is BackgroundExports.Status.Ready } }
        val files = world.exports.tasks.map { (it.status as BackgroundExports.Status.Ready).file }
        assertTrue(files[0].fileName.contains("id-cards") && files[0].fileName.endsWith(".pdf"), files[0].fileName)
        assertTrue(files[0].fileName.contains("OPEN") && files[0].fileName.contains("2026"), files[0].fileName)
        assertEquals("application/pdf", files[0].mimeType)
        assertTrue(files[1].fileName.contains("id-list") && files[1].fileName.endsWith(".xlsx"), files[1].fileName)
    }

    @Test
    fun theInvitationsAskForTheMissingEmailsSaveThemAndSend() = runComposeUiTest {
        val world = world(slotAna, slotBen)
        show(world)
        click("Invitations")
        onNodeWithText("Invitations and reminders").assertExists()
        click("Select all shown (2)")
        click("Send (2)")
        onNodeWithText("Emails are missing").assertExists()
        onNode(hasSetTextAction() and hasText("2 · BEN SMITH")).performTextInput("ben@example.test")
        waitForIdle()
        sendTheTypedEmails()

        waitUntil(timeoutMillis = 5_000) { world.accounts.sent.isNotEmpty() }
        assertEquals(listOf(2), world.accounts.savedEmails.single().map { it.playerId })
        assertEquals(listOf(listOf(1, 2)), world.accounts.sent)
    }

    @Test
    fun aPlayerWhoseEmailIsRefusedGetsNoInvitation() = runComposeUiTest {
        val world = world(slotAna, slotBen)
        world.accounts.emailResults[2] = PlayerEmailResult(2, PlayerEmailOutcome.FAILED)
        show(world)
        click("Invitations")
        click("Select all shown (2)")
        click("Send (2)")
        onNode(hasSetTextAction() and hasText("2 · BEN SMITH")).performTextInput("ben@example.test")
        waitForIdle()
        sendTheTypedEmails()

        waitUntil(timeoutMillis = 5_000) { world.accounts.sent.isNotEmpty() }
        assertEquals(listOf(listOf(1)), world.accounts.sent)
    }

    @Test
    fun whenOnlyARefusedEmailIsChosenNothingIsSent() = runComposeUiTest {
        val world = world(slotBen)
        world.accounts.emailResults[2] = PlayerEmailResult(2, PlayerEmailOutcome.FAILED)
        show(world)
        click("Invitations")
        click("Select all shown (1)")
        click("Send (1)")
        onNode(hasSetTextAction() and hasText("2 · BEN SMITH")).performTextInput("ben@example.test")
        waitForIdle()
        sendTheTypedEmails()

        waitUntil(timeoutMillis = 5_000) { world.accounts.savedEmails.isNotEmpty() }
        waitForIdle()
        assertEquals(emptyList(), world.accounts.sent)
    }

    @Test
    fun aFailedSendShowsTheErrorInTheInvitations() = runComposeUiTest {
        val world = world(slotAna)
        world.accounts.fails = true
        show(world)
        click("Invitations")
        click("Select all shown (1)")
        click("Send (1)")
        awaitText("Network request failed.")
        onNodeWithText("Invitations and reminders").assertExists()
        click("OK")
        assertEquals(0, count("Network request failed."))
        click("Close")
        assertEquals(0, count("Invitations and reminders"))
    }

    @Test
    fun aFailedSaveOfTheTypedEmailsShowsTheErrorAndSendsNothing() = runComposeUiTest {
        val world = world(slotBen)
        world.accounts.fails = true
        show(world)
        click("Invitations")
        click("Select all shown (1)")
        click("Send (1)")
        onNode(hasSetTextAction() and hasText("2 · BEN SMITH")).performTextInput("ben@example.test")
        waitForIdle()
        sendTheTypedEmails()
        awaitText("Network request failed.")
        assertEquals(emptyList(), world.accounts.sent)
    }

    @Test
    fun aFailedSaveOfTheEmailOfOnePlayerShowsWhatWentWrong() = runComposeUiTest {
        val world = world(slotBen)
        world.accounts.fails = true
        show(world)
        onAllNodesWithContentDescription("Player actions")[0].performClick()
        waitForIdle()
        onNodeWithText("Edit email").performClick()
        waitForIdle()
        onNodeWithText("Email of BEN SMITH").assertExists()
        onNode(hasSetTextAction() and hasText("Email")).performTextInput("ben@example.test")
        waitForIdle()
        click("Save")
        awaitText("Something went wrong")
    }

    @Test
    fun theUpAndDownArrowsMoveBetweenTheActionsOfTheRows() = runComposeUiTest {
        show(world(slotAna, emptySlot, slotBen.copy(id = 4)))
        val actions = onAllNodes(hasClickAction() and (hasText("Assign") or hasContentDescription("Player actions")))
        actions[0].requestFocus()
        waitForIdle()
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        button("Assign").assertIsFocused()
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        actions[2].assertIsFocused()
        // Left and Right stay on the action of the row.
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        actions[2].assertIsFocused()
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        button("Assign").assertIsFocused()
        onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        actions[0].assertIsFocused()
    }
}
