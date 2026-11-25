package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
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
            server.requests += "${request.method.value} $path $body".trim()
            server.failWith?.let { return@mockBackendApi respond(ByteReadChannel("""{"error":"refused"}"""), it) }
            when {
                path.endsWith("/sync/manifest") -> jsonResponse("""{"resources":{},"globalResources":{},"tableVersions":{}}""")
                path.endsWith("/members") -> jsonResponse("""{"members":[{"uid":"u1","email":"a@b.c","role":"EDITOR","onlyTournament":true}]}""")
                path.endsWith("/teams") -> jsonResponse("""{"teams":[{"id":2,"name":"Aces","playerIds":[1,2,3,4]}]}""")
                path.endsWith("/players") -> jsonResponse("""{"players":[{"id":5,"name":"P5","team":0}]}""")
                path.endsWith("/rounds") -> jsonResponse("""{"rounds":[{"roundId":1},{"roundId":2}]}""")
                path.endsWith("/complete-open-tables") -> jsonResponse("""{"completed":3}""")
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
}
