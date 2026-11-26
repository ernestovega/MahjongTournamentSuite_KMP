package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.Country
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.domain.usecase.DrawTournamentScheduleUseCase
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.CreateTournamentPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.store.AppMemoryStore
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The form that creates a tournament: the check of each field before the creation (it opens the tab of the first
 * wrong field), the creation with a draw or with registrations, and the errors of the server.
 */
@OptIn(ExperimentalTestApi::class)
class CreateTournamentDialogFlowsUiTest {
    // The form gives the progress and the result on the main dispatcher, which a test has only when it sets one.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val pressClock = TestTimeSource()

    private class World(
        access: AdminStatus = AdminStatus("me", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-es")),
        val createSucceeds: Boolean = true,
        countriesFail: Boolean = false,
    ) {
        val requests = mutableListOf<CreateTournamentRequest>()
        val created = mutableListOf<Tournament>()
        var dismissed = 0
        val store = AppMemoryStore().also { it.adminStatus.value = access }
        private val server = FakeTournamentRepository().apply { countries = listOf(Country("ES", "Spain")) }
        val repository: TournamentRepository = object : TournamentRepository by server {
            override suspend fun createTournament(request: CreateTournamentRequest): AppResult<Tournament> {
                requests += request
                return if (createSucceeds) {
                    AppResult.Success(Tournament(id = "new", name = request.name, isTeams = request.isTeams, numPlayers = request.numPlayers, numRounds = request.numRounds))
                } else {
                    AppResult.Failure(AppError.Network)
                }
            }

            override suspend fun listCountries(refreshMode: RefreshMode) = if (countriesFail) AppResult.Failure(AppError.Network) else server.listCountries(refreshMode)
        }
        val admin: AdminRepository = FakeAdminRepository(access.role)
    }

    private fun ComposeUiTest.show(world: World = World()): World {
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.repository }
                            single { world.store }
                            factory { DrawTournamentScheduleUseCase() }
                            factory { CreateTournamentPresenter(get(), get(), get(), adminRepository = world.admin) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalPressTimeSource provides pressClock) {
                    MtsTheme(useDarkTheme = false) {
                        CreateTournamentDialog(onDismiss = { world.dismissed++ }, onCreated = { world.created += it })
                    }
                }
            }
        }
        waitForIdle()
        return world
    }

    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and hasText(text))
    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun ComposeUiTest.count(text: String, substring: Boolean = false) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun ComposeUiTest.click(text: String) {
        button(text).performClick()
        waitForIdle()
    }

    /** Buttons ignore a second press within 500 ms: each press of Create comes later on the clock of the buttons. */
    private fun ComposeUiTest.create() {
        pressClock += 1.seconds
        click("Create")
    }

    /** The dates of the tournament and the date and time of its one round. */
    private fun ComposeUiTest.fillSchedule() {
        click("Schedule")
        field("From").performTextReplacement("22/10/2026")
        field("To").performTextReplacement("22/10/2026")
        field("Date").performScrollTo().performTextInput("22/10/2026")
        field("Start").performScrollTo().performTextInput("10:00")
        waitForIdle()
    }

    private fun ComposeUiTest.fillGeneral(players: String = "16", rounds: String = "1") {
        field("Name").performTextInput("Open Madrid")
        field("Short name").performTextInput("OPEN")
        field("Players").performTextReplacement(players)
        field("Rounds").performTextReplacement(rounds)
        waitForIdle()
    }

    private fun ComposeUiTest.fillVenue() {
        click("Venue")
        field("Venue name").performScrollTo().performTextInput("Club")
        field("Address").performScrollTo().performTextInput("Calle 1")
        field("City").performScrollTo().performTextInput("Madrid")
        field("Contact email").performScrollTo().performTextInput("club@example.test")
        waitForIdle()
    }

    @Test
    fun anEmptyFormStaysOnTheGeneralTabWithTheErrors() = runComposeUiTest {
        val world = show()
        field("Players").performTextReplacement("6")
        field("Rounds").performTextReplacement("0")
        create()
        onNodeWithText("Name is required.").assertExists()
        onNodeWithText("Use 1 to 10 characters.").assertExists()
        onNodeWithText("Must be a multiple of 4", substring = true).assertExists()
        onNodeWithText("Must be a positive whole number.").assertExists()
        // Typing in a field clears its error.
        field("Short name").performTextInput("OPEN")
        waitForIdle()
        assertEquals(0, count("Use 1 to 10 characters."))
        assertEquals(emptyList(), world.requests)
    }

    @Test
    fun aWrongMatchTimeIsMarkedOnTheGeneralTab() = runComposeUiTest {
        show()
        fillGeneral()
        fillVenue()
        click("General")
        field("Match duration (minutes)").performScrollTo().performTextReplacement("0")
        onNode(isToggleable() and hasText("Finish the hand in play")).performScrollTo().performClick()
        waitForIdle()
        field("Extra time (minutes)").performScrollTo().performTextReplacement("")
        create()
        assertTrue(count("Use a whole number of minutes from 1 to", substring = true) >= 1)
        field("Match duration (minutes)").performScrollTo().performTextReplacement("90")
        field("Extra time (minutes)").performScrollTo().performTextInput("10")
        waitForIdle()
        assertEquals(0, count("Use a whole number of minutes from 1 to", substring = true))
    }

    @Test
    fun aMissingPlaceOpensTheVenueTab() = runComposeUiTest {
        show()
        fillGeneral()
        create()
        onNodeWithText("City is required.").assertExists()
        onNodeWithText("Contact email is required.").assertExists()
        field("City").performScrollTo().performTextInput("Madrid")
        waitForIdle()
        assertEquals(0, count("City is required."))
    }

    @Test
    fun aMissingStartDateOpensTheScheduleTab() = runComposeUiTest {
        show()
        fillGeneral()
        fillVenue()
        click("Schedule")
        field("From").performTextReplacement("")
        waitForIdle()
        click("General")
        create()
        onNodeWithText("Use DD/MM/YYYY.").assertExists()

        field("From").performTextInput("24/10/2026")
        waitForIdle()
        assertEquals(0, count("Use DD/MM/YYYY."))
        // An end date before the start moves to the start, and an empty end date takes the start date.
        field("To").performTextReplacement("22/10/2026")
        waitForIdle()
        field("To").assert(hasText("24/10/2026"))
        field("To").performTextReplacement("")
        waitForIdle()
        field("To").assert(hasText("24/10/2026"))
    }

    @Test
    fun theDatePickerGivesBackTheChosenDates() = runComposeUiTest {
        show()
        click("Schedule")
        field("From").performTextReplacement("22/10/2026")
        field("To").performTextReplacement("24/10/2026")
        onNode(hasClickAction() and hasContentDescription("Choose start date")).performClick()
        waitForIdle()
        click("OK")
        field("From").assert(hasText("22/10/2026"))
        field("To").assert(hasText("24/10/2026"))
        onNode(hasClickAction() and hasContentDescription("Choose end date")).performClick()
        waitForIdle()
        onAllNodes(hasClickAction() and hasText("Cancel")).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
        waitForIdle()
        assertEquals(1, onAllNodes(hasClickAction() and hasText("Cancel")).fetchSemanticsNodes().size)
    }

    @Test
    fun aCompleteFormDrawsAndCreatesTheTournament() = runComposeUiTest {
        val world = show()
        fillGeneral()
        onNode(isToggleable() and hasText("Heavy computing")).performScrollTo().performClick()
        waitForIdle()
        fillVenue()
        fillSchedule()
        create()
        waitUntil(timeoutMillis = 10_000) { world.created.isNotEmpty() }
        val request = world.requests.single()
        assertEquals("Open Madrid", request.name)
        assertEquals("OPEN", request.shortName)
        assertEquals(16, request.numPlayers)
        assertEquals(listOf("org-es"), request.organizerIds)
        // The country of the main organizer fills the host country.
        assertEquals("ES", request.hostCountry)
        assertNull(request.registration)
        assertTrue(request.tables.isNotEmpty())
        assertEquals("new", world.store.tournaments.value.single().id)
    }

    @Test
    fun aRefusedCreationShowsTheError() = runComposeUiTest {
        val world = show(World(createSucceeds = false))
        fillGeneral()
        fillVenue()
        fillSchedule()
        create()
        waitUntil(timeoutMillis = 10_000) { count("Network request failed.") > 0 }
        click("OK")
        assertEquals(emptyList(), world.created)
    }

    @Test
    fun withRegistrationsThereIsNoDrawAndThePlacesAreChecked() = runComposeUiTest {
        val world = show()
        fillGeneral()
        onNode(isToggleable() and hasText("Open registrations")).performScrollTo().performClick()
        waitForIdle()
        field("Places (optional)").performScrollTo().performTextInput("2")
        create()
        onNodeWithText("Write a number from 4 to 1000, or leave it empty.").assertExists()
        field("Places (optional)").performScrollTo().performTextReplacement("40")
        waitForIdle()
        assertEquals(0, count("Write a number from 4 to 1000, or leave it empty."))

        fillVenue()
        fillSchedule()
        create()
        waitUntil(timeoutMillis = 10_000) { world.created.isNotEmpty() }
        val request = world.requests.single()
        assertEquals(0, request.numPlayers)
        assertEquals(40, assertNotNull(request.registration).maxPlayers)
    }

    @Test
    fun aSuperAdminMustChooseTheMainOrganizer() = runComposeUiTest {
        val world = show(World(access = AdminStatus("me", GlobalUserRole.SUPER_ADMIN)))
        fillGeneral()
        fillVenue()
        create()
        // Without an organizer the form has no host country either: the venue tab shows first.
        onNodeWithText("Select a host country.").assertExists()
        assertEquals(emptyList(), world.requests)
    }

    @Test
    fun aFailedLoadOfTheCountriesShowsTheError() = runComposeUiTest {
        show(World(countriesFail = true))
        waitUntil(timeoutMillis = 5_000) { count("Network request failed.") > 0 }
        click("OK")
        assertEquals(0, count("Network request failed."))
    }

    @Test
    fun cancelClosesTheForm() = runComposeUiTest {
        val world = show()
        click("Cancel")
        assertEquals(1, world.dismissed)
    }
}
