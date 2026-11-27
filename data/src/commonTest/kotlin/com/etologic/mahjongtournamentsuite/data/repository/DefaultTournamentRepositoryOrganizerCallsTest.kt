package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.CreateTournamentRequest
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import com.etologic.mahjongtournamentsuite.domain.model.IdCardStaffCandidate
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountChangeRequest
import com.etologic.mahjongtournamentsuite.domain.model.PlayerCountOutcome
import com.etologic.mahjongtournamentsuite.domain.model.ROUND_HAS_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.RegistrationSettings
import com.etologic.mahjongtournamentsuite.domain.model.ResolvedVenueLink
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TABLE_HAS_ALL_HANDS
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentPlayer
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The organizer's calls that build or change a tournament: create it, change its number of players, read a venue link
 * and the staff of the ID cards. Each one sends its body to its route, gives the server's answer back, and on a refusal
 * gives a failure instead of throwing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentRepositoryOrganizerCallsTest {
    private class Server {
        /** "METHOD /path body" of every request. */
        val requests = mutableListOf<String>()
        var failWith: HttpStatusCode? = null
        var failBody = """{"error":"refused"}"""
        var venuePlace = true
        var broken = false

        fun body(methodAndPath: String) = requests.single { it.startsWith("$methodAndPath ") }.removePrefix("$methodAndPath ")
        fun count(methodAndPath: String) = requests.count { it.startsWith("$methodAndPath ") || it == methodAndPath }
    }

    private fun TestScope.repository(server: Server) = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            server.requests += "${request.method.value} $path $body".trim()
            if (path.endsWith("/sync/manifest")) {
                return@mockBackendApi jsonResponse("""{"resources":{},"globalResources":{},"tableVersions":{}}""")
            }
            if (server.broken) error("The connection broke.")
            server.failWith?.let { return@mockBackendApi respond(ByteReadChannel(server.failBody), it) }
            when {
                path == "/tournaments" && request.method.value == "POST" -> jsonResponse(TOURNAMENT)
                path == "/tournaments" -> jsonResponse("""{"tournaments":[$TOURNAMENT]}""")
                path.endsWith("/player-count") -> jsonResponse(
                    """{"tournament":$TOURNAMENT,"emails":{"sent":2,"noEmail":["10000003"],"failed":["10000004"]}}""",
                )
                path == "/venues/resolve-link" -> jsonResponse(
                    if (server.venuePlace) {
                        """{"url":"https://maps.test/full","place":{"name":"Casa","address":"Calle 1","city":"Madrid",""" +
                            """"countryCode":"ES","timeZone":"Europe/Madrid"}}"""
                    } else {
                        """{"url":"https://maps.test/full"}"""
                    },
                )
                path.endsWith("/id-cards/staff-names") && request.method.value == "PUT" -> jsonResponse(body)
                path.endsWith("/id-cards/staff-names") -> jsonResponse("""{"names":["Ana","Luis"]}""")
                path.endsWith("/id-cards/staff-candidates") -> jsonResponse(
                    """{"candidates":[{"uid":"u1","name":"Ana","role":"EDITOR","inTournament":true},""" +
                        """{"uid":"u2","name":"Luis","role":"SOMETHING_NEW"}]}""",
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultTournamentRepositoryOrganizerCallsTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = TestTimeSource(),
            freshFor = 10.seconds,
        ),
        json = Json { ignoreUnknownKeys = true },
        tableStore = TournamentTableStore(),
    )

    private val players = listOf(TournamentPlayer(id = 1, name = "P1", team = 0, country = "ES"))
    private val tables = listOf(TournamentTable(roundId = 1, tableId = 1, playerIds = listOf(1, 2, 3, 4), isCompleted = false, useTotalsOnly = false, hasProgress = false))

    private fun createRequest(registration: RegistrationSettings? = null) = CreateTournamentRequest(
        name = "Madrid Open",
        eventStartDate = "2027-02-06",
        eventEndDate = "2027-02-07",
        isTeams = false,
        numPlayers = 4,
        numRounds = 1,
        numTries = 10,
        players = players,
        tables = tables,
        shortName = "MO",
        organizerIds = listOf("org1"),
        hostCountry = "ES",
        hostCity = "Madrid",
        timeZone = "Europe/Madrid",
        registration = registration,
    )

    @Test
    fun creatingATournamentSendsItAndReadsTheListAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.IF_CHANGED)
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(1, server.count("GET /tournaments"), "the second read comes from the cache")

        val created = assertIs<AppResult.Success<Tournament>>(repository.createTournament(createRequest())).value

        assertEquals("t1", created.id)
        val sent = server.body("POST /tournaments")
        assertTrue(""""name":"Madrid Open"""" in sent, sent)
        assertTrue(""""players":[{"id":1,"name":"P1","team":0,"country":"ES"""" in sent, sent)
        assertTrue(""""timeZone":"Europe/Madrid"""" in sent, sent)
        assertTrue(""""registration"""" !in sent, "no registrations: $sent")
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments"), "the list is read again with the new tournament")
    }

    @Test
    fun aTournamentWithRegistrationsSendsTheirSettings() = runTest {
        val server = Server()
        val repository = repository(server)

        repository.createTournament(createRequest(RegistrationSettings(maxPlayers = 60, closesOn = "2027-01-31", slug = "madrid")))

        val sent = server.body("POST /tournaments")
        assertTrue(""""maxPlayers":60""" in sent, sent)
        assertTrue(""""slug":"madrid"""" in sent, sent)
    }

    @Test
    fun changingThePlayerCountSendsTheDrawAndGivesTheEmailsBack() = runTest {
        val server = Server()
        val repository = repository(server)

        val outcome = assertIs<AppResult.Success<PlayerCountOutcome>>(
            repository.changePlayerCount(
                PlayerCountChangeRequest(
                    tournamentId = "t1",
                    numTries = 5,
                    players = players,
                    tables = tables,
                    removedSlotIds = listOf(8),
                    waitingEmaIds = listOf("10000009"),
                ),
            ),
        ).value

        assertEquals("t1", outcome.tournament.id)
        assertEquals(2, outcome.emailsSent)
        assertEquals(listOf("10000003"), outcome.noEmail)
        assertEquals(listOf("10000004"), outcome.emailFailed)
        val sent = server.body("POST /tournaments/t1/player-count")
        assertTrue(""""removedSlotIds":[8]""" in sent, sent)
        assertTrue(""""waitingEmaIds":["10000009"]""" in sent, sent)
        assertTrue(""""players":[{"id":1,"team":0}]""" in sent, sent)
    }

    @Test
    fun aVenueLinkGivesThePlaceWhenTheServerFindsOne() = runTest {
        val server = Server()
        val repository = repository(server)

        val found = assertIs<AppResult.Success<ResolvedVenueLink>>(repository.resolveVenueLink("  https://maps.test/x  ")).value
        assertEquals("https://maps.test/full", found.url)
        assertEquals("Casa", found.place?.name)
        assertEquals("Europe/Madrid", found.place?.timeZone)
        assertEquals("""{"url":"https://maps.test/x"}""", server.body("POST /venues/resolve-link"), "the link goes trimmed")

        server.venuePlace = false
        assertNull(assertIs<AppResult.Success<ResolvedVenueLink>>(repository.resolveVenueLink("https://maps.test/x")).value.place)
    }

    @Test
    fun theStaffOfTheIdCardsIsReadAndSaved() = runTest {
        val server = Server()
        val repository = repository(server)

        assertEquals(listOf("Ana", "Luis"), assertIs<AppResult.Success<List<String>>>(repository.loadIdCardStaffNames("t1")).value)
        assertEquals(
            listOf("Eva"),
            assertIs<AppResult.Success<List<String>>>(repository.saveIdCardStaffNames("t1", listOf("Eva"))).value,
        )
        assertEquals("""{"names":["Eva"]}""", server.body("PUT /tournaments/t1/id-cards/staff-names"))

        val candidates = assertIs<AppResult.Success<List<IdCardStaffCandidate>>>(repository.listIdCardStaffCandidates("t1")).value
        assertEquals(
            listOf(
                IdCardStaffCandidate(uid = "u1", name = "Ana", role = GlobalUserRole.EDITOR, inTournament = true),
                IdCardStaffCandidate(uid = "u2", name = "Luis", role = GlobalUserRole.PLAYER, inTournament = false),
            ),
            candidates,
            "a role this app does not know reads as a player",
        )
    }

    @Test
    fun aRefusedCallGivesAFailure() = runTest {
        val server = Server()
        val repository = repository(server)
        server.failWith = HttpStatusCode.InternalServerError

        assertIs<AppResult.Failure>(repository.createTournament(createRequest()))
        assertIs<AppResult.Failure>(
            repository.changePlayerCount(PlayerCountChangeRequest(tournamentId = "t1", numTries = 1, players = players, tables = tables)),
        )
        assertIs<AppResult.Failure>(repository.resolveVenueLink("https://maps.test/x"))
        assertIs<AppResult.Failure>(repository.loadIdCardStaffNames("t1"))
        assertIs<AppResult.Failure>(repository.saveIdCardStaffNames("t1", listOf("Eva")))
        assertIs<AppResult.Failure>(repository.listIdCardStaffCandidates("t1"))
    }

    @Test
    fun aClockChangeOnARoundWithHandsSaysSo() = runTest {
        val server = Server()
        val repository = repository(server)
        server.failWith = HttpStatusCode.Conflict
        server.failBody = """{"error":"$ROUND_HAS_HANDS"}"""

        val failure = assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop))
        assertEquals(AppError.Conflict(ROUND_HAS_HANDS), failure.error)

        server.failBody = """{"error":"something else"}"""
        val other = assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop))
        assertTrue(other.error != AppError.Conflict(ROUND_HAS_HANDS))
    }

    @Test
    fun aControlChangeOnATableWithAllItsHandsSaysSo() = runTest {
        val server = Server()
        val repository = repository(server)
        server.failWith = HttpStatusCode.Conflict
        server.failBody = """{"error":"$TABLE_HAS_ALL_HANDS"}"""

        val failure = assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange(hands = ControlMode.OPEN)))
        assertEquals(AppError.Conflict(TABLE_HAS_ALL_HANDS), failure.error)

        server.failWith = HttpStatusCode.BadRequest
        val other = assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange(hands = ControlMode.OPEN)))
        assertTrue(other.error != AppError.Conflict(TABLE_HAS_ALL_HANDS))
    }

    @Test
    fun theOtherRefusalsOfTheClockAndTheControlKeepTheirOwnError() = runTest {
        val server = Server()
        val repository = repository(server)
        server.failWith = HttpStatusCode.BadRequest
        server.failBody = """{"error":"$ROUND_HAS_HANDS"}"""
        assertTrue(assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop)).error != AppError.Conflict(ROUND_HAS_HANDS))

        server.failWith = HttpStatusCode.Conflict
        server.failBody = """{"error":"something else"}"""
        val control = assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange(hands = ControlMode.OPEN)))
        assertTrue(control.error != AppError.Conflict(TABLE_HAS_ALL_HANDS))

        server.broken = true
        assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop))
        assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange(hands = ControlMode.OPEN)))
    }

    private companion object {
        const val TOURNAMENT = """{"id":"t1","name":"Madrid Open","shortName":"MO","isTeams":false,"numPlayers":4,"numRounds":1,"hostCountry":"ES"}"""
    }
}
