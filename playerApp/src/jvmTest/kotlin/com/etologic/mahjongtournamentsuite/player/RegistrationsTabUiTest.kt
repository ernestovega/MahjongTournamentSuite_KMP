package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.DrawAssignment
import com.etologic.mahjongtournamentsuite.domain.model.NewRegistration
import com.etologic.mahjongtournamentsuite.domain.model.NoEmaNumberRequest
import com.etologic.mahjongtournamentsuite.domain.model.OwnRegistration
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.PublicRegistration
import com.etologic.mahjongtournamentsuite.domain.model.RegisterRequest
import com.etologic.mahjongtournamentsuite.domain.model.RegisterResult
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationImport
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationPhase
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationStatus
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationTeam
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationUpdate
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val settings = RegistrationSettings(status = RegistrationPhase.OPEN, maxPlayers = 2, closesOn = "2027-01-31", slug = "mo-2027", confirmed = 2, waiting = 1)

private val tournament = Tournament(
    id = "t",
    name = "Madrid Open",
    isTeams = true,
    numPlayers = 0,
    numRounds = 7,
    drawPending = true,
    registration = settings,
)

private val publicList = TournamentRegistrations(
    settings = settings,
    isTeams = true,
    registrations = listOf(
        PublicRegistration("10990001", "ANA LOPEZ", "ES", RegistrationStatus.CONFIRMED, 1, teamId = "dragons"),
        PublicRegistration("10990002", "BEA RUIZ", "IT", RegistrationStatus.CONFIRMED, 2),
        PublicRegistration("10990003", "CARLA GIL", "FR", RegistrationStatus.WAITING, 1),
    ),
    teams = listOf(RegistrationTeam("dragons", "Dragons", "10990001", listOf("10990001"))),
)

/** The registrations of the server in memory. It records the calls of the person. */
private class FakeRegistrations(var own: OwnRegistration? = null, var list: TournamentRegistrations = publicList) : RegistrationRepository {
    val registered = mutableListOf<RegisterRequest>()
    var cancelled = 0

    override suspend fun loadPublicList(tournamentId: String) = AppResult.Success(list)
    override suspend fun resolveLink(slug: String) = AppResult.Success("t")
    override suspend fun register(tournamentId: String, request: RegisterRequest): AppResult<RegisterResult> {
        registered += request
        return AppResult.Success(RegisterResult(RegistrationStatus.PENDING, null, "da***@example.com", null))
    }
    override suspend fun confirm(tournamentId: String, token: String) = AppResult.Success(own!!)
    override suspend fun loadOwn(tournamentId: String): AppResult<OwnRegistration?> = AppResult.Success(own)
    override suspend fun updateOwn(tournamentId: String, foodRestrictions: String, consent: Boolean) = AppResult.Success(own!!.copy(foodRestrictions = foodRestrictions))
    override suspend fun cancelOwn(tournamentId: String): AppResult<Unit> {
        cancelled++
        own = null
        return AppResult.Success(Unit)
    }
    override suspend fun createTeam(tournamentId: String, name: String) = AppResult.Success(own!!)
    override suspend fun renameTeam(tournamentId: String, name: String) = AppResult.Success(own!!)
    override suspend fun leaveTeam(tournamentId: String) = AppResult.Success(own!!)
    override suspend fun addTeamMember(tournamentId: String, emaId: String) = AppResult.Success(own!!)
    override suspend fun removeTeamMember(tournamentId: String, emaId: String) = AppResult.Success(own!!)
    override suspend fun requestWithoutEmaNumber(tournamentId: String, request: NoEmaNumberRequest) = AppResult.Success(Unit)
    override suspend fun loadAdminList(tournamentId: String): AppResult<AdminRegistrations> = error("admin")
    override suspend fun adminAdd(tournamentId: String, registration: NewRegistration): AppResult<AdminRegistration> = error("admin")
    override suspend fun adminUpdate(tournamentId: String, emaId: String, update: RegistrationUpdate): AppResult<AdminRegistration> = error("admin")
    override suspend fun adminRemove(tournamentId: String, emaId: String, notify: Boolean): AppResult<Unit> = error("admin")
    override suspend fun updateSettings(tournamentId: String, settings: RegistrationSettings): AppResult<RegistrationSettings> = error("admin")
    override suspend fun previewImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = error("admin")
    override suspend fun applyImport(tournamentId: String, fileName: String, bytes: ByteArray): AppResult<RegistrationImport> = error("admin")
    override suspend fun makeDraw(
        tournamentId: String,
        numTries: Long,
        players: List<com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer>,
        tables: List<com.etologic.mahjongtournamentsuite.domain.model.TournamentTable>,
        assignments: List<DrawAssignment>,
    ): AppResult<Tournament> = error("admin")
}

/** A guest: no session. */
private val guest: AuthRepository = Proxy.newProxyInstance(AuthRepository::class.java.classLoader, arrayOf(AuthRepository::class.java)) { _, method, _ ->
    when (method.name) {
        "currentSession" -> null
        else -> error("Unexpected call ${method.name}")
    }
} as AuthRepository

private val registry: PlayerRepository = Proxy.newProxyInstance(PlayerRepository::class.java.classLoader, arrayOf(PlayerRepository::class.java)) { _, method, _ ->
    when (method.name) {
        "listPlayers" -> AppResult.Success(listOf(Player("10990004", "Dani", "Sol", "ES"), Player("10990005", "Eva", "Mar", "PT")))
        else -> error("Unexpected call ${method.name}")
    }
} as PlayerRepository

/** The Players tab of a tournament that takes registrations: the public list, the registration and the own registration. */
@OptIn(ExperimentalTestApi::class)
class RegistrationsTabUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun cleanUp() {
        Locale.setDefault(original)
        stopKoin()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(repository: FakeRegistrations, shown: Tournament = tournament) = setContent {
        KoinApplication(application = { modules(module { factory { PlayerRegistrationPresenter(repository, guest, registry) } }) }) {
            MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { RegistrationsTab(shown) } }
        }
    }

    @Test
    fun theListShowsThePlacesAndTheWaitingListWithTheTeamsAndNothingPrivate() = runComposeUiTest {
        show(FakeRegistrations())
        waitUntil { onAllNodesWithTextExists("ANA LOPEZ") }
        onNodeWithText("Registrations are open").assertIsDisplayed()
        onNodeWithText("With a place (2)").assertIsDisplayed()
        onNodeWithText("Waiting list (1)").assertIsDisplayed()
        onNodeWithText("10990001 · Dragons").assertIsDisplayed()
        // A person of a tournament with teams who has none yet.
        onNodeWithText("10990002 · No team yet").assertIsDisplayed()
        onNodeWithText("CARLA GIL").assertIsDisplayed()
        onNodeWithTag("registration-register").assertIsDisplayed()
        onNodeWithText("I have no EMA number").assertIsDisplayed()
    }

    @Test
    fun theSearchFiltersByNameNumberOrTeam() = runComposeUiTest {
        show(FakeRegistrations())
        waitUntil { onAllNodesWithTextExists("ANA LOPEZ") }
        onNodeWithText("Search").performTextInput("dragons")
        onNodeWithText("ANA LOPEZ").assertIsDisplayed()
        onNodeWithText("BEA RUIZ").assertDoesNotExist()
        onNodeWithText("CARLA GIL").assertDoesNotExist()
    }

    @Test
    fun aGuestPicksItsEmaNumberWritesAnEmailAndGetsTheLinkToConfirm() = runComposeUiTest {
        val repository = FakeRegistrations()
        show(repository)
        waitUntil { onAllNodesWithTextExists("ANA LOPEZ") }
        onNodeWithTag("registration-register").performClick()
        waitUntil { onAllNodesWithTagExists("registration-ema-search") }
        onNodeWithTag("registration-submit").assertIsNotEnabled()
        onNodeWithTag("registration-ema-search").performTextInput("dani")
        waitUntil { onAllNodesWithTextExists("DANI SOL") }
        onNodeWithText("DANI SOL").performClick()
        onNodeWithText("Email").performTextInput("dani@example.com")
        // Food restrictions need the consent.
        onNodeWithText("Food restrictions (optional)").performTextInput("Celiac")
        onNodeWithTag("registration-submit").assertIsNotEnabled()
        onNodeWithTag("registration-consent").performClick()
        onNodeWithTag("registration-submit").assertIsEnabled().performClick()
        waitUntil { repository.registered.isNotEmpty() }
        assertEquals(RegisterRequest("10990004", "dani@example.com", "Celiac", consent = true), repository.registered.single())
        waitUntil { onAllNodesWithTextExists("We sent a link to da***@example.com.", substring = true) }
    }

    @Test
    fun aRegisteredPersonSeesItsPlaceAndCancelsWithAConfirmation() = runComposeUiTest {
        val repository = FakeRegistrations(own = OwnRegistration("10990002", "BEA RUIZ", "IT", RegistrationStatus.CONFIRMED, 2))
        show(repository)
        waitUntil { onAllNodesWithTextExists("You are registered, with place 2.") }
        onNodeWithText("BEA RUIZ (you)").assertIsDisplayed()
        // The team is still missing in a tournament with teams.
        onNodeWithText("Your team is missing.", substring = true).assertIsDisplayed()
        onNodeWithTag("registration-register").assertDoesNotExist()
        onNodeWithTag("registration-manage").performClick()
        waitUntil { onAllNodesWithTagExists("registration-cancel") }
        onNodeWithText("Create the team").assertIsDisplayed()
        onNodeWithTag("registration-cancel").performClick()
        waitUntil { onAllNodesWithTextExists("Cancel your registration?") }
        onNodeWithText("Cancel registration").performClick()
        waitUntil { repository.cancelled == 1 }
        waitUntil { onAllNodesWithTagExists("registration-register") }
    }

    @Test
    fun closedRegistrationsHaveNoButtonToRegister() = runComposeUiTest {
        val closed = settings.copy(status = RegistrationPhase.CLOSED)
        show(FakeRegistrations(list = publicList.copy(settings = closed)), tournament.copy(registration = closed))
        waitUntil { onAllNodesWithTextExists("ANA LOPEZ") }
        onNodeWithText("Registrations are closed").assertIsDisplayed()
        onNodeWithTag("registration-register").assertDoesNotExist()
        onNodeWithText("I have no EMA number").assertDoesNotExist()
    }

    @Test
    fun aTournamentInRegistrationShowsOnlyPlayersAndInfo() {
        assertEquals(listOf(MainTab.PLAYERS, MainTab.INFO), MainTab.visible(isTeams = true, inRegistration = true))
        assertTrue(MainTab.TABLES in MainTab.visible(isTeams = false))
        assertTrue(tournament.inRegistration)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextExists(text: String, substring: Boolean = false) =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagExists(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
