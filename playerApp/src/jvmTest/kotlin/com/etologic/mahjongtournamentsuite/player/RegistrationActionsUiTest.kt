package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistration
import com.etologic.mahjongtournamentsuite.domain.model.AdminRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.AuthSession
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
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentRegistrations
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.model.UserProfile
import com.etologic.mahjongtournamentsuite.domain.repository.AuthRepository
import com.etologic.mahjongtournamentsuite.domain.repository.PlayerRepository
import com.etologic.mahjongtournamentsuite.domain.repository.RegistrationRepository
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val openSettings = RegistrationSettings(status = RegistrationPhase.OPEN, maxPlayers = null, closesOn = "2027-01-31", slug = "mo-2027", confirmed = 2, waiting = 0)

private val teamsTournament = Tournament(id = "t", name = "Madrid Open", isTeams = true, numPlayers = 0, numRounds = 7, drawPending = true, registration = openSettings)

private val teamsList = TournamentRegistrations(
    settings = openSettings,
    isTeams = true,
    registrations = listOf(
        PublicRegistration("10990001", "ANA LOPEZ", "ES", RegistrationStatus.CONFIRMED, 1, teamId = "dragons"),
        PublicRegistration("10990002", "BEA RUIZ", "IT", RegistrationStatus.CONFIRMED, 2, teamId = "dragons"),
    ),
    teams = listOf(RegistrationTeam("dragons", "Dragons", "10990001", listOf("10990001", "10990002"))),
)

private val captain = OwnRegistration("10990001", "ANA LOPEZ", "ES", RegistrationStatus.CONFIRMED, 1, teamId = "dragons", email = "ana@x.es", foodRestrictions = "Celiac", sharedEmail = true)

/** The registrations of the server, with every call written down. [failing] answers the changes with a lost connection. */
private class RecordingRegistrations(
    var own: OwnRegistration? = null,
    var list: AppResult<TournamentRegistrations> = AppResult.Success(teamsList),
    private val failing: Boolean = false,
    private val registerAnswer: RegisterResult = RegisterResult(RegistrationStatus.WAITING, 3, "da***@example.com", "token"),
) : RegistrationRepository {
    val calls = mutableListOf<String>()

    private fun change(call: String): AppResult<OwnRegistration> {
        calls += call
        return if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(own!!)
    }

    override suspend fun loadPublicList(tournamentId: String) = list
    override suspend fun resolveLink(slug: String): AppResult<String> {
        calls += "resolve:$slug"
        return AppResult.Success("from-$slug")
    }
    override suspend fun register(tournamentId: String, request: RegisterRequest): AppResult<RegisterResult> {
        calls += "register:${request.emaId}"
        return if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(registerAnswer)
    }
    override suspend fun confirm(tournamentId: String, token: String): AppResult<OwnRegistration> {
        calls += "confirm:$token"
        return if (token == "bad") AppResult.Failure(AppError.Network) else AppResult.Success(captain)
    }
    override suspend fun loadOwn(tournamentId: String): AppResult<OwnRegistration?> = AppResult.Success(own)
    override suspend fun updateOwn(tournamentId: String, foodRestrictions: String, consent: Boolean) = change("food:$foodRestrictions:$consent")
    override suspend fun cancelOwn(tournamentId: String): AppResult<Unit> {
        calls += "cancel"
        return if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(Unit)
    }
    override suspend fun createTeam(tournamentId: String, name: String) = change("create:$name")
    override suspend fun renameTeam(tournamentId: String, name: String) = change("rename:$name")
    override suspend fun leaveTeam(tournamentId: String) = change("leave")
    override suspend fun addTeamMember(tournamentId: String, emaId: String) = change("add:$emaId")
    override suspend fun removeTeamMember(tournamentId: String, emaId: String) = change("remove:$emaId")
    override suspend fun requestWithoutEmaNumber(tournamentId: String, request: NoEmaNumberRequest): AppResult<Unit> {
        calls += "no-ema:${request.name}:${request.email}"
        return if (failing) AppResult.Failure(AppError.Network) else AppResult.Success(Unit)
    }
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
        players: List<TournamentPlayer>,
        tables: List<TournamentTable>,
        assignments: List<DrawAssignment>,
    ): AppResult<Tournament> = error("admin")
}

/** An account: with [emaId] it is the account of an EMA player. Without [signedIn] there is no session. */
private fun account(signedIn: Boolean, emaId: String? = null): AuthRepository = Proxy.newProxyInstance(AuthRepository::class.java.classLoader, arrayOf(AuthRepository::class.java)) { _, method, _ ->
    when (method.name) {
        "currentSession" -> if (signedIn) AuthSession("u1", "id", "r") else null
        "getMe" -> AppResult.Success(UserProfile("u1", "dani@x.es", emaId = emaId))
        else -> error("Unexpected call ${method.name}")
    }
} as AuthRepository

private val emaRegistry: PlayerRepository = Proxy.newProxyInstance(PlayerRepository::class.java.classLoader, arrayOf(PlayerRepository::class.java)) { _, method, _ ->
    when (method.name) {
        "listPlayers" -> AppResult.Success(listOf(Player("10990004", "Dani", "Sol", "ES")))
        else -> error("Unexpected call ${method.name}")
    }
} as PlayerRepository

/** What a registered person changes in the own registration, the message without an EMA number, and the links of registrations. */
@OptIn(ExperimentalTestApi::class)
class RegistrationActionsUiTest {
    private val original = Locale.getDefault()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
        stopKoin()
    }

    @AfterTest
    fun cleanUp() {
        Locale.setDefault(original)
        stopKoin()
    }

    private fun ComposeUiTest.show(repository: RecordingRegistrations, auth: AuthRepository = account(signedIn = false), shown: Tournament = teamsTournament) = setContent {
        KoinApplication(application = { modules(module { factory { PlayerRegistrationPresenter(repository, auth, emaRegistry) } }) }) {
            MtsTheme(useDarkTheme = false) { Column(Modifier.fillMaxSize()) { RegistrationsTab(shown) } }
        }
    }

    private fun ComposeUiTest.waitFor(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForTag(tag: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.click(text: String, scroll: Boolean = false) {
        // A button low in a dialog that scrolls: a click out of the dialog would close it.
        val node = onNode(hasClickAction() and hasText(text) and !hasSetTextAction())
        if (scroll) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label, substring = true))

    @Test
    fun theCaptainSavesTheFoodRenamesTheTeamAndChangesTheMembers() = runComposeUiTest {
        val repository = RecordingRegistrations(own = captain)
        show(repository)
        waitForTag("registration-manage")
        onNodeWithTag("registration-manage").performClick()
        waitForTag("registration-shared-email")
        waitFor("BEA RUIZ")
        field("Food restrictions").performTextReplacement("Vegan")
        click("Save")
        field("Team name").performTextReplacement("Tigers")
        click("Rename the team")
        click("Remove")
        field("Add a member").performScrollTo().performTextInput("1099x0005")
        click("Add a member", scroll = true)
        click("Leave the team", scroll = true)
        waitUntil(timeoutMillis = 5_000) { repository.calls.size == 5 }
        assertEquals(listOf("food:Vegan:true", "rename:Tigers", "remove:10990002", "add:10990005", "leave"), repository.calls)
        click("Close")
    }

    @Test
    fun aRegistrationToConfirmAsksToOpenTheEmail() = runComposeUiTest {
        show(
            RecordingRegistrations(own = OwnRegistration("10990002", "BEA RUIZ", "IT", RegistrationStatus.PENDING, 2)),
            shown = teamsTournament.copy(registration = openSettings.copy(waiting = 2, closesOn = null)),
        )
        waitFor("Confirm your email")
        waitFor("Open the link of the email")
    }

    @Test
    fun aPersonWithoutATeamCreatesOneAndALostConnectionSaysSo() = runComposeUiTest {
        val repository = RecordingRegistrations(own = OwnRegistration("10990003", "CARLA GIL", "FR", RegistrationStatus.CONFIRMED, 3), failing = true)
        show(repository)
        waitForTag("registration-manage")
        onNodeWithTag("registration-manage").performClick()
        waitFor("Create the team")
        field("Team name").performTextInput("Tigers")
        click("Create the team")
        waitFor("OK")
        click("OK")
        assertEquals(listOf("create:Tigers"), repository.calls)
    }

    @Test
    fun aPersonWithoutAnEmaNumberWritesToTheOrganization() = runComposeUiTest {
        val repository = RecordingRegistrations()
        show(repository)
        waitFor("I have no EMA number")
        click("I have no EMA number")
        field("Name and surname").performTextInput("Zoe Fern")
        field("Country").performTextInput("ES")
        field("Email").performTextInput(" zoe@x.es ")
        field("Message").performTextInput("Hello")
        click("Send")
        waitFor("Your message is sent")
        assertEquals(listOf("no-ema:Zoe Fern:zoe@x.es"), repository.calls)
    }

    @Test
    fun aMessageWithoutEmaThatIsNotSentShowsTheErrorAndCanBeCancelled() = runComposeUiTest {
        val repository = RecordingRegistrations(failing = true)
        show(repository)
        waitFor("I have no EMA number")
        click("I have no EMA number")
        field("Name and surname").performTextInput("Zoe Fern")
        field("Email").performTextInput("zoe@x.es")
        click("Send")
        waitFor("OK")
        click("OK")
        click("Cancel")
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun aSignedInPlayerRegistersAsItselfAndGetsTheWaitingList() = runComposeUiTest {
        val repository = RecordingRegistrations(list = AppResult.Success(teamsList.copy(registrations = emptyList(), teams = emptyList())))
        show(repository, auth = account(signedIn = true, emaId = "10990004"))
        waitFor("Nobody has registered yet.")
        onNodeWithTag("registration-register").performClick()
        waitFor("You register as DANI SOL (EMA 10990004).")
        onNodeWithTag("registration-submit").performClick()
        waitFor("You are number 3 on the waiting list")
        assertEquals(listOf("register:10990004"), repository.calls)
    }

    @Test
    fun aRegistrationWithAPlaceSaysWhichOne() = runComposeUiTest {
        val repository = RecordingRegistrations(registerAnswer = RegisterResult(RegistrationStatus.CONFIRMED, 4, "da***@x.es", null))
        show(repository, auth = account(signedIn = true, emaId = "10990009"))
        waitFor("ANA LOPEZ")
        field("Search").performTextInput("nobody")
        waitFor("Nobody matches the search.")
        onNodeWithTag("registration-register").performClick()
        waitFor("(EMA 10990009)")
        onNodeWithTag("registration-submit").performClick()
        waitFor("with place 4")
    }

    @Test
    fun aRegistrationThatIsNotSentShowsTheError() = runComposeUiTest {
        val repository = RecordingRegistrations(list = AppResult.Success(teamsList.copy(registrations = emptyList(), teams = emptyList())), failing = true)
        show(repository, auth = account(signedIn = true, emaId = "10990004"))
        waitFor("Nobody has registered yet.")
        onNodeWithTag("registration-register").performClick()
        waitFor("(EMA 10990004)")
        onNodeWithTag("registration-submit").performClick()
        waitFor("OK")
        click("OK")
        assertEquals(listOf("register:10990004"), repository.calls)
    }

    @Test
    fun aCancelThatIsNotSentShowsTheError() = runComposeUiTest {
        val repository = RecordingRegistrations(own = captain, failing = true)
        show(repository)
        waitForTag("registration-manage")
        onNodeWithTag("registration-manage").performClick()
        waitFor("Cancel my registration")
        click("Cancel my registration")
        waitFor("Cancel your registration?")
        click("Cancel registration")
        waitFor("OK")
        click("OK")
        assertEquals(listOf("cancel"), repository.calls)
    }

    @Test
    fun aListThatDoesNotLoadShowsTheError() = runComposeUiTest {
        show(RecordingRegistrations(list = AppResult.Failure(AppError.Network)))
        waitFor("OK")
        click("OK")
    }

    @Test
    fun theLinksOfTheEmailsConfirmAndTheOthersFindTheTournament() = runBlocking {
        val repository = RecordingRegistrations()
        val presenter = PlayerRegistrationPresenter(repository, account(signedIn = true, emaId = " "), emaRegistry)
        assertEquals(AppResult.Success("t1"), presenter.resolveLink(RegistrationLink(tournamentId = "t1", token = "good")))
        assertEquals(AppResult.Failure(AppError.Network), presenter.resolveLink(RegistrationLink(tournamentId = "t1", token = "bad")))
        assertEquals(AppResult.Success("t2"), presenter.resolveLink(RegistrationLink(tournamentId = "t2", myTable = true)))
        assertEquals(AppResult.Success("from-mo-2027"), presenter.resolveLink(RegistrationLink(slug = "mo-2027")))
        assertEquals(listOf("confirm:good", "confirm:bad", "resolve:mo-2027"), repository.calls)
        // An account without an EMA number registers by picking itself.
        assertNull(presenter.loadMe(emptyList()))
        assertNull(PlayerRegistrationPresenter(repository, account(signedIn = false), emaRegistry).loadMe(emptyList()))
        assertEquals(Player("10990007", "", ""), PlayerRegistrationPresenter(repository, account(signedIn = true, emaId = "10990007"), emaRegistry).loadMe(emptyList()))
    }

    @Test
    fun theOldQrOfATableAndTheEmailLinkWithoutTournamentAreRead() {
        assertEquals(RegistrationLink(slug = "mo-2027", myTable = true), registrationLinkOf("/mo-2027/table/3", emptyMap()))
        assertEquals(RegistrationLink(tournamentId = "abc", myTable = true), registrationLinkOf("/t/abc/mesa/2", emptyMap()))
        assertNull(registrationLinkOf("/inscripcion/gestionar", mapOf("t" to " ")))
        assertEquals(SiteLink.Tournament("abc"), siteLinkOf("/", mapOf("tournament" to "abc")))
        assertEquals(SiteLink.Home, siteLinkOf("/", mapOf("tournament" to " ")))
    }
}
