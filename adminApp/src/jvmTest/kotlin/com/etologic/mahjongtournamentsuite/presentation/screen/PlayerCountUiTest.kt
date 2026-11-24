package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Permission
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
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
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.PlayerCountRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * The change of the number of players (docs/player-count-change.md): the screen from its first step to the result,
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
                            factory { PlayerCountPresenter(get(), get(), get(), DrawTournamentScheduleUseCase(), get()) }
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

    private fun ComposeUiTest.showScreen(world: World, started: Boolean = false, onBack: () -> Unit = {}) {
        show(world) { PlayerCountScreen(tournamentId = "t1", started = started, onBack = onBack) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountList").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The node of [tag], scrolled into view first when it is a line of the list. */
    private fun ComposeUiTest.see(tag: String): SemanticsNodeInteraction {
        runCatching { onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag)) }
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.click(tag: String) {
        press()
        see(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitForDone() {
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("playerCountDone").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The red button of the review, then the red button of "cannot be undone". */
    private fun ComposeUiTest.makeTheDraw() {
        click("playerCountConfirmButton")
        onNodeWithText("This cannot be undone.", substring = true).assertIsDisplayed()
        click("playerCountSureButton")
    }

    @Test
    fun removesATableWithTheFourPeopleTheOrganizerMarks() = runComposeUiTest {
        val world = World(individual, slots(20))
        var back = false
        showScreen(world) { back = true }

        see("playerCountNow").assertTextEquals("Now: 20 players, 5 tables per round.")
        see("playerCountTarget").assertTextEquals("After the change: 20 players, 5 tables per round.")
        see("playerCountChosen").assertTextEquals("Mark the people who leave, in groups of 4.")
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()

        click("playerCountSlot-5")
        see("playerCountChosen").assertTextEquals("Marked: 1. Mark 3 more or unmark 1.")
        listOf(6, 7).forEach { click("playerCountSlot-$it") }
        see("playerCountChosen").assertTextEquals("Marked: 3. Mark 1 more or unmark 3.")
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()
        click("playerCountSlot-8")
        see("playerCountChosen").assertTextEquals("Marked: 4. Tables that leave: 1.")
        see("playerCountTarget").assertTextEquals("After the change: 16 players, 4 tables per round.")
        // 16 is the minimum: a fifth person cannot be marked, and no table can be added while people leave.
        see("playerCountSlot-9").assertIsNotEnabled()
        see("playerCountMore").assertIsNotEnabled()
        onNodeWithText("To add tables, first unmark the people who leave.").assertIsDisplayed()
        click("playerCountContinue")

        onNodeWithText("Make a new draw?").assertIsDisplayed()
        onNodeWithText("From 20 to 16 players").assertIsDisplayed()
        onNodeWithText("The tables of all 2 rounds. They are drawn again.").assertIsDisplayed()
        onNodeWithText("The people who leave:").assertIsDisplayed()
        listOf("#5 · Name 5", "#6 · Player 6", "#7 · Name 7", "#8 · Name 8").forEachIndexed { index, line ->
            onNodeWithTag("playerCountLeaving-$index").assertTextEquals(line)
        }
        onNodeWithText("How does the draw work?").assertIsDisplayed()
        // Without registrations, nothing is said about them.
        assertTrue(onAllNodesWithText("Their registrations.").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Every person affected gets an email", substring = true).assertIsDisplayed()

        makeTheDraw()
        waitForDone()
        onNodeWithText("The tournament now has 16 players and a new draw.").assertIsDisplayed()
        onNodeWithText("0 people got an email.").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountNoEmail").fetchSemanticsNodes().isEmpty())

        val request = world.server.playerCountRequest!!
        assertEquals(listOf(5, 6, 7, 8), request.removedSlotIds)
        assertEquals(16, request.players.size)
        assertEquals(8, request.tables.size)
        assertEquals(16, world.store.tournaments.value.single().numPlayers)

        click("playerCountClose")
        assertTrue(back)
    }

    @Test
    fun eightMarkedTakeAwayTwoTables() = runComposeUiTest {
        val world = World(individual.copy(numPlayers = 28), slots(28))
        world.store.upsertTournaments(listOf(individual.copy(numPlayers = 28)))
        showScreen(world)
        (1..8).forEach { click("playerCountSlot-$it") }
        see("playerCountChosen").assertTextEquals("Marked: 8. Tables that leave: 2.")
        see("playerCountTarget").assertTextEquals("After the change: 20 players, 5 tables per round.")
        click("playerCountSlot-9")
        // 9 marked: one more would leave 16 players, so both ways are offered.
        see("playerCountChosen").assertTextEquals("Marked: 9. Mark 3 more or unmark 1.")
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()
    }

    @Test
    fun theReviewCanGoBackAndTheChoiceStays() = runComposeUiTest {
        val world = World(individual, slots(20))
        showScreen(world)
        listOf(1, 2, 3, 4).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        click("playerCountBack")
        see("playerCountChosen").assertTextEquals("Marked: 4. Tables that leave: 1.")
        see("playerCountSlot-1").assertIsOn()
        // The cancel of "cannot be undone" changes nothing.
        click("playerCountContinue")
        click("playerCountConfirmButton")
        press()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithTag("playerCountConfirm").assertIsDisplayed()
        assertNull(world.server.playerCountRequest)
    }

    @Test
    fun addedTablesLockTheListOfWhoLeaves() = runComposeUiTest {
        val world = World(individual, slots(20))
        showScreen(world)
        click("playerCountMore")
        click("playerCountMore")
        see("playerCountAdded").assertTextEquals("Tables added: 2")
        see("playerCountTarget").assertTextEquals("After the change: 28 players, 7 tables per round.")
        onNodeWithText("8 new places, empty until you assign them in Players.").assertIsDisplayed()
        onNodeWithText("To mark who leaves, first take away the tables added.").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountSlot-1").fetchSemanticsNodes().isEmpty())
        onNodeWithTag("playerCountContinue").assertIsEnabled()
        click("playerCountLess")
        click("playerCountLess")
        onNodeWithTag("playerCountContinue").assertIsNotEnabled()
        see("playerCountSlot-1").assertIsDisplayed()
    }

    @Test
    fun theSearchFindsPeopleByNameNumberOrEmaAndKeepsTheChoice() = runComposeUiTest {
        val world = World(individual, slots(20))
        showScreen(world)
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
        see("playerCountChosen").assertTextEquals("Marked: 1. Mark 3 more or unmark 1.")
        onNodeWithTag("playerCountSearch").performTextReplacement("")
        waitForIdle()
        see("playerCountSlot-7").assertIsOn()
        assertTrue(onAllNodesWithText("Nobody matches the search.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theSearchFindsPeopleByTheNameOfTheirTeam() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showScreen(world)
        val shown = { (1..20).filter { onAllNodesWithTag("playerCountSlot-$it").fetchSemanticsNodes().isNotEmpty() } }
        onNodeWithTag("playerCountSearch").performTextInput("team 4")
        waitForIdle()
        assertEquals(listOf(13, 14, 15, 16), shown())
    }

    @Test
    fun theResultSaysWhoGotNoEmailByName() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.server.playerCountEmails = PlayerCountOutcome(Tournament(id = "", name = "", isTeams = false, numPlayers = 0, numRounds = 0), emailsSent = 14, noEmail = listOf("10000005"), emailFailed = listOf("10000007"))
        showScreen(world)
        listOf(5, 6, 7, 8).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        makeTheDraw()
        waitForDone()
        onNodeWithText("14 people got an email.").assertIsDisplayed()
        onNodeWithText("Without an email, tell them yourself:").assertIsDisplayed()
        onNodeWithText("#5 · Name 5").assertIsDisplayed()
        onNodeWithText("The email failed, tell them yourself:").assertIsDisplayed()
        onNodeWithText("#7 · Name 7").assertIsDisplayed()
    }

    @Test
    fun theSmallestTournamentCanOnlyAddTables() = runComposeUiTest {
        val small = individual.copy(numPlayers = 16)
        showScreen(World(small, slots(16)))
        onNodeWithText("With 16 players nobody can leave: a tournament needs at least 16.").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountSlot-1").fetchSemanticsNodes().isEmpty())
        see("playerCountMore").assertIsEnabled()
    }

    @Test
    fun addsATableAndGivesPlacesToPeopleOfTheWaitingList() = runComposeUiTest {
        val withRegistrations = individual.copy(numPlayers = 16, registration = RegistrationSettings())
        val waiting = listOf(
            AdminRegistration("20000001", "Late One", "ES", RegistrationStatus.WAITING, 1),
            AdminRegistration("20000002", "Late Two", "ES", RegistrationStatus.WAITING, 2),
        )
        val world = World(withRegistrations, slots(16), waiting = waiting)
        showScreen(world)

        click("playerCountMore")
        onNodeWithText("Waiting list: up to 4 people (optional)").assertIsDisplayed()
        onNodeWithTag("playerCountContinue").assertIsEnabled()
        click("playerCountWaiting-20000002")
        click("playerCountContinue")

        onNodeWithText("From 16 to 20 players").assertIsDisplayed()
        onNodeWithText("3 empty places, numbered after the highest one, to assign afterwards.").assertIsDisplayed()
        onNodeWithText("From the waiting list, with a place now:").assertIsDisplayed()
        onNodeWithTag("playerCountFromWaiting-0").assertTextEquals("Late Two")
        makeTheDraw()
        waitForDone()
        assertEquals(listOf("20000002"), world.server.playerCountRequest!!.waitingEmaIds)
        assertEquals(emptyList(), world.server.playerCountRequest!!.removedSlotIds)
    }

    @Test
    fun aTeamTournamentRemovesPeopleAndTheirTeamsStayIncomplete() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showScreen(world)

        // People, not teams (Ernesto, 24-nov): 3 of team 1 and 1 of team 2.
        onNodeWithText("Who leaves?").assertIsDisplayed()
        onNodeWithText("Mark the people. If a team loses someone, the next step helps you complete it.").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountTeam-1").fetchSemanticsNodes().isEmpty())
        listOf(1, 2, 3, 5).forEach { click("playerCountSlot-$it") }
        see("playerCountChosen").assertTextEquals("Marked: 4. Tables that leave: 1.")
        // 16 is the minimum: nobody else can leave.
        see("playerCountSlot-6").assertIsNotEnabled()
        click("playerCountContinue")
        // The step Teams: the organizer may go on with the teams incomplete.
        onNodeWithText("Reorganize the teams").assertIsDisplayed()
        see("playerCountTeamsStatus").assertTextEquals("Incomplete teams: 2.")
        see("playerCountTeam-1").assertExists()
        see("playerCountTeam-2").assertExists()
        click("playerCountTeamsContinue")
        listOf("#1 · Name 1", "#2 · Name 2", "#3 · Name 3", "#5 · Name 5").forEachIndexed { index, line ->
            onNodeWithTag("playerCountLeaving-$index").assertTextEquals(line)
        }
        onNodeWithText("Teams that stay incomplete, with the players they keep. Reorganize them afterwards:").assertIsDisplayed()
        onNodeWithTag("playerCountBrokenTeams-0").assertTextEquals("Team 1 (1)")
        onNodeWithTag("playerCountBrokenTeams-1").assertTextEquals("Team 2 (3)")
        makeTheDraw()
        waitForDone()
        val request = world.server.playerCountRequest!!
        assertEquals(listOf(1, 2, 3, 5), request.removedSlotIds)
        // Everyone who stays keeps their number and team; teammates never share a table.
        assertEquals((4..20).filter { it != 5 }, request.players.map { it.id }.sorted())
        assertEquals(listOf(1, 3, 4, 4, 4), request.players.groupBy { it.team }.values.map { it.size }.sorted())
        request.players.forEach { assertEquals((it.id - 1) / 4 + 1, it.team) }
        val teamOf = request.players.associate { it.id to it.team }
        request.tables.forEach { table -> assertEquals(4, table.playerIds.map { teamOf[it] }.toSet().size, "$table") }
        assertEquals(teamsTournament.numRounds * 4, request.tables.size)
    }

    @Test
    fun theStepTeamsCompletesTheTeamsWithTheProposalOrByHand() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showScreen(world)
        listOf(1, 2, 3, 5).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        see("playerCountStep-1").assertExists()
        see("playerCountStep-3").assertExists()

        // By hand: the 4 goes to team 2, from its menu.
        click("playerCountMove-4")
        click("playerCountMoveTo-4-2")
        see("playerCountTeamsStatus").assertTextEquals("Every team has 4 players.")
        // Start again, and then the proposal makes the same move.
        click("playerCountTeamsReset")
        see("playerCountTeamsStatus").assertTextEquals("Incomplete teams: 2.")
        click("playerCountPropose")
        see("playerCountTeamsStatus").assertTextEquals("Every team has 4 players.")

        click("playerCountTeamsContinue")
        onNodeWithTag("playerCountTeamChanges-0").assertTextEquals("#4 · Name 4 → Team 2")
        assertTrue(onAllNodesWithTag("playerCountBrokenTeams-0").fetchSemanticsNodes().isEmpty())
        // Back from the review goes to the teams, with the moves kept.
        click("playerCountBack")
        see("playerCountTeamsStatus").assertTextEquals("Every team has 4 players.")
        click("playerCountTeamsContinue")
        makeTheDraw()
        waitForDone()
        val request = world.server.playerCountRequest!!
        assertEquals(2, request.players.first { it.id == 4 }.team)
        assertEquals(listOf(4, 4, 4, 4), request.players.groupBy { it.team }.values.map { it.size })
        val teamOf = request.players.associate { it.id to it.team }
        request.tables.forEach { table -> assertEquals(4, table.playerIds.map { teamOf[it] }.toSet().size, "$table") }
    }

    @Test
    fun anotherChoiceOfWhoLeavesForgetsTheMoves() = runComposeUiTest {
        val teamsTournament = individual.copy(isTeams = true)
        val teams = (1..5).map { TournamentTeam(it, "Team $it", (1..4).map { slot -> (it - 1) * 4 + slot }) }
        val world = World(teamsTournament, slots(20, isTeams = true), teams)
        showScreen(world)
        listOf(1, 2, 3, 5).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        click("playerCountPropose")
        click("playerCountTeamsBack")
        // A whole team leaves instead: no team is incomplete, so there is no step Teams.
        listOf(5, 4).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        onNodeWithText("Make a new draw?").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("playerCountTeamChanges-0").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithTag("playerCountStep-3").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aRefusalOfTheServerShowsItsReasonAndKeepsTheReview() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.server.playerCountError = AppError.Registration(RegistrationErrorCode.TOURNAMENT_STARTED)
        showScreen(world)
        listOf(1, 2, 3, 4).forEach { click("playerCountSlot-$it") }
        click("playerCountContinue")
        makeTheDraw()
        waitUntil(timeoutMillis = 30_000) {
            onAllNodesWithText("The tournament already has data in its tables. The draw can no longer change.").fetchSemanticsNodes().isNotEmpty()
        }
        press()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithTag("playerCountConfirm").assertIsDisplayed()
        assertEquals(20, world.store.tournaments.value.single().numPlayers)
    }

    @Test
    fun aTournamentWithTableDataExplainsWhyItCannotChange() = runComposeUiTest {
        val started = individual.copy(hasTableData = true)
        show(World(started, slots(20))) { PlayerCountScreen(tournamentId = "t1", onBack = {}) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountLocked").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("The number of players cannot change").assertIsDisplayed()
        onNodeWithText("The tournament already has data in its tables.\nThe number of players can only change before the first hand.").assertIsDisplayed()
    }

    @Test
    fun playersPassesThatATableHasData() = runComposeUiTest {
        show(World(individual, slots(20))) { PlayerCountScreen(tournamentId = "t1", started = true, onBack = {}) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountLocked").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aFinishedTournamentExplainsWhyItCannotChange() = runComposeUiTest {
        val finished = individual.copy(isCompleted = true)
        show(World(finished, slots(20))) { PlayerCountScreen(tournamentId = "t1", onBack = {}) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("The tournament is finished.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun editTournamentOpensItFromTheDataTab() = runComposeUiTest {
        val world = World(individual, slots(20))
        world.store.adminStatus.value = AdminStatus("u1", GlobalUserRole.SUPER_ADMIN)
        var opened: Tournament? = null
        show(world) { EditTournamentDialog(tournament = individual, onDismiss = {}, onPlayerCount = { opened = it }) }
        waitForIdle()
        press()
        onNodeWithText("Data").performClick()
        waitForIdle()
        click("changePlayerCount")
        assertEquals("t1", opened?.id)
    }

    @Test
    fun playersOpensTheScreenOfAnAdmin() = runComposeUiTest {
        val world = World(individual, slots(20))
        show(world) {
            val navController = rememberNavController()
            NavHost(navController, startDestination = PlayersRoute("t1")) {
                composable<PlayersRoute> { PlayersScreen(navController, "t1") }
                composable<PlayerCountRoute> { entry ->
                    val args = entry.toRoute<PlayerCountRoute>()
                    PlayerCountScreen(args.tournamentId, args.started, onBack = { navController.popBackStack() })
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Number of players").fetchSemanticsNodes().isNotEmpty() }
        press()
        onNodeWithText("Number of players").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("playerCountList").fetchSemanticsNodes().isNotEmpty() }
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
