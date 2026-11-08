package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.data.cache.globalManifest
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * A refresh of the player tournament screen asks for the tournament manifest once. Its global revisions answer the
 * checks of the tournament list, the EMA players and the profile, so a refresh without changes costs one request.
 * A change of any revision must still be seen by the next refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleManifestPollTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val requests = mutableListOf<String>()
    private var tournamentsRevision = 1L
    private var emaPlayersRevision = 1L
    private var usersRevision = 1L
    private var playersRevision = 1L
    private var teamsRevision = 1L
    private var tableVersion = 1L
    private var tournamentName = "Open"
    private var emaPlayerName = "Ada"
    // The app names the slots itself, so a change of a slot shows in its team.
    private var playerTeam = 1
    private var teamName = "Team A"
    private var tableEast = "1"

    /** An old backend sends no global revisions inside the tournament manifest. */
    private var manifestHasGlobalRevisions = true

    private val clock = TestTimeSource()
    private lateinit var cache: SessionRepositoryCache
    private lateinit var tournaments: DefaultTournamentRepository
    private lateinit var players: DefaultPlayerRepository

    private fun count(path: String) = requests.count { it == "GET $path" }
    private val tournamentManifests get() = count("/tournaments/t1/sync/manifest")
    private val globalManifests get() = count("/sync/manifest")
    private val dataRequests get() = requests.count { !it.endsWith("/sync/manifest") }

    private fun tournamentManifestJson(): String {
        val global = """"globalResources":{"tournaments":{"revision":$tournamentsRevision},""" +
            """"emaPlayers":{"revision":$emaPlayersRevision},"countries":{"revision":1},"users":{"revision":$usersRevision}},"""
        return """{"resources":{"tables":{"revision":$tableVersion},"players":{"revision":$playersRevision},""" +
            """"teams":{"revision":$teamsRevision}},${if (manifestHasGlobalRevisions) global else ""}""" +
            """"tableVersions":{"1_1":$tableVersion}}"""
    }

    private fun globalManifestJson() = """{"resources":{"tournaments":{"revision":$tournamentsRevision},""" +
        """"emaPlayers":{"revision":$emaPlayersRevision},"countries":{"revision":1},"users":{"revision":$usersRevision}}}"""

    @BeforeTest
    fun reset() {
        requests.clear()
    }

    private fun TestScope.startApp() {
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = clock,
            freshFor = 10.seconds,
        )
        val backend = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            requests += "${request.method.value} $path"
            when {
                path == "/sync/manifest" -> jsonResponse(globalManifestJson())
                path == "/tournaments/t1/sync/manifest" -> jsonResponse(tournamentManifestJson())
                path == "/tournaments" ->
                    jsonResponse("""{"tournaments":[{"id":"t1","name":"$tournamentName","isTeams":true,"numPlayers":4,"numRounds":1}]}""")
                path.endsWith("/players") -> jsonResponse("""{"players":[{"id":1,"name":"P1","team":$playerTeam}]}""")
                path.endsWith("/teams") -> jsonResponse("""{"teams":[{"id":1,"name":"$teamName","playerIds":[1]}]}""")
                path.endsWith("/tables-with-hands") -> jsonResponse(
                    """{"tables":[{"table":{"version":$tableVersion,"roundId":1,"tableId":1,"playerIds":[1,2,3,4],""" +
                        """"playerEastId":"$tableEast"},"hands":[{"handId":1,"handScore":"8"}]}]}""",
                )
                path == "/ema-player-registry" ->
                    jsonResponse("""{"players":[{"emaId":"1","firstName":"$emaPlayerName","lastName":"L"}]}""")
                else -> respond(ByteReadChannel("unexpected $path"), HttpStatusCode.NotFound)
            }
        }
        val auth = FakeAuthRepository()
        tournaments = DefaultTournamentRepository(
            backendApi = backend,
            authRepository = auth,
            logger = Logger.withTag("tournaments"),
            cache = cache,
            json = json,
            tableStore = TournamentTableStore(),
        )
        players = DefaultPlayerRepository(backend, auth, Logger.withTag("players"), cache)
    }

    private fun <T> AppResult<T>.value(): T = assertIs<AppResult.Success<T>>(this).value

    private class Screen(val tournament: String, val playerTeam: Int, val team: String, val emaPlayer: String, val east: String)

    /** What the player tournament screen reads on every refresh, in the same order. */
    private suspend fun refresh(mode: RefreshMode = RefreshMode.IF_CHANGED, prefetch: Boolean = true): Screen {
        if (prefetch) tournaments.prefetchRevisions("t1")
        val tournament = tournaments.listTournaments(mode).value().single { it.id == "t1" }
        val player = tournaments.listTournamentPlayers("t1", mode).value().single()
        val emaPlayer = players.listPlayers(mode).value().single()
        val tables = tournaments.getAllTablesWithHands("t1", mode).value()
        val team = tournaments.listTournamentTeams("t1", mode).value().single()
        return Screen(tournament.name, player.team, team.name, emaPlayer.firstName, tables.single().first.playerEastId)
    }

    /** The next poll comes after the cached values stopped being fresh. */
    private suspend fun nextPoll(mode: RefreshMode = RefreshMode.IF_CHANGED): Screen {
        clock += 15.seconds
        requests.clear()
        return refresh(mode)
    }

    @Test
    fun aRefreshWithoutChangesCostsOneRequest() = runTest {
        startApp()
        refresh()

        nextPoll()

        assertEquals(listOf("GET /tournaments/t1/sync/manifest"), requests)
    }

    @Test
    fun theFirstLoadAlsoAsksForTheRevisionsOnlyOnce() = runTest {
        startApp()

        refresh()

        assertEquals(1, tournamentManifests)
        assertEquals(0, globalManifests)
    }

    @Test
    fun aRefreshEveryFiveSecondsStillCostsOneRequestEachTime() = runTest {
        startApp()
        refresh()

        repeat(4) {
            clock += 5.seconds
            requests.clear()
            refresh()
            assertEquals(listOf("GET /tournaments/t1/sync/manifest"), requests)
        }
    }

    @Test
    fun aChangedTournamentListIsSeenByTheNextRefresh() = runTest {
        startApp()
        refresh()
        tournamentsRevision++
        tournamentName = "Open renamed"

        val screen = nextPoll()

        assertEquals("Open renamed", screen.tournament)
        assertEquals(0, globalManifests)
        assertEquals(1, count("/tournaments"))
    }

    @Test
    fun changedEmaPlayersAreSeenByTheNextRefresh() = runTest {
        startApp()
        refresh()
        emaPlayersRevision++
        emaPlayerName = "Grace"

        val screen = nextPoll()

        assertEquals("Grace", screen.emaPlayer)
        assertEquals(0, globalManifests)
        assertEquals(1, count("/ema-player-registry"))
    }

    @Test
    fun changedTournamentPlayersAndTeamsAreSeenByTheNextRefresh() = runTest {
        startApp()
        refresh()
        playersRevision++
        playerTeam = 2
        teamsRevision++
        teamName = "Team B"

        val screen = nextPoll()

        assertEquals(2, screen.playerTeam)
        assertEquals("Team B", screen.team)
        assertEquals(1, tournamentManifests)
    }

    @Test
    fun aSavedTableIsSeenByTheNextRefresh() = runTest {
        startApp()
        refresh()
        tableVersion++
        tableEast = "2"

        val screen = nextPoll()

        assertEquals("2", screen.east)
        assertEquals(1, count("/tournaments/t1/tables-with-hands"))
        assertEquals(1, tournamentManifests)
    }

    @Test
    fun onlyTheChangedDataIsDownloaded() = runTest {
        startApp()
        refresh()
        emaPlayersRevision++

        nextPoll()

        assertEquals(listOf("GET /tournaments/t1/sync/manifest", "GET /ema-player-registry"), requests)
    }

    @Test
    fun aChangeRightAfterARefreshIsSeenByTheNextOne() = runTest {
        startApp()
        refresh()
        // The change happens between two polls, while the revisions of the last poll are still shared.
        clock += 1.seconds
        tournamentsRevision++
        tournamentName = "Open renamed"

        assertEquals("Open renamed", nextPoll().tournament)
    }

    @Test
    fun theSharedGlobalRevisionsExpireAfterTwoSeconds() = runTest {
        startApp()
        tournaments.prefetchRevisions("t1")
        clock += 3.seconds
        requests.clear()

        cache.globalManifest(mockFreeBackend(), "id-token")

        assertEquals(1, globalManifestsAsked)
    }

    @Test
    fun withinTwoSecondsTheProfileCheckUsesTheTournamentManifest() = runTest {
        startApp()
        tournaments.prefetchRevisions("t1")
        usersRevision = 7

        val global = cache.globalManifest(mockFreeBackend(), "id-token")

        assertEquals(0, globalManifestsAsked)
        // The value from the tournament manifest, received a moment ago.
        assertEquals(1L, global.resources.getValue("users").revision)
    }

    @Test
    fun anOldBackendWithoutGlobalRevisionsStillWorks() = runTest {
        manifestHasGlobalRevisions = false
        startApp()
        refresh()
        tournamentsRevision++
        tournamentName = "Open renamed"

        val screen = nextPoll()

        assertEquals("Open renamed", screen.tournament)
        assertEquals(1, globalManifests)
    }

    @Test
    fun withoutThePrefetchTheReadsStillSeeEveryChange() = runTest {
        startApp()
        refresh(prefetch = false)
        tournamentsRevision++
        tournamentName = "Open renamed"
        emaPlayersRevision++
        emaPlayerName = "Grace"
        tableVersion++
        tableEast = "2"

        clock += 15.seconds
        val screen = refresh(prefetch = false)

        assertEquals("Open renamed", screen.tournament)
        assertEquals("Grace", screen.emaPlayer)
        assertEquals("2", screen.east)
    }

    @Test
    fun aWriteMakesTheNextReadAskAgain() = runTest {
        startApp()
        refresh()
        cache.markStale("tournament:t1:players")
        playersRevision++
        playerTeam = 3
        requests.clear()

        // Right after the write, without waiting for a poll.
        val player = tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value().single()

        assertEquals(3, player.team)
    }

    @Test
    fun aForcedRefreshDownloadsEverythingWithOneManifestRequest() = runTest {
        startApp()
        refresh()

        nextPoll(RefreshMode.FORCE)

        assertEquals(1, tournamentManifests)
        assertEquals(0, globalManifests)
        assertEquals(5, dataRequests)
    }

    // --- Helpers for the direct checks of the shared global revisions ---

    private var globalManifestsAsked = 0

    /** A backend for [globalManifest] that counts the requests without changing [requests]. */
    private fun mockFreeBackend() = mockBackendApi { request ->
        val path = request.url.encodedPath.removePrefix("/functions")
        if (path == "/sync/manifest") globalManifestsAsked++
        jsonResponse(globalManifestJson())
    }
}
