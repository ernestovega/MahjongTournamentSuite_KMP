package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.PersistedValues
import com.etologic.mahjongtournamentsuite.data.cache.PersistentCacheStore
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The device. It keeps the saved answers while the app starts again. */
private class DeviceStore : PersistentCacheStore {
    val values = mutableMapOf<String, String>()
    override suspend fun read(name: String): String? = values[name]
    override suspend fun write(name: String, value: String) {
        values[name] = value
    }
    override suspend fun clear() = values.clear()
}

/** A backend whose data and revisions the tests change between two starts of the app. */
private class ChangingServer {
    val requests = mutableListOf<String>()
    var playersRevision = 1L
    var teamsRevision = 1L
    var tournamentsRevision = 1L
    var emaPlayersRevision = 1L
    var playerTeam = 1
    var teamName = "Team A"
    var tournamentName = "Open"
    var emaPlayerName = "Ada"
    val tableVersions = mutableMapOf("1_1" to 1L, "1_2" to 1L)
    val tableEast = mutableMapOf("1_1" to "1", "1_2" to "2")

    fun count(suffix: String) = requests.count { it.endsWith(suffix) }

    fun tableJson(key: String): String {
        val (round, table) = key.split("_")
        return """{"table":{"version":${tableVersions.getValue(key)},"roundId":$round,"tableId":$table,""" +
            """"playerIds":[1,2,3,4],"playerEastId":"${tableEast.getValue(key)}"},"hands":[{"handId":1,"handScore":"8"}]}"""
    }

    fun tournamentManifestJson() = """{"resources":{"tables":{"revision":${tableVersions.values.sum()}},""" +
        """"players":{"revision":$playersRevision},"teams":{"revision":$teamsRevision}},""" +
        """"globalResources":{"tournaments":{"revision":$tournamentsRevision},"emaPlayers":{"revision":$emaPlayersRevision},""" +
        """"countries":{"revision":1},"users":{"revision":1}},"tableVersions":{""" +
        tableVersions.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}}"

    fun globalManifestJson() = """{"resources":{"tournaments":{"revision":$tournamentsRevision},""" +
        """"emaPlayers":{"revision":$emaPlayersRevision},"countries":{"revision":1},"users":{"revision":1}}}"""
}

/**
 * The saved answers must never hide a change. Each test starts the app, changes the server, and starts the app
 * again with the same device, as a reload of the web page does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersistedRepositoryFreshnessTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val device = DeviceStore()
    private val server = ChangingServer()

    private class App(
        val tournaments: DefaultTournamentRepository,
        val players: DefaultPlayerRepository,
        val cache: SessionRepositoryCache,
    )

    private fun TestScope.startApp(clock: TestTimeSource = TestTimeSource()): App {
        val persisted = PersistedValues(device, json)
        val cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = clock,
            freshFor = 10.seconds,
            persisted = persisted,
        )
        val backend = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            server.requests += "${request.method.value} $path"
            when {
                path == "/sync/manifest" -> jsonResponse(server.globalManifestJson())
                path.endsWith("/sync/manifest") -> jsonResponse(server.tournamentManifestJson())
                path == "/tournaments" ->
                    jsonResponse("""{"tournaments":[{"id":"t1","name":"${server.tournamentName}","isTeams":true,"numPlayers":4,"numRounds":1}]}""")
                path.endsWith("/players") -> jsonResponse("""{"players":[{"id":1,"name":"P1","team":${server.playerTeam}}]}""")
                path.endsWith("/teams") -> jsonResponse("""{"teams":[{"id":1,"name":"${server.teamName}","playerIds":[1]}]}""")
                path.endsWith("/tables-with-hands") ->
                    jsonResponse("""{"tables":[${server.tableVersions.keys.sorted().joinToString(",") { server.tableJson(it) }}]}""")
                path == "/ema-player-registry" ->
                    jsonResponse("""{"players":[{"emaId":"1","firstName":"${server.emaPlayerName}","lastName":"L"}]}""")
                else -> respond(ByteReadChannel("unexpected $path"), HttpStatusCode.NotFound)
            }
        }
        val auth = FakeAuthRepository()
        return App(
            tournaments = DefaultTournamentRepository(
                backendApi = backend,
                authRepository = auth,
                logger = Logger.withTag("tournaments"),
                cache = cache,
                json = json,
                tableStore = TournamentTableStore(),
                persisted = persisted,
            ),
            players = DefaultPlayerRepository(backend, auth, Logger.withTag("players"), cache),
            cache = cache,
        )
    }

    private fun <T> AppResult<T>.value(): T = assertIs<AppResult.Success<T>>(this).value

    private suspend fun App.east(mode: RefreshMode = RefreshMode.IF_CHANGED) = tournaments.getAllTablesWithHands("t1", mode).value().map { it.first.playerEastId }

    // --- Lists ---

    @Test
    fun anUnchangedListIsNotDownloadedAgainAfterARestart() = runTest {
        startApp().tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()

        val players = startApp().tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()

        assertEquals(1, players.single().team)
        assertEquals(1, server.count("/t1/players"))
    }

    @Test
    fun aListThatChangedWhileTheAppWasClosedIsShownNew() = runTest {
        startApp().tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()
        server.playerTeam = 2
        server.playersRevision = 2

        val players = startApp().tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()

        assertEquals(2, players.single().team)
    }

    @Test
    fun aCachedReadAfterARestartStillChecksTheRevision() = runTest {
        startApp().tournaments.listTournamentTeams("t1", RefreshMode.IF_CHANGED).value()
        server.teamName = "Team B"
        server.teamsRevision = 2

        val teams = startApp().tournaments.listTournamentTeams("t1", RefreshMode.CACHED).value()

        assertEquals("Team B", teams.single().name)
    }

    @Test
    fun aForcedReadAfterARestartAlwaysDownloads() = runTest {
        startApp().tournaments.listTournamentTeams("t1", RefreshMode.IF_CHANGED).value()

        startApp().tournaments.listTournamentTeams("t1", RefreshMode.FORCE).value()

        assertEquals(2, server.count("/t1/teams"))
    }

    @Test
    fun theTournamentListThatChangedIsShownNewAfterARestart() = runTest {
        startApp().tournaments.listTournaments(RefreshMode.IF_CHANGED).value()
        server.tournamentName = "Open 2026"
        server.tournamentsRevision = 2

        assertEquals("Open 2026", startApp().tournaments.listTournaments(RefreshMode.IF_CHANGED).value().single().name)
    }

    @Test
    fun theEmaPlayersThatChangedAreShownNewAfterARestart() = runTest {
        startApp().players.listPlayers(RefreshMode.IF_CHANGED).value()
        server.emaPlayerName = "Grace"
        server.emaPlayersRevision = 2

        assertEquals("Grace", startApp().players.listPlayers(RefreshMode.IF_CHANGED).value().single().firstName)
    }

    @Test
    fun unchangedEmaPlayersAreNotDownloadedAgainAfterARestart() = runTest {
        startApp().players.listPlayers(RefreshMode.IF_CHANGED).value()

        startApp().players.listPlayers(RefreshMode.IF_CHANGED).value()

        assertEquals(1, server.count("/ema-player-registry"))
    }

    // --- Polling after a restart ---

    @Test
    fun pollingStillMarksARestoredListOldWhenItChanges() = runTest {
        val clock = TestTimeSource()
        startApp().tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()
        val app = startApp(clock)
        app.tournaments.listTournamentPlayers("t1", RefreshMode.CACHED).value()
        app.tournaments.getTournamentManifest("t1").value()

        server.playerTeam = 3
        server.playersRevision = 2
        clock += 3.seconds
        app.tournaments.getTournamentManifest("t1").value()

        assertEquals(3, app.tournaments.listTournamentPlayers("t1", RefreshMode.CACHED).value().single().team)
    }

    // --- Tables ---

    @Test
    fun unchangedTablesAreNotDownloadedAgainAfterARestart() = runTest {
        startApp().east()

        assertEquals(listOf("1", "2"), startApp().east())
        assertEquals(1, server.count("/tables-with-hands"))
    }

    @Test
    fun aTableThatSomeoneSavedWhileTheAppWasClosedIsShownNew() = runTest {
        startApp().east()
        server.tableEast["1_2"] = "9"
        server.tableVersions["1_2"] = 2

        assertEquals(listOf("1", "9"), startApp().east())
    }

    @Test
    fun aCachedTableReadAfterARestartStillChecksTheTableVersions() = runTest {
        startApp().east()
        server.tableEast["1_1"] = "7"
        server.tableVersions["1_1"] = 5

        assertEquals(listOf("7", "2"), startApp().east(RefreshMode.CACHED))
    }

    @Test
    fun aTableChangeAfterTheRestartIsSeenByTheNextIfChangedRead() = runTest {
        val clock = TestTimeSource()
        startApp().east()
        val app = startApp(clock)
        app.east()

        server.tableEast["1_1"] = "8"
        server.tableVersions["1_1"] = 2
        clock += 3.seconds

        assertEquals(listOf("8", "2"), app.east())
    }

    @Test
    fun theNewestTablesAreSavedSoTheNextRestartNeedsNoDownload() = runTest {
        startApp().east()
        server.tableEast["1_2"] = "9"
        server.tableVersions["1_2"] = 2
        startApp().east()

        assertEquals(listOf("1", "9"), startApp().east())
        assertEquals(2, server.count("/tables-with-hands"))
    }

    @Test
    fun aForcedTableReadAfterARestartAlwaysDownloads() = runTest {
        startApp().east()

        startApp().east(RefreshMode.FORCE)

        assertEquals(2, server.count("/tables-with-hands"))
    }

    // --- Accounts ---

    @Test
    fun signingOutRemovesEverySavedAnswer() = runTest {
        val app = startApp()
        app.east()
        app.tournaments.listTournamentPlayers("t1", RefreshMode.IF_CHANGED).value()
        assertTrue(device.values.isNotEmpty())

        app.cache.clear()

        assertTrue(device.values.isEmpty())
    }
}
