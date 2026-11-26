package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRoundSchedule
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
import com.etologic.mahjongtournamentsuite.presentation.presenter.TournamentsPresenter
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeAdminRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeSignedInAuthRepository
import com.etologic.mahjongtournamentsuite.presentation.screen.FakeTournamentRepository
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The edit dialog of a tournament: the check of each field before the save (it opens the tab of the first wrong field),
 * the errors of the server, the date picker, the data tab, and the delete of the tournament.
 */
@OptIn(ExperimentalTestApi::class)
class EditTournamentDialogFlowsUiTest {
    private val valid = Tournament(
        id = "t1", name = "Open", isTeams = false, numPlayers = 8, numRounds = 1,
        eventStartDate = "2026-10-22", eventEndDate = "2026-10-24",
        shortName = "OPEN", primaryColor = "#02B16B", hostCountry = "ESP", hostCity = "Madrid", timeZone = "Europe/Madrid",
        venue = TournamentVenue(name = "Club", address = "Calle 1", contactEmail = "club@example.test"),
        roundSchedules = listOf(TournamentRoundSchedule(roundId = 1, date = "2026-10-22", startTime = "10:00")),
        organizerIds = listOf("org-es"),
    )

    private class World(val access: AdminStatus?, organizationsFail: Boolean = false, deleteSucceeds: Boolean = false) {
        val server = FakeTournamentRepository().apply { writesSucceed = true }
        val deleted = mutableListOf<String>()
        val store = AppMemoryStore().also { it.adminStatus.value = access }
        val repository: TournamentRepository = object : TournamentRepository by server {
            override suspend fun deleteTournament(tournamentId: String): AppResult<Unit> {
                deleted += tournamentId
                return if (deleteSucceeds) AppResult.Success(Unit) else AppResult.Failure(AppError.Network)
            }
        }
        val admin: AdminRepository = FakeAdminRepository(access?.role ?: GlobalUserRole.EDITOR).let { fake ->
            if (!organizationsFail) {
                fake
            } else {
                object : AdminRepository by fake {
                    override suspend fun listOrganizations(): AppResult<List<Organization>> = AppResult.Failure(AppError.Network)
                }
            }
        }
        var dismissed = 0
        var deletedCalls = 0
        val playerCounts = mutableListOf<String>()
    }

    /** Buttons ignore a second press within 500 ms: the tests move this clock instead of waiting. */
    private val pressClock = TestTimeSource()

    private val superAdmin = AdminStatus("me", GlobalUserRole.SUPER_ADMIN)

    private fun ComposeUiTest.show(
        tournament: Tournament = valid,
        world: World = World(superAdmin),
        withPlayerCount: Boolean = false,
    ): World {
        world.server.tournaments = listOf(tournament)
        setContent {
            val themeController = ThemeController(ThemePreference.Light, false, {})
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { world.repository }
                            single<AdminRepository> { world.admin }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single { world.store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides themeController, LocalPressTimeSource provides pressClock) {
                    MtsTheme(useDarkTheme = false) {
                        EditTournamentDialog(
                            tournament = tournament,
                            onDismiss = { world.dismissed++ },
                            onDeleted = { world.deletedCalls++ },
                            onPlayerCount = if (withPlayerCount) ({ world.playerCounts += it.id }) else null,
                        )
                    }
                }
            }
        }
        waitForIdle()
        return world
    }

    private fun ComposeUiTest.button(text: String) = onNode(hasClickAction() and hasText(text))
    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun ComposeUiTest.click(text: String) {
        button(text).performClick()
        waitForIdle()
    }
    private fun ComposeUiTest.save() = click("Save")
    private fun ComposeUiTest.tab(title: String) = click(title)

    @Test
    fun aValidChangeIsSavedAndClosesTheDialog() = runComposeUiTest {
        val world = show()
        field("Tournament name").performTextReplacement("Open 2026")
        save()
        waitUntil(timeoutMillis = 5_000) { world.dismissed == 1 }
        assertEquals(true, world.server.savedIsPublic)
    }

    @Test
    fun anEmptyNameOrShortNameStaysOnTheGeneralTabWithTheErrors() = runComposeUiTest {
        val world = show()
        field("Tournament name").performTextReplacement(" ")
        field("Short name").performTextReplacement("")
        save()
        onNodeWithText("Tournament name is required.").assertExists()
        onNodeWithText("Use 1 to 10 characters.").assertExists()
        // Typing clears the error of the field.
        field("Tournament name").performTextInput("Open")
        field("Short name").performTextInput("OPEN-LONG-NAME")
        waitForIdle()
        assertEquals(0, count("Tournament name is required."))
        assertEquals(0, count("Use 1 to 10 characters."))
        assertEquals(0, world.dismissed)
    }

    @Test
    fun aMissingPlaceOpensTheVenueTabWithTheErrors() = runComposeUiTest {
        show(valid.copy(hostCountry = "", hostCity = ""))
        save()
        onNodeWithText("Select a host country.").assertExists()
        onNodeWithText("Host city is required.").assertExists()
        field("City").performScrollTo().performTextInput("Madrid")
        waitForIdle()
        assertEquals(0, count("Host city is required."))
    }

    @Test
    fun aMissingVenueAndContactEmailAreMarked() = runComposeUiTest {
        show(valid.copy(venue = TournamentVenue()))
        save()
        // The venue tab is open: its time zone field shows.
        onNodeWithText("Time zone", substring = true).assertExists()
        assertEquals(0, count("Tournament name"))
    }

    @Test
    fun withoutOrganizersTheOrganizersTabAsksForTheMainOne() = runComposeUiTest {
        show(valid.copy(organizerIds = emptyList()))
        save()
        onNodeWithText("Choose the main organizer.").assertExists()
    }

    @Test
    fun aCoOrganizerCanBeRemovedBeforeTheSave() = runComposeUiTest {
        val world = show(valid.copy(organizerIds = listOf("org-es", "org-fr")))
        tab("Organizers")
        onNode(hasContentDescription("Remove Paris Club"), useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(0, onAllNodes(hasContentDescription("Remove Paris Club"), useUnmergedTree = true).fetchSemanticsNodes().size)
        save()
        waitUntil(timeoutMillis = 5_000) { world.dismissed == 1 }
    }

    @Test
    fun wrongDatesOpenTheScheduleTab() = runComposeUiTest {
        show(valid.copy(eventStartDate = null, eventEndDate = null, roundSchedules = emptyList()))
        save()
        onNodeWithText("Use DD/MM/YYYY.").assertExists()
        onNodeWithText("Use a valid date on or after From.").assertExists()

        // A start date alone gives the end date the same day.
        field("From").performTextInput("22/10/2026")
        waitForIdle()
        assertEquals(0, count("Use DD/MM/YYYY."))
        field("To").performTextReplacement("")
        waitForIdle()
        field("To").assert(hasText("22/10/2026"))
    }

    @Test
    fun anEndBeforeTheStartIsMarked() = runComposeUiTest {
        show(valid.copy(eventStartDate = "2026-10-24", eventEndDate = "2026-10-22", roundSchedules = emptyList()))
        save()
        onNodeWithText("Use a valid date on or after From.").assertExists()
        field("To").performTextReplacement("25102026")
        waitForIdle()
        assertEquals(0, count("Use a valid date on or after From."))
    }

    @Test
    fun aRoundOutsideTheDatesOfTheTournamentStopsTheSave() = runComposeUiTest {
        val world = show(valid.copy(roundSchedules = listOf(TournamentRoundSchedule(roundId = 1, date = "2026-11-30", startTime = "10:00"))))
        save()
        // The schedule tab opens with the error under the tabs.
        onNodeWithText("From", substring = true).assertExists()
        assertEquals(0, world.dismissed)
        assertNull(world.server.savedIsPublic)
    }

    @Test
    fun aWrongMatchDurationStopsTheSave() = runComposeUiTest {
        val world = show()
        field("Match duration (minutes)").performScrollTo().performTextReplacement("0")
        save()
        onNodeWithText("Match duration must be a whole number of minutes", substring = true).assertExists()
        assertNull(world.server.savedIsPublic)
    }

    @Test
    fun aWrongTimeZoneOpensTheVenueTab() = runComposeUiTest {
        val world = show(valid.copy(timeZone = "not a zone"))
        save()
        onNodeWithText("Time zone must be a name like Europe/Madrid.").assertExists()
        assertNull(world.server.savedIsPublic)
    }

    @Test
    fun aWrongExtraTimeStopsTheSaveWhenTheHandIsFinished() = runComposeUiTest {
        val world = show()
        onNodeWithText("Finish the hand in play").performScrollTo().performClick()
        waitForIdle()
        field("Extra time (minutes)").performScrollTo().performTextReplacement("")
        save()
        onNodeWithText("Extra time must be a whole number of minutes", substring = true).assertExists()
        assertNull(world.server.savedIsPublic)
    }

    @Test
    fun aRefusedSaveShowsTheError() = runComposeUiTest {
        val world = World(superAdmin).also { it.server.writesSucceed = false }
        show(world = world)
        save()
        waitUntil(timeoutMillis = 5_000) { count("Error") > 0 }
        click("OK")
        assertEquals(0, world.dismissed)
    }

    @Test
    fun aFailedLoadOfTheOrganizationsShowsTheError() = runComposeUiTest {
        show(world = World(superAdmin, organizationsFail = true))
        waitUntil(timeoutMillis = 5_000) { count("Network request failed.") > 0 }
    }

    @Test
    fun theDatePickerGivesBackTheChosenDates() = runComposeUiTest {
        show()
        tab("Schedule")
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
    fun theDataTabShowsTheTournamentAndOpensTheChangeOfPlayers() = runComposeUiTest {
        val world = show(valid.copy(isCompleted = true), withPlayerCount = true)
        tab("Data")
        onNodeWithText("t1").assertExists()
        onNodeWithText("Yes").assertExists()
        onNodeWithText("No").assertExists()
        assertTrue(count("—") >= 2)
        onNodeWithTag("changePlayerCount").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("t1"), world.playerCounts)
    }

    @Test
    fun withoutTheRightToConfigureThereIsNoChangeOfPlayersNorDelete() = runComposeUiTest {
        show(world = World(AdminStatus("me", GlobalUserRole.EDITOR, permissions = emptySet())), withPlayerCount = true)
        tab("Data")
        onNodeWithTag("changePlayerCount").assertDoesNotExist()
        assertEquals(0, count("Delete"))
    }

    @Test
    fun cancelClosesTheDialog() = runComposeUiTest {
        val world = show()
        click("Cancel")
        assertEquals(1, world.dismissed)
    }

    @Test
    fun deletingAsksFirstAndThenRemovesTheTournament() = runComposeUiTest {
        // The dialog calls onDeleted on the main dispatcher, which a test has only when it sets one.
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            deleteAndCheck()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun ComposeUiTest.deleteAndCheck() {
        val world = show(world = World(superAdmin, deleteSucceeds = true))
        click("Delete")
        onNodeWithText("Delete tournament", substring = true).assertExists()
        onNodeWithText("This will permanently delete \"Open\" (ID: t1).", substring = true).assertExists()
        onAllNodes(hasClickAction() and hasText("Delete")).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
        waitUntil(timeoutMillis = 5_000) { world.deletedCalls == 1 }
        assertEquals(listOf("t1"), world.deleted)
    }

    @Test
    fun aRefusedDeleteShowsTheErrorAndCancelKeepsTheTournament() = runComposeUiTest {
        val world = show()
        click("Delete")
        onAllNodes(hasClickAction() and hasText("Cancel")).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
        waitForIdle()
        assertEquals(0, count("This will permanently delete \"Open\" (ID: t1).", substring = true))

        pressClock += 1.seconds
        click("Delete")
        onAllNodes(hasClickAction() and hasText("Delete")).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
        waitUntil(timeoutMillis = 5_000) { count("Network request failed.") > 0 }
        assertEquals(0, world.deletedCalls)
    }

    private fun ComposeUiTest.count(text: String, substring: Boolean) = onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
}
