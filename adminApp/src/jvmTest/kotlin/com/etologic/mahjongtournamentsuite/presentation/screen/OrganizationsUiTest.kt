package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.domain.model.AdminStatus
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationDraft
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationPlace
import com.etologic.mahjongtournamentsuite.domain.model.OrganizationType
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.VenueLinkPlace
import com.etologic.mahjongtournamentsuite.domain.repository.AdminRepository
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.EditTournamentDialog
import com.etologic.mahjongtournamentsuite.presentation.components.LocalPressTimeSource
import com.etologic.mahjongtournamentsuite.presentation.presenter.IdCardProofPresenter
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * The organizations: the optional premises of a club, read from a Google Maps link like the venue of a tournament,
 * and who changes the organizers of a tournament.
 */
@OptIn(ExperimentalTestApi::class)
class OrganizationsUiTest {
    private val club = Organization("org-es", "Madrid Club", OrganizationType.CLUB, "ES", canEdit = true)
    private val link = "https://maps.app.goo.gl/abc123"

    /** The clock of the press threshold, moved by hand, so a fast or slow machine never changes a result. */
    private val pressClock = TestTimeSource()

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.showEditor(
        organization: Organization?,
        resolve: suspend (String) -> AppResult<ResolvedVenueLink> = { AppResult.Success(ResolvedVenueLink(it, null)) },
        onSave: (OrganizationDraft) -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(LocalPressTimeSource provides pressClock) {
                MtsTheme(useDarkTheme = false) {
                    OrganizationEditorDialog(
                        organization = organization,
                        saving = false,
                        onDismiss = {},
                        onSave = onSave,
                        onDelete = null,
                        onError = {},
                        resolvePlaceLink = resolve,
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.typeInto(label: String, text: String) {
        onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(text)
    }

    @Test
    fun aGoogleMapsLinkFillsThePremisesOfTheClub() = runComposeUiTest {
        var asked: String? = null
        var saved: OrganizationDraft? = null
        showEditor(
            organization = club,
            resolve = { url ->
                asked = url
                AppResult.Success(ResolvedVenueLink(url, VenueLinkPlace("Club room", "Calle Mayor 1", "Madrid", "ES")))
            },
            onSave = { saved = it },
        )

        onNodeWithText("Club premises").performScrollTo().assertExists()
        typeInto("Google Maps link", link)
        awaitText("Calle Mayor 1")
        assertEquals(link, asked)
        onNodeWithText("Save").performClick()

        val draft = assertNotNull(saved)
        assertEquals(OrganizationPlace(link, "Club room", "Calle Mayor 1", "Madrid"), draft.place)
        assertEquals("ES", draft.country, "the link never changes the country of the club")
    }

    @Test
    fun thePremisesAreOptionalButANameAloneIsNotEnough() = runComposeUiTest {
        var saved: OrganizationDraft? = null
        showEditor(organization = club, onSave = { saved = it })

        typeInto("Name of the premises", "Club room")
        onNodeWithText("Save").performClick()
        awaitText("Add a Google Maps link or an address")
        assertNull(saved)

        typeInto("Address", "Calle Mayor 1")
        // Every button ignores a second press within 500 ms.
        pressClock += 600.milliseconds
        onNodeWithText("Save").performClick()
        assertEquals(OrganizationPlace(name = "Club room", address = "Calle Mayor 1"), saved?.place)
    }

    @Test
    fun aClubWithoutPremisesSavesNoPlace() = runComposeUiTest {
        var saved: OrganizationDraft? = null
        showEditor(organization = club, onSave = { saved = it })

        onNodeWithText("Save").performClick()
        assertTrue(assertNotNull(saved).place.isEmpty)
    }

    @Test
    fun aFederationHasNoPremises() = runComposeUiTest {
        var saved: OrganizationDraft? = null
        val federation = club.copy(type = OrganizationType.FEDERATION, place = OrganizationPlace(address = "Old address"))
        showEditor(organization = federation, onSave = { saved = it })

        assertEquals(0, onAllNodesWithText("Club premises").fetchSemanticsNodes().size)
        onNodeWithText("Save").performClick()
        assertTrue(assertNotNull(saved).place.isEmpty, "a federation saves no place")
    }

    @Test
    fun theSavedPremisesShowWhenTheClubOpens() = runComposeUiTest {
        showEditor(organization = club.copy(place = OrganizationPlace(link, "Club room", "Calle Mayor 1", "Madrid")), onSave = {})

        onNodeWithText("Calle Mayor 1").performScrollTo().assertExists()
        onNodeWithText("Club room").assertExists()
        onNodeWithText(link).assertExists()
    }

    private fun ComposeUiTest.showTournament(status: AdminStatus, organizerIds: List<String>) {
        val tournament = Tournament(
            id = "t1",
            name = "Open",
            isTeams = false,
            numPlayers = 8,
            numRounds = 2,
            eventStartDate = "2026-10-22",
            eventEndDate = "2026-10-24",
            organizerIds = organizerIds,
        )
        val store = AppMemoryStore().apply { adminStatus.value = status }
        setContent {
            val koin = remember {
                koinApplication {
                    modules(
                        module {
                            single { Logger.withTag("ui-test") }
                            single<TournamentRepository> { FakeTournamentRepository().apply { tournaments = listOf(tournament) } }
                            single<AdminRepository> { FakeAdminRepository(status.role) }
                            single<AuthRepository> { FakeSignedInAuthRepository() }
                            single { store }
                            factory { TournamentsPresenter(get(), get(), get()) }
                            factory { IdCardProofPresenter(get()) }
                        },
                    )
                }.koin
            }
            KoinContext(koin) {
                CompositionLocalProvider(LocalThemeController provides ThemeController(ThemePreference.Light, false, {})) {
                    MtsTheme(useDarkTheme = false) { EditTournamentDialog(tournament = tournament, onDismiss = {}) }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Organizers").performClick()
        awaitText("Main organizer")
    }

    private val locked = "Only a Super Admin or a Club Admin of the main organizer changes the organizers"

    @Test
    fun aClubAdminOfTheMainOrganizerChangesTheOrganizers() = runComposeUiTest {
        showTournament(AdminStatus("me", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-es")), listOf("org-es"))

        awaitText("Add a co-organizer")
        assertEquals(0, onAllNodesWithText(locked, substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aClubAdminOfACoOrganizerOnlySeesTheOrganizers() = runComposeUiTest {
        showTournament(AdminStatus("me", GlobalUserRole.CLUB_ADMIN, adminOrganizations = setOf("org-es")), listOf("org-fr", "org-es"))

        awaitText(locked)
        assertEquals(0, onAllNodesWithText("Add a co-organizer").fetchSemanticsNodes().size)
    }

    @Test
    fun aSuperAdminChangesTheOrganizersOfAnyTournament() = runComposeUiTest {
        showTournament(AdminStatus("me", GlobalUserRole.SUPER_ADMIN), listOf("org-fr"))

        awaitText("Add a co-organizer")
    }
}
