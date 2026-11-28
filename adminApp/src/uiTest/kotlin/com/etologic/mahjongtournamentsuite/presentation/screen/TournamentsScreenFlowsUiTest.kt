package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.PlayerBaseRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayerCountRoute
import com.etologic.mahjongtournamentsuite.presentation.PlayersRoute
import com.etologic.mahjongtournamentsuite.presentation.RegistrationsRoute
import com.etologic.mahjongtournamentsuite.presentation.SignInRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentRoute
import com.etologic.mahjongtournamentsuite.presentation.TournamentsRoute
import com.etologic.mahjongtournamentsuite.presentation.UsersRoute
import com.etologic.mahjongtournamentsuite.presentation.components.LocalCompactLayout
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
import com.etologic.mahjongtournamentsuite.presentation.sync.TournamentListPoll
import com.etologic.mahjongtournamentsuite.presentation.theme.LocalThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemeController
import com.etologic.mahjongtournamentsuite.presentation.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.compose.KoinContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The list of tournaments: where a tournament opens (its players, its rounds or its registrations), the edit and the
 * creation dialogs, the buttons to other screens, the profile with the sign out, and the errors of the server.
 * The other screens are stubs here: each shows its name.
 */
@OptIn(ExperimentalTestApi::class)
class TournamentsScreenFlowsUiTest {
    // The screen navigates on the main dispatcher, which a test has only when it sets one.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val pressClock = TestTimeSource()

    private val open = Tournament(id = "t1", name = "Open Madrid", isTeams = false, numPlayers = 4, numRounds = 1)
    private val assigned = (1..4).map { TournamentPlayer(id = it, name = "P$it", team = 0, assignedEmaId = "0$it") }

    private class World(
        tournaments: List<Tournament>,
        players: List<TournamentPlayer> = emptyList(),
        role: GlobalUserRole = GlobalUserRole.SUPER_ADMIN,
        val countriesFail: Boolean = false,
        val alias: String = "Me",
        /** The loads that fail: "profile", "admin" or "tournaments". */
        val failing: Set<String> = emptySet(),
    ) {
        val requests = mutableListOf<CreateTournamentRequest>()
        val server = FakeTournamentRepository().apply {
            this.tournaments = tournaments
            this.players = players
            countries = listOf(Country("ES", "Spain"))
        }
        val repository: TournamentRepository = object : TournamentRepository by server {
            override suspend fun listCountries(refreshMode: RefreshMode) = if (countriesFail) AppResult.Failure(AppError.Network) else server.listCountries(refreshMode)

            override suspend fun listTournaments(refreshMode: RefreshMode): AppResult<List<Tournament>> = if ("tournaments" in failing) AppResult.Failure(AppError.Network) else server.listTournaments(refreshMode)

            override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> {
                requests += request
                val tournament = Tournament(id = "new", name = request.name, isTeams = request.isTeams, numPlayers = request.numPlayers, numRounds = request.numRounds)
                return AppResult.Success(if (request.registration != null) tournament.copy(drawPending = true) else tournament)
            }
        }
        private val fakeAdmin = FakeAdminRepository(role)
        val admin: AdminRepository = object : AdminRepository by fakeAdmin {
            override suspend fun whoAmI(refreshMode: RefreshMode): AppResult<AdminStatus> = if ("admin" in failing) AppResult.Failure(AppError.Network) else fakeAdmin.whoAmI(refreshMode)
        }
        val auth: AuthRepository = object : AuthRepository by FakeSignedInAuthRepository() {
            override suspend fun getMe(refreshMode: RefreshMode): AppResult<UserProfile> = if ("profile" in failing) AppResult.Failure(AppError.Network) else AppResult.Success(UserProfile("me", "john.doe-smith@example.test", alias))
        }
        val store = AppMemoryStore()
    }

    private fun ComposeUiTest.show(world: World, compact: Boolean = false) {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.repository }
                            single<AdminRepository> { world.admin }
                            single<AuthRepository> { world.auth }
                            single { world.store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { DrawTournamentScheduleUseCase() }
                            factory { CreateTournamentPresenter(get(), get(), get(), adminRepository = world.admin) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalCompactLayout provides compact, LocalPressTimeSource provides pressClock) {
                    MtsTheme(useDarkTheme = false) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = TournamentsRoute) {
                            composable<TournamentsRoute> { TournamentsScreen(navController) }
                            composable<TournamentRoute> {
                                Text("AT tournament")
                                // The tournament screen opens the players itself (TournamentScreenUiTest).
                                if (it.toRoute<TournamentRoute>().openPlayers) Text("AT opens its players")
                            }
                            composable<PlayersRoute> { Text("AT players") }
                            composable<RegistrationsRoute> { Text("AT registrations") }
                            composable<UsersRoute> { Text("AT users") }
                            composable<PlayerBaseRoute> { Text("AT player base") }
                            composable<PlayerCountRoute> { Text("AT player count") }
                            composable<SignInRoute> { Text("AT sign in") }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
    private fun ComposeUiTest.click(text: String) {
        onNode(hasClickAction() and (hasText(text) or hasContentDescription(text))).performClick()
        waitForIdle()
    }

    /** The profile button shows the alias of the signed-in user. */
    private fun ComposeUiTest.openProfile() {
        onAllNodes(hasClickAction() and hasText("Me", substring = true))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openTheTournament() {
        awaitText("Open Madrid")
        onAllNodes(hasClickAction() and hasText("Open Madrid", substring = true))[0].performClick()
        waitForIdle()
    }

    @Test
    fun aTournamentWithEverySlotAssignedOpensOnItsRounds() = runComposeUiTest {
        show(World(listOf(open), players = assigned))
        openTheTournament()
        awaitText("AT tournament")
    }

    @Test
    fun aTournamentWithAnOpenSlotOpensOnItsPlayers() = runComposeUiTest {
        show(World(listOf(open), players = assigned.dropLast(1) + TournamentPlayer(id = 4, name = "P4", team = 0)))
        openTheTournament()
        awaitText("AT opens its players")
    }

    @Test
    fun aTournamentBeforeItsDrawOpensOnItsRegistrations() = runComposeUiTest {
        show(World(listOf(open.copy(drawPending = true))))
        openTheTournament()
        awaitText("AT registrations")
    }

    @Test
    fun aFailedLoadOfThePlayersShowsTheError() = runComposeUiTest {
        val world = World(listOf(open)).also { it.server.failPlayers = true }
        show(world)
        openTheTournament()
        awaitText("Network request failed.")
        click("OK")
        onNodeWithText("Tournaments").assertExists()
    }

    @Test
    fun aFailedLoadOfTheListShowsTheError() = runComposeUiTest {
        show(World(listOf(open), countriesFail = true))
        awaitText("Network request failed.")
    }

    @Test
    fun aTournamentWithALogoShowsIt() = runComposeUiTest {
        show(World(listOf(open.copy(associationLogoUrl = "https://logos.test/open.png"))))
        awaitText("Open Madrid")
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasContentDescription("Logo for Open Madrid"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun withoutTournamentsTheListSaysSo() = runComposeUiTest {
        show(World(emptyList()))
        awaitText("No tournaments yet")
    }

    @Test
    fun theEditDialogOpensFromTheRowAndLeadsToTheChangeOfPlayers() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        click("Edit Open Madrid")
        onNodeWithText("Edit tournament").assertExists()
        click("Data")
        onNodeWithTag("changePlayerCount").performScrollTo().performClick()
        waitForIdle()
        awaitText("AT player count")
    }

    @Test
    fun theEditDialogClosesWithCancel() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        click("Edit Open Madrid")
        onAllNodes(hasClickAction() and hasText("Cancel")).onLast().performClick()
        waitForIdle()
        assertEquals(0, count("Edit tournament"))
    }

    @Test
    fun newTournamentOpensTheFormAndCancelClosesIt() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        click("New tournament")
        onNodeWithText("Fields marked with * are required.").assertExists()
        onAllNodes(hasClickAction() and hasText("Cancel")).onLast().performClick()
        waitForIdle()
        assertEquals(0, count("Fields marked with * are required."))
    }

    @Test
    fun theTopBarOpensTheUsers() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        click("Users")
        awaitText("AT users")
    }

    @Test
    fun theTopBarOpensTheEmaPlayers() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        click("EMA players")
        awaitText("AT player base")
    }

    @Test
    fun withoutAnAliasTheProfileShowsTheNameOfTheEmail() = runComposeUiTest {
        show(World(listOf(open), alias = ""))
        awaitText("John Doe Smith")
    }

    @Test
    fun theProfileSignsOutAfterTheConfirmation() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        openProfile()
        click("Logout")
        onNodeWithText("Sign out").assertExists()
        click("Yes, sign out")
        awaitText("AT sign in")
    }

    @Test
    fun cancellingTheSignOutStaysOnTheList() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        openProfile()
        click("Logout")
        onAllNodes(hasClickAction() and hasText("Cancel")).onLast().performClick()
        waitForIdle()
        assertEquals(0, count("Yes, sign out"))
        onNodeWithText("Open Madrid").assertExists()
    }

    @Test
    fun deletingTheOwnAccountLeavesForTheSignIn() = runComposeUiTest {
        show(World(listOf(open)))
        awaitText("Open Madrid")
        openProfile()
        onNode(hasClickAction() and hasText("Delete my account")).performScrollTo().performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("Delete")).onLast().performClick()
        awaitText("AT sign in")
    }

    @Test
    fun onAPhoneACardOpensTheTournamentAndItsEditButtonTheDialog() = runComposeUiTest {
        val withLogo = open.copy(associationLogoUrl = "https://logo.test/a.png", drawPending = true, registration = RegistrationSettings(maxPlayers = 8, confirmed = 5, waiting = 2))
        show(World(listOf(withLogo)), compact = true)
        awaitText("Open Madrid")
        awaitText("5 / 8 (+2)")

        click("Edit Open Madrid")
        onNodeWithText("Edit tournament").assertExists()
        onAllNodes(hasClickAction() and hasText("Cancel")).onLast().performClick()
        waitForIdle()

        openTheTournament()
        awaitText("AT registrations")
    }

    @Test
    fun aFailedProfileShowsTheErrorOfTheProfileFirst() = runComposeUiTest {
        show(World(listOf(open), failing = setOf("profile", "admin", "tournaments")))
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedAccessShowsTheError() = runComposeUiTest {
        show(World(listOf(open), failing = setOf("admin", "tournaments")))
        awaitText("Network request failed.")
    }

    @Test
    fun aFailedListOfTournamentsShowsTheError() = runComposeUiTest {
        show(World(listOf(open), failing = setOf("tournaments")))
        awaitText("Network request failed.")
    }

    @Test
    fun theListChecksTheServerAgainAfterAWhile() = runComposeUiTest {
        val world = World(listOf(open))
        show(world)
        awaitText("Open Madrid")
        world.server.tournaments = listOf(open, open.copy(id = "t2", name = "Winter Cup"))

        mainClock.advanceTimeBy(TournamentListPoll.IDLE.inWholeMilliseconds + 1_000)
        awaitText("Winter Cup")
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))

    /** Fills a valid form: one round, its date and time, and a venue. */
    private fun ComposeUiTest.fillTheNewTournament(registrations: Boolean) {
        click("New tournament")
        field("Name").performTextInput("Spring Open")
        field("Short name").performTextInput("SPRING")
        field("Players").performTextReplacement("16")
        field("Rounds").performTextReplacement("1")
        if (registrations) onNode(isToggleable() and hasText("Open registrations")).performScrollTo().performClick()
        waitForIdle()
        click("Venue")
        field("Venue name").performScrollTo().performTextInput("Club")
        field("Address").performScrollTo().performTextInput("Calle 1")
        field("City").performScrollTo().performTextInput("Madrid")
        field("Contact email").performScrollTo().performTextInput("club@example.test")
        click("Schedule")
        field("From").performTextReplacement("22/10/2026")
        field("To").performTextReplacement("22/10/2026")
        field("Date").performScrollTo().performTextInput("22/10/2026")
        field("Start").performScrollTo().performTextInput("10:00")
        waitForIdle()
        pressClock += 1.seconds
        click("Create")
    }

    @Test
    fun aNewTournamentWithItsDrawOpensOnItsPlayers() = runComposeUiTest {
        // The new tournament opens on its players, over its tournament screen.
        // A club admin organizes with its club, so the form needs no organizer.
        show(World(listOf(open), role = GlobalUserRole.CLUB_ADMIN))
        awaitText("Open Madrid")
        fillTheNewTournament(registrations = false)
        waitUntil(timeoutMillis = 10_000) { count("AT opens its players") > 0 }
    }

    @Test
    fun aNewTournamentWithRegistrationsOpensOnThem() = runComposeUiTest {
        // A club admin organizes with its club, so the form needs no organizer.
        show(World(listOf(open), role = GlobalUserRole.CLUB_ADMIN))
        awaitText("Open Madrid")
        fillTheNewTournament(registrations = true)
        waitUntil(timeoutMillis = 10_000) { count("AT registrations") > 0 }
    }
}
