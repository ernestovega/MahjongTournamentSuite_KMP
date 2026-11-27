package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.RoundClockAction
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.Tournament
import com.etologic.mahjongtournamentsuite.domain.model.TournamentVenue
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The writes of the tournament screens of the admin (members, teams, players, name, delete, tables): each call goes to
 * its route with its body, and the lists it changes are read again from the server afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentRepositoryWritesTest {
    private class Server {
        /** "METHOD /path body" of every request. */
        val requests = mutableListOf<String>()
        var failWith: HttpStatusCode? = null

        fun count(methodAndPath: String) = requests.count { it.startsWith("$methodAndPath ") || it == methodAndPath }
    }

    private fun TestScope.repository(server: Server) = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val query = request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
            server.requests += "${request.method.value} $path$query $body".trim()
            server.failWith?.let { return@mockBackendApi respond(ByteReadChannel("""{"error":"refused"}"""), it) }
            when {
                path.endsWith("/sync/manifest") -> jsonResponse("""{"resources":{},"globalResources":{},"tableVersions":{}}""")
                path.endsWith("/members") -> jsonResponse("""{"members":[{"uid":"u1","email":"a@b.c","role":"EDITOR","onlyTournament":true}]}""")
                path.endsWith("/teams") -> jsonResponse("""{"teams":[{"id":2,"name":"Aces","playerIds":[1,2,3,4]}]}""")
                path.endsWith("/players") -> jsonResponse("""{"players":[{"id":5,"name":"P5","team":0}]}""")
                path.endsWith("/rounds") -> jsonResponse("""{"rounds":[{"roundId":1},{"roundId":2}]}""")
                path.endsWith("/complete-open-tables") -> jsonResponse("""{"completed":3}""")
                path.endsWith("/clock") || path.endsWith("/control") || path.endsWith("/round-clocks") -> jsonResponse(CLOCKS)
                path.endsWith("/table-qr-codes") || path.endsWith("/id-list") || path.endsWith("/score-sheets") ->
                    respond(ByteReadChannel("%PDF $path"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
                path == "/me" -> jsonResponse(
                    """{"emaId":"10000001","tournaments":[{"tournamentId":"t1","playerId":7,"teamId":2}],""" +
                        """"registrations":[{"tournamentId":"t2","status":"WAITING"},{"tournamentId":"t3","status":"CONFIRMED"}]}""",
                )
                path.endsWith("/settings") ->
                    jsonResponse("""{"id":"t1","name":"Madrid Open","shortName":"MO","isTeams":false,"numPlayers":4,"numRounds":1,"hostCountry":"ES"}""")
                path == "/tournaments" -> jsonResponse("""{"tournaments":[]}""")
                else -> jsonResponse("""{"ok":true}""")
            }
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultTournamentRepositoryWritesTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = TestTimeSource(),
            freshFor = 10.seconds,
        ),
        json = Json { ignoreUnknownKeys = true },
        tableStore = TournamentTableStore(),
    )

    @Test
    fun aMemberChangeReadsTheMembersAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        val members = assertIs<AppResult.Success<*>>(repository.listTournamentMembers("t1", RefreshMode.IF_CHANGED)).value as List<*>
        assertEquals(1, members.size)
        repository.listTournamentMembers("t1", RefreshMode.IF_CHANGED)
        assertEquals(1, server.count("GET /tournaments/t1/members"), "the second read comes from the cache")

        assertIs<AppResult.Success<Unit>>(repository.upsertTournamentMember("t1", "u2"))
        assertEquals(1, server.count("PUT /tournaments/t1/members/u2"))
        repository.listTournamentMembers("t1", RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments/t1/members"))

        assertIs<AppResult.Success<Unit>>(repository.removeTournamentMember("t1", "u2"))
        assertEquals(1, server.count("DELETE /tournaments/t1/members/u2"))
        repository.listTournamentMembers("t1", RefreshMode.IF_CHANGED)
        assertEquals(3, server.count("GET /tournaments/t1/members"))
    }

    @Test
    fun aTeamChangeSendsTheNameAndTheMembersAndReadsTheTeamsAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournamentTeams("t1", RefreshMode.IF_CHANGED)

        assertIs<AppResult.Success<Unit>>(repository.updateTournamentTeam("t1", 2, "Bamboo", listOf("10000001", null)))

        assertEquals(listOf("""PUT /tournaments/t1/teams/2 {"name":"Bamboo","emaIds":["10000001",null]}"""), server.requests.filter { it.startsWith("PUT") })
        repository.listTournamentTeams("t1", RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments/t1/teams"))
    }

    @Test
    fun anEmaPlayerGoesToAPlayerNumberAndThePlayersAreReadAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournamentPlayers("t1", RefreshMode.IF_CHANGED)

        assertIs<AppResult.Success<Unit>>(repository.assignTournamentPlayer("t1", 5, "10000005"))
        assertIs<AppResult.Success<Unit>>(repository.assignTournamentPlayer("t1", 6, null))

        assertEquals(
            listOf("""PUT /tournaments/t1/players/5 {"emaId":"10000005"}""", "PUT /tournaments/t1/players/6 {}"),
            server.requests.filter { it.startsWith("PUT") },
        )
        repository.listTournamentPlayers("t1", RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments/t1/players"))
    }

    @Test
    fun theNameIsTrimmedAndDeleteAndRenameReadTheTournamentsAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.IF_CHANGED)

        assertIs<AppResult.Success<Unit>>(repository.renameTournament("t1", "  Madrid Open  "))
        assertEquals(listOf("""PUT /tournaments/t1 {"name":"Madrid Open"}"""), server.requests.filter { it.startsWith("PUT") })
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments"))

        assertIs<AppResult.Success<Unit>>(repository.deleteTournament("t1"))
        assertEquals(1, server.count("DELETE /tournaments/t1"))
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(3, server.count("GET /tournaments"))
    }

    @Test
    fun theSettingsAreCleanedBeforeTheyGoAndTheTournamentsAreReadAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.IF_CHANGED)

        val result = repository.updateTournamentSettings(
            tournamentId = "t1",
            name = "  Madrid Open ",
            shortName = " MO ",
            eventStartDate = " 2027-02-06",
            eventEndDate = "2027-02-07 ",
            hostCountry = " es ",
            hostCity = " Madrid ",
            venue = TournamentVenue(name = " Club ", address = " Calle 1 ", mapUrl = " https://maps.example/x ", contactEmail = " info@club.es "),
            timeZone = " Europe/Madrid ",
            isPublic = true,
        )

        assertEquals("Madrid Open", assertIs<AppResult.Success<Tournament>>(result).value.name)
        val body = server.requests.single { it.startsWith("PUT /tournaments/t1/settings ") }
        listOf(
            """"name":"Madrid Open"""", """"shortName":"MO"""", """"eventStartDate":"2027-02-06"""", """"eventEndDate":"2027-02-07"""",
            """"hostCountry":"ES"""", """"hostCity":"Madrid"""", """"venueName":"Club"""", """"venueAddress":"Calle 1"""",
            """"venueMapUrl":"https://maps.example/x"""", """"contactEmail":"info@club.es"""", """"timeZone":"Europe/Madrid"""", """"isPublic":true""",
        ).forEach { assertTrue(it in body, "$it in $body") }
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments"))
    }

    @Test
    fun refusedSettingsAreAFailureAndKeepTheList() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.IF_CHANGED)
        server.failWith = HttpStatusCode.Forbidden

        assertIs<AppResult.Failure>(
            repository.updateTournamentSettings("t1", "X", "X", eventStartDate = "2027-02-06", eventEndDate = "2027-02-06", hostCountry = "ES", hostCity = "Madrid"),
        )
        server.failWith = null
        repository.listTournaments(RefreshMode.IF_CHANGED)
        assertEquals(1, server.count("GET /tournaments"), "a refused change keeps the cached list")
    }

    @Test
    fun closingTablesSendsThemAndGivesHowManyClosed() = runTest {
        val server = Server()
        val result = repository(server).completeTables("t1", listOf(TableKey(1, 2), TableKey(1, 3)))

        assertEquals(3, assertIs<AppResult.Success<Int>>(result).value)
        assertEquals(1, server.count("POST /tournaments/t1/complete-open-tables"))
    }

    @Test
    fun theRoundsAreMapped() = runTest {
        val rounds = assertIs<AppResult.Success<*>>(repository(Server()).listTournamentRounds("t1", RefreshMode.FORCE)).value as List<*>
        assertEquals(2, rounds.size)
    }

    @Test
    fun aRefusedWriteIsAFailureAndChangesNothing() = runTest {
        val server = Server().apply { failWith = HttpStatusCode.Forbidden }
        val repository = repository(server)

        assertIs<AppResult.Failure>(repository.upsertTournamentMember("t1", "u2"))
        assertIs<AppResult.Failure>(repository.removeTournamentMember("t1", "u2"))
        assertIs<AppResult.Failure>(repository.updateTournamentTeam("t1", 2, "Bamboo", emptyList()))
        assertIs<AppResult.Failure>(repository.assignTournamentPlayer("t1", 5, "10000005"))
        assertIs<AppResult.Failure>(repository.renameTournament("t1", "X"))
        assertIs<AppResult.Failure>(repository.deleteTournament("t1"))
        assertIs<AppResult.Failure>(repository.completeTables("t1", emptyList()))
    }

    @Test
    fun aSubstitutionIsSentOrRemovedAndThePlayersAreReadAgain() = runTest {
        val server = Server()
        val repository = repository(server)
        repository.listTournamentPlayers("t1", RefreshMode.IF_CHANGED)

        val substitution = PlayerSubstitution(SubstitutionReason.LATE, fromRound = 2, duringSession = true)
        assertIs<AppResult.Success<Unit>>(repository.setPlayerSubstitution("t1", 5, substitution))
        assertIs<AppResult.Success<Unit>>(repository.setPlayerSubstitution("t1", 5, null))

        assertEquals(
            listOf(
                """PUT /tournaments/t1/players/5/substitution {"reason":"LATE","fromRound":2,"duringSession":true}""",
                "DELETE /tournaments/t1/players/5/substitution",
            ),
            server.requests.filter { "/substitution" in it },
        )
        repository.listTournamentPlayers("t1", RefreshMode.IF_CHANGED)
        assertEquals(2, server.count("GET /tournaments/t1/players"))
    }

    @Test
    fun theTimerSendsItsActionAndGetsTheClocksBack() = runTest {
        val server = Server()
        val repository = repository(server)

        val started = assertIs<AppResult.Success<*>>(repository.updateRoundClock("t1", 2, RoundClockAction.Start(agoSeconds = 30))).value
            as com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
        repository.updateRoundClock("t1", 2, RoundClockAction.Stop)

        assertEquals(1_000L, started.of(2)?.startedAtMs)
        assertEquals(9_000L, started.serverNowMs)
        val sent = server.requests.filter { it.startsWith("POST /tournaments/t1/rounds/2/clock") }
        assertEquals(2, sent.size)
        assertTrue(""""action":"start"""" in sent[0] && """"agoSeconds":30""" in sent[0], sent[0])
        assertTrue(""""action":"stop"""" in sent[1], sent[1])
    }

    @Test
    fun theRoundControlIsSentAndReadBack() = runTest {
        val server = Server()
        val repository = repository(server)

        val clocks = assertIs<AppResult.Success<*>>(
            repository.updateRoundControl("t1", 2, RoundControlChange(hands = ControlMode.CLOSED, completeTables = listOf(4))),
        ).value as com.etologic.mahjongtournamentsuite.domain.model.RoundClocks

        assertEquals(ControlMode.CLOSED, clocks.controls.single().hands)
        val sent = server.requests.single { it.startsWith("POST /tournaments/t1/rounds/2/control") }
        assertTrue(""""completeTables":[4]""" in sent, sent)
        val read = assertIs<AppResult.Success<*>>(repository.getRoundClocks("t1")).value as com.etologic.mahjongtournamentsuite.domain.model.RoundClocks
        assertEquals(listOf(2), read.clocks.map { it.roundId })
    }

    @Test
    fun theOwnTournamentsGiveTheSlotsAndTheWaitingRegistrations() = runTest {
        val repository = repository(Server())

        val mine = assertIs<AppResult.Success<*>>(repository.listMyTournaments()).value as com.etologic.mahjongtournamentsuite.domain.model.MyTournaments
        assertEquals(listOf(Triple("t1", 7, 2)), mine.slots.map { Triple(it.tournamentId, it.playerId, it.teamId) })
        assertEquals(listOf("t2" to true, "t3" to false), mine.registrations.map { it.tournamentId to it.waiting })
        assertEquals(mine.slots, assertIs<AppResult.Success<*>>(repository.listMyTournamentSlots()).value)
    }

    @Test
    fun refusedTimerAndSubstitutionCallsAreFailures() = runTest {
        val repository = repository(Server().apply { failWith = HttpStatusCode.Forbidden })

        assertIs<AppResult.Failure>(repository.updateRoundClock("t1", 1, RoundClockAction.Stop))
        assertIs<AppResult.Failure>(repository.updateRoundControl("t1", 1, RoundControlChange(seats = ControlMode.OPEN)))
        assertIs<AppResult.Failure>(repository.getRoundClocks("t1"))
        assertIs<AppResult.Failure>(repository.setPlayerSubstitution("t1", 5, null))
        assertIs<AppResult.Failure>(repository.listMyTournaments())
    }

    @Test
    fun theTableCardsAndTheIdListAreDownloadedOnceUntilTheirDataChanges() = runTest {
        val server = Server()
        val repository = repository(server)

        val cards = assertIs<AppResult.Success<ByteArray>>(repository.generateTableQrCodes("t1", perSheet = 4)).value
        assertIs<AppResult.Success<ByteArray>>(repository.generateTableQrCodes("t1", perSheet = 4))
        val list = assertIs<AppResult.Success<ByteArray>>(repository.generateTournamentIdList("t1")).value

        assertEquals("%PDF /tournaments/t1/table-qr-codes", cards.decodeToString())
        assertEquals("%PDF /tournaments/t1/id-list", list.decodeToString())
        assertEquals(listOf("GET /tournaments/t1/table-qr-codes?perSheet=4"), server.requests.filter { "table-qr-codes" in it })
    }

    @Test
    fun theScoreSheetsAreDownloadedOnceForEachChoice() = runTest {
        val server = Server()
        val repository = repository(server)

        val blank = assertIs<AppResult.Success<ByteArray>>(repository.generateScoreSheets("t1", perTable = false, emaLogo = true)).value
        assertIs<AppResult.Success<ByteArray>>(repository.generateScoreSheets("t1", perTable = false, emaLogo = true))
        assertIs<AppResult.Success<ByteArray>>(repository.generateScoreSheets("t1", perTable = true, emaLogo = false))

        assertEquals("%PDF /tournaments/t1/score-sheets", blank.decodeToString())
        assertEquals(
            listOf("GET /tournaments/t1/score-sheets?mode=blank&ema=true", "GET /tournaments/t1/score-sheets?mode=tables&ema=false"),
            server.requests.filter { "score-sheets" in it },
        )
    }

    private companion object {
        const val CLOCKS = """{"roundClocks":[{"roundId":2,"startedAtMs":1000}],"serverNowMs":9000,""" +
            """"roundControls":[{"roundId":2,"hands":"closed"}]}"""
    }
}
