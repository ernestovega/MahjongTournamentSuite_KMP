package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTeam
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerAccountRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerImportRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.GenerateTournamentScheduleBruteForceParallelUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.presenter.PlayerCountPresenter
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
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The change of the number of players (docs/player-count-change.md): the dialog from its first step to the result,
 * with a server in memory, and the two places that open it.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerCountUiTest {
    private val allPermissions = Permission.entries.toSet()
    private val individual = Tournament(id = "t1", name = "Open", isTeams = false, numPlayers = 20, numRounds = 2, permissions = allPermissions)

    private fun slots(count: Int, isTeams: Boolean = false) = (1..count).map { id ->
        TournamentPlayer(
            id = id,
            name = if (id == 6) "Player 6" else "Name $id",
            team = if (isTeams) (id - 1) / 4 + 1 else 0,
            assignedEmaId = if (id == 6) null else "1000${id.toString().padStart(4, '0')}",
        )
    }

    private class World(tournament: Tournament, players: List<TournamentPlayer>, teams: List<TournamentTeam> = emptyList(), val waiting: List<AdminRegistration> = emptyList()) {
        val server = FakeTournamentRepository().apply {
            tournaments = listOf(tournament)
            this.players = players
            this.teams = teams
            writesSucceed = true
        }
        val store = AppMemoryStore().apply { upsertTournaments(listOf(tournament)) }
        val registrations: RegistrationRepository = Proxy.newProxyInstance(
            RegistrationRepository::class.java.classLoader,
            arrayOf(RegistrationRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "loadAdminList") {
                AppResult.Success(AdminRegistrations(RegistrationSettings(), tournament.isTeams, false, registrations = waiting, teams = emptyList()))
            } else {
                AppResult.Failure(AppError.Network)
            }
        } as RegistrationRepository
    }

    /** The clock of the press threshold, moved by hand before each press: every button ignores a second press within 500 ms. */
    private val pressClock = TestTimeSource()

    private fun press() {
        pressClock += 600.milliseconds
    }

    private fun ComposeUiTest.show(world: World, role: GlobalUserRole = GlobalUserRole.SUPER_ADMIN, content: @androidx.compose.runtime.Composable () -> Unit) {
        setContent {
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.server }
                            single<AdminRepository> { FakeAdminRepository(role) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single<PlayerRepository> { FakePlayerRepository() }
                            single<PlayerAccountRepository> { FakePlayerAccountRepository() }
                            single<RegistrationRepository> { world.registrations }
                            single { world.store }
                            single<PlayerImportRepository> { NoImportRepository() }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { PlayersPresenter(get(), get(), get(), get()) }
                            factory { PlayerImportPresenter(get(), get()) }
                            factory { PlayerCountPresenter(get(), get(), get(), GenerateTournamentScheduleBruteForceParallelUseCase(), get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(
                    LocalThemeController provides ThemeController(ThemePreference.Light, false, {}),
                    LocalPressTimeSource provides pressClock,
                ) {
                    MtsTheme(useDarkTheme = false) { content() }
                }
            }
        }
    }

    private fun ComposeUiTest.showFlow(world: World, tournament: Tournament, onDismiss: () -> Unit = {}) {
        show(world) { PlayerCountFlow(tournament = tournament, onDismiss = onDismiss) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountChoose").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.click(tag: String) {
        press()
        val node = onNodeWithTag(tag)
        // Rows of the dialog scroll; its buttons do not.
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    @Test
    fun removesATableWithTheFourPeopleTheOrganizerChooses() = runComposeUiTest {
        val world = World(individual, slots(20))
        var dismissed = false
        showFlow(world, individual) { dismissed = true }

        onNodeWithText("Now: 20 players, 5 tables per round.").assertIsDisplayed()
        onNodeWithTag("playerCountTarget").assertTextEquals("20 players · 5 tables")
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()

        press()

        onNodeWithContentDescription("One table less").performClick()
        onNodeWithTag("playerCountTarget").assertTextEquals("16 players · 4 tables")
        onNodeWithText("Who leaves? Choose 4.").assertIsDisplayed()
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()

        listOf(5, 6, 7, 8).forEach { click("playerCountSlot-$it") }
        onNodeWithTag("playerCountChosen").assertTextEquals("4 of 4 chosen")
        // A fifth person cannot be chosen.
        click("playerCountSlot-9")
        onNodeWithTag("playerCountSlot-9").assertIsOff()
        onNodeWithTag("playerCountSlot-5").assertIsOn()
        onNodeWithTag("playerCountContinue").assertIsEnabled().performClick()
        waitForIdle()

        onNodeWithText("Make a new draw?").assertIsDisplayed()
        onNodeWithText("From 20 to 16 players").assertIsDisplayed()
        onNodeWithText("The tables of all 2 rounds. They are drawn again.").assertIsDisplayed()
        onNodeWithText("The people who leave: #5 · Name 5, #6 · Player 6, #7 · Name 7, #8 · Name 8.").assertIsDisplayed()
        onNodeWithText("How does the draw work?").assertIsDisplayed()
        // Without registrations, nothing is said about them.
        assertTrue(onAllNodesWithText("Their registrations.").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Every person affected gets an email", substring = true).assertIsDisplayed()

        click("playerCountConfirmButton")
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("playerCountDone").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("The tournament now has 16 players and a new draw.").assertIsDisplayed()
        onNodeWithText("0 people got an email.").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountNoEmail").fetchSemanticsNodes().isEmpty())

        val request = world.server.playerCountRequest!!
        assertEquals(listOf(5, 6, 7, 8), request.removedSlotIds)
        assertEquals(16, request.players.size)
        assertEquals(8, request.tables.size)
        assertEquals(16, world.store.tournaments.value.single().numPlayers)

        press()

        onNodeWithText("Close").performClick()
        waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun backKeepsTheChoiceAndANewNumberClearsIt() = runComposeUiTest {
        val world = World(individual, slots(20))
        showFlow(world, individual)
        press()
        onNodeWithContentDescription("One table less").performClick()
        listOf(1, 2, 3, 4).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        press()
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithTag("playerCountChosen").assertTextEquals("4 of 4 chosen")

        press()

        onNodeWithContentDescription("One table more").performClick()
        press()
        onNodeWithContentDescription("One table more").performClick()
        onNodeWithTag("playerCountTarget").assertTextEquals("24 players · 6 tables")
        onNodeWithText("4 new places, empty until you assign them in Players.").assertIsDisplayed()
        press()
        onNodeWithContentDescription("One table less").performClick()
        press()
        onNodeWithContentDescription("One table less").performClick()
        onNodeWithTag("playerCountChosen").assertTextEquals("0 of 4 chosen")
        assertNull(world.server.playerCountRequest)
    }

    @Test
    fun theSearchFindsPeopleByNameNumberOrEmaAndKeepsTheChoice() = runComposeUiTest {
        val world = World(individual, slots(20))
        showFlow(world, individual)
        press()
        onNodeWithContentDescription("One table less").performClick()
        val shown = { (1..20).filter { onAllNodesWithTag("playerCountSlot-$it").fetchSemanticsNodes().isNotEmpty() } }

        onNodeWithTag("playerCountSearch").performTextInput("náme 7")
        waitForIdle()
        assertEquals(listOf(7), shown())
        click("playerCountSlot-7")

        onNodeWithTag("playerCountSearch").performTextReplacement("#12")
        waitForIdle()
        assertEquals(listOf(12), shown())
        onNodeWithTag("playerCountSearch").performTextReplacement("10000015")
        waitForIdle()
        assertEquals(listOf(15), shown())

        onNodeWithTag("playerCountSearch").performTextReplacement("nobody here")
        waitForIdle()
        assertEquals(emptyList(), shown())
        onNodeWithText("Nobody matches the search.").assertIsDisplayed()

        // The choice stays while the search hides it, and the whole list comes back with an empty search.
        onNodeWithTag("playerCountChosen").assertTextEquals("1 of 4 chosen")
        onNodeWithTag("playerCountSearch").performTextReplacement("")
        waitForIdle()
        onNodeWithTag("playerCountSlot-7").assertIsOn()
        assertTrue(onAllNodesWithText("Nobody matches the search.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theSearchFindsTeamsByTheirNameOrPlayersAndTheWaitingListByName() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showFlow(world, teamsTournament)
        press()
        onNodeWithContentDescription("One table less").performClick()
        val shownTeams = { (1..5).filter { onAllNodesWithTag("playerCountTeam-$it").fetchSemanticsNodes().isNotEmpty() } }
        onNodeWithTag("playerCountSearch").performTextInput("team 4")
        waitForIdle()
        assertEquals(listOf(4), shownTeams())
        // Name 10 plays in team 3.
        onNodeWithTag("playerCountSearch").performTextReplacement("Name 10")
        waitForIdle()
        assertEquals(listOf(3), shownTeams())
    }

    @Test
    fun theResultSaysWhoGotNoEmailByName() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.server.playerCountEmails = PlayerCountOutcome(Tournament(id = "", name = "", isTeams = false, numPlayers = 0, numRounds = 0), emailsSent = 14, noEmail = listOf("10000005"), emailFailed = listOf("10000007"))
        showFlow(world, individual)
        press()
        onNodeWithContentDescription("One table less").performClick()
        listOf(5, 6, 7, 8).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        click("playerCountConfirmButton")
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("playerCountDone").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("14 people got an email.").assertIsDisplayed()
        onNodeWithText("Without an email, tell them yourself:").assertIsDisplayed()
        onNodeWithText("#5 · Name 5").assertIsDisplayed()
        onNodeWithText("The email failed, tell them yourself:").assertIsDisplayed()
        onNodeWithText("#7 · Name 7").assertIsDisplayed()
    }

    @Test
    fun theSmallestTournamentCannotLoseATable() = runComposeUiTest {
        val small = individual.copy(numPlayers = 16)
        showFlow(World(small, slots(16)), small)
        onNodeWithContentDescription("One table less").assertIsNotEnabled()
        onNodeWithText("Use − or + to remove or add whole tables of 4 players.").assertIsDisplayed()
    }

    @Test
    fun addsATableAndGivesPlacesToPeopleOfTheWaitingList() = runComposeUiTest {
        val withRegistrations = individual.copy(numPlayers = 16, registration = RegistrationSettings())
        val waiting = listOf(
            AdminRegistration("20000001", "Late One", "ES", RegistrationStatus.WAITING, 1),
            AdminRegistration("20000002", "Late Two", "ES", RegistrationStatus.WAITING, 2),
        )
        val world = World(withRegistrations, slots(16), waiting = waiting)
        showFlow(world, withRegistrations)

        press()

        onNodeWithContentDescription("One table more").performClick()
        onNodeWithText("Waiting list: up to 4 people (optional)").assertIsDisplayed()
        onNodeWithTag("playerCountContinue").assertIsEnabled()
        click("playerCountWaiting-20000002")
        click("playerCountContinue")

        onNodeWithText("From 16 to 20 players").assertIsDisplayed()
        onNodeWithText("3 empty places, to assign afterwards.").assertIsDisplayed()
        onNodeWithText("From the waiting list, with a place now: Late Two.").assertIsDisplayed()
        click("playerCountConfirmButton")
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("playerCountDone").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("20000002"), world.server.playerCountRequest!!.waitingEmaIds)
        assertEquals(emptyList(), world.server.playerCountRequest!!.removedSlotIds)
    }

    @Test
    fun aTeamTournamentRemovesWholeTeams() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showFlow(world, teamsTournament)

        press()

        onNodeWithContentDescription("One table less").performClick()
        onNodeWithText("Which teams leave? Choose 1.").assertIsDisplayed()
        click("playerCountTeam-3")
        click("playerCountContinue")
        onNodeWithText("The teams that leave, with their players: Team 3.").assertIsDisplayed()
        onNodeWithText("The other teams, with their names and players.").assertIsDisplayed()
        click("playerCountConfirmButton")
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("playerCountDone").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(9, 10, 11, 12), world.server.playerCountRequest!!.removedSlotIds)
        assertEquals(List(4) { 4 }, world.server.playerCountRequest!!.players.groupBy { it.team }.values.map { it.size })
    }

    @Test
    fun aRefusalOfTheServerShowsItsReasonAndKeepsTheConfirmation() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.server.playerCountError = AppError.Registration(RegistrationErrorCode.TOURNAMENT_STARTED)
        showFlow(world, individual)
        press()
        onNodeWithContentDescription("One table less").performClick()
        listOf(1, 2, 3, 4).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        click("playerCountConfirmButton")
        waitUntil(timeoutMillis = 30_000) {
            onAllNodesWithText("The tournament already has data in its tables. The draw can no longer change.").fetchSemanticsNodes().isNotEmpty()
        }
        press()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithTag("playerCountConfirm").assertIsDisplayed()
    }

    @Test
    fun aTournamentWithTableDataExplainsWhyItCannotChange() = runComposeUiTest {
        val started = individual.copy(hasTableData = true)
        show(World(started, slots(20))) { PlayerCountFlow(tournament = started, onDismiss = {}) }
        onNodeWithTag("playerCountLocked").assertIsDisplayed()
        onNodeWithText("The number of players cannot change").assertIsDisplayed()
        onNodeWithText("The tournament already has data in its tables.\nThe number of players can only change before the first hand.").assertIsDisplayed()
    }

    @Test
    fun aFinishedTournamentExplainsWhyItCannotChange() = runComposeUiTest {
        val finished = individual.copy(isCompleted = true)
        show(World(finished, slots(20))) { PlayerCountFlow(tournament = finished, onDismiss = {}) }
        onNodeWithText("The tournament is finished.").assertIsDisplayed()
    }

    @Test
    fun editTournamentOpensItFromTheDataTab() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.store.adminStatus.value = AdminStatus("u1", GlobalUserRole.SUPER_ADMIN)
        show(world) { EditTournamentDialog(tournament = individual, onDismiss = {}) }
        waitForIdle()
        press()
        onNodeWithText("Data").performClick()
        waitForIdle()
        click("changePlayerCount")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountChoose").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun playersOffersItToAnAdmin() = runComposeUiTest {
        val world = World(individual, slots(20))
        show(world) { PlayersScreen(rememberNavController(), "t1") }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Number of players").fetchSemanticsNodes().isNotEmpty() }
        press()
        onNodeWithText("Number of players").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountChoose").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun playersHidesItFromAnAccountWithoutTheRightToConfigure() = runComposeUiTest {
        val editorView = individual.copy(permissions = allPermissions - Permission.TOURNAMENT_CONFIGURE)
        val world = World(editorView, slots(20))
        show(world, GlobalUserRole.EDITOR) { PlayersScreen(rememberNavController(), "t1") }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertTrue(onAllNodesWithText("Number of players").fetchSemanticsNodes().isEmpty())
    }
}
