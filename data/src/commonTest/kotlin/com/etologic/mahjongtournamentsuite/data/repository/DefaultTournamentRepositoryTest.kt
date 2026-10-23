package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.TableKey
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** A scripted backend. It counts the requests, so the tests can see which calls the repository avoids. */
private class FakeTournamentServer {
    val requests = mutableListOf<String>()
    var tablesRevision = 1L
    var tableVersions = mutableMapOf("1_1" to 1L)
    var tableVersion = 1L
    var eastPlayer = "1"
    var saveStatus = HttpStatusCode.OK
    var saveBody: String? = null
    var manifestStatus = HttpStatusCode.OK
    var playersRevision = 1L
    var teamsRevision = 1L
    var tournamentsRevision = 1L
    var emaPlayersRevision = 1L
    var countriesRevision = 1L
    var usersRevision = 1L

    fun count(path: String) = requests.count { it.endsWith(path) }

    fun tableJson(version: Long = tableVersion, east: String = eastPlayer) =
        """{"table":{"version":$version,"roundId":1,"tableId":1,"playerIds":[1,2,3,4],"playerEastId":"$east"},""" +
            """"hands":[{"handId":1,"handScore":"8"}]}"""

    fun manifestJson() = """{"resources":{"tables":{"revision":$tablesRevision},"players":{"revision":$playersRevision},""" +
        """"teams":{"revision":$teamsRevision}},"globalResources":{"tournaments":{"revision":$tournamentsRevision},""" +
        """"emaPlayers":{"revision":$emaPlayersRevision},"countries":{"revision":$countriesRevision},""" +
        """"users":{"revision":$usersRevision}},"tableVersions":{""" +
        tableVersions.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + """}}"""
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun kotlinx.coroutines.test.TestScope.repository(
        server: FakeTournamentServer,
        clock: TestTimeSource = TestTimeSource(),
    ): DefaultTournamentRepository = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            server.requests += "${request.method.value} $path"
            when {
                path.endsWith("/sync/manifest") ->
                    if (server.manifestStatus == HttpStatusCode.OK) {
                        jsonResponse(server.manifestJson())
                    } else {
                        respond(ByteReadChannel("manifest failed"), server.manifestStatus, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
                    }
                path.endsWith("/state") ->
                    respond(
                        content = ByteReadChannel(server.saveBody ?: server.tableJson(version = server.tableVersion + 1)),
                        status = server.saveStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                path.endsWith("/players") -> jsonResponse("""{"players":[{"id":1,"name":"P1","team":0}]}""")
                path.endsWith("/teams") -> jsonResponse("""{"teams":[]}""")
                path.endsWith("/id-list") || path.endsWith("/ema-report") ->
                    respond(ByteReadChannel("file"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                path == "/tournaments" -> jsonResponse("""{"tournaments":[]}""")
                path == "/countries" -> jsonResponse("""{"countries":[]}""")
                path.endsWith("/reset") -> jsonResponse("""{"ok":true}""")
                path.endsWith("/tables-with-hands") ->
                    jsonResponse("""{"tables":[${server.tableJson()}]}""")
                path.endsWith("/tables/1/1") -> jsonResponse(server.tableJson())
                else -> respond(ByteReadChannel("unexpected $path"), HttpStatusCode.NotFound)
            }
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultTournamentRepositoryTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = clock,
            freshFor = 10.seconds,
        ),
        json = json,
        tableStore = TournamentTableStore(),
    )

    private suspend fun DefaultTournamentRepository.table(mode: RefreshMode = RefreshMode.IF_CHANGED) =
        getTableWithHands("t1", 1, 1, mode)

    private suspend fun DefaultTournamentRepository.save(expectedVersion: Long = 1, east: String = "9") =
        saveTableState("t1", 1, 1, expectedVersion, mapOf("playerEastId" to east), emptyMap())

    @Test
    fun manifestParsesTableVersionsAndSkipsMalformedKeys() = runTest {
        val server = FakeTournamentServer().apply {
            tablesRevision = 7
            tableVersions = mutableMapOf("1_1" to 3L, "2_4" to 9L, "bad" to 1L, "1_x" to 2L, "1_2_3" to 4L)
        }

        val manifest = assertIs<AppResult.Success<*>>(repository(server).getTournamentManifest("t1")).value
            as com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest

        assertEquals(7, manifest.tablesRevision)
        assertEquals(mapOf(TableKey(1, 1) to 3L, TableKey(2, 4) to 9L), manifest.tableVersions)
    }

    @Test
    fun manifestWithoutTableVersionsIsEmpty() = runTest {
        val server = FakeTournamentServer().apply { tableVersions = mutableMapOf() }

        val manifest = assertIs<AppResult.Success<*>>(repository(server).getTournamentManifest("t1")).value
            as com.etologic.mahjongtournamentsuite.domain.model.TournamentManifest

        assertTrue(manifest.tableVersions.isEmpty())
    }

    @Test
    fun manifestCallAlwaysAsksTheBackend() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)

        repository.getTournamentManifest("t1")
        repository.getTournamentManifest("t1")

        assertEquals(2, server.count("/sync/manifest"))
    }

    @Test
    fun manifestFailureBecomesAFailureResult() = runTest {
        val server = FakeTournamentServer().apply { manifestStatus = HttpStatusCode.NotFound }

        assertIs<AppResult.Failure>(repository(server).getTournamentManifest("t1"))
    }

    @Test
    fun cachedLoadsOfOneScreenShareOneManifestRequest() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)

        repository.table()
        repository.getRoundTablesWithHandsOrNull()
        repository.listTournamentTables("t1", refreshMode = RefreshMode.IF_CHANGED)

        assertEquals(1, server.count("/sync/manifest"))
    }

    @Test
    fun manifestFromPollingIsReusedByTheNextCachedLoad() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)

        repository.getTournamentManifest("t1")
        repository.table()

        assertEquals(1, server.count("/sync/manifest"))
    }

    @Test
    fun cachedTableIsServedWithoutAnyRequestInsideTheFreshWindow() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.table()
        val before = server.requests.size

        clock += 9.seconds
        repository.table()

        assertEquals(before, server.requests.size)
    }

    @Test
    fun oldCachedTableLoadsAgainOnlyWhenTheRevisionChanged() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.table()

        clock += 11.seconds
        repository.table()
        assertEquals(1, server.count("/tables/1/1"))

        server.tablesRevision = 2
        server.eastPlayer = "5"
        clock += 11.seconds
        val table = assertIs<AppResult.Success<Pair<TableState, *>>>(repository.table()).value.first

        assertEquals(2, server.count("/tables/1/1"))
        assertEquals("5", table.playerEastId)
    }

    @Test
    fun tableLoadsAgainRightAfterOwnSaveInsteadOfShowingTheOldCopy() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.table()

        repository.save()
        server.eastPlayer = "9"
        val table = assertIs<AppResult.Success<Pair<TableState, *>>>(repository.table()).value.first

        assertEquals(2, server.count("/tables/1/1"))
        assertEquals("9", table.playerEastId)
    }

    @Test
    fun saveDropsTheSharedManifestSoTheNextLoadSeesTheNewRevision() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.table()

        server.tablesRevision = 2
        repository.save()
        repository.table()

        assertEquals(2, server.count("/sync/manifest"))
    }

    @Test
    fun saveReturnsTheTableWithTheNewVersion() = runTest {
        val server = FakeTournamentServer()
        val result = assertIs<AppResult.Success<Pair<TableState, *>>>(repository(server).save(expectedVersion = 1))

        assertEquals(2, result.value.first.version)
    }

    @Test
    fun conflictCarriesTheServerTableAndMarksTheCachedCopiesOld() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.table()
        server.saveStatus = HttpStatusCode.Conflict
        server.saveBody = """{"error":"conflict","details":{"expectedVersion":1,"currentVersion":4,"current":${server.tableJson(version = 4, east = "7")}}}"""

        val error = assertIs<AppError.Conflict>(assertIs<AppResult.Failure>(repository.save()).error)

        assertEquals(1, error.expectedVersion)
        assertEquals(4, error.currentVersion)
        assertEquals("7", error.currentTable?.playerEastId)
        assertEquals(1, error.currentHands.size)
        server.eastPlayer = "7"
        repository.table()
        assertEquals(2, server.count("/tables/1/1"))
    }

    @Test
    fun conflictWithAnUnreadableBodyStillGivesAConflictWithoutATable() = runTest {
        val server = FakeTournamentServer().apply {
            saveStatus = HttpStatusCode.Conflict
            saveBody = "<html>proxy error</html>"
        }

        val error = assertIs<AppError.Conflict>(assertIs<AppResult.Failure>(repository(server).save()).error)

        assertNull(error.currentTable)
        assertTrue(error.currentHands.isEmpty())
    }

    @Test
    fun otherSaveFailuresAreNotConflictsAndKeepTheCache() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.table()
        server.saveStatus = HttpStatusCode.InternalServerError
        server.saveBody = "boom"

        val error = assertIs<AppResult.Failure>(repository.save()).error
        repository.table()

        assertTrue(error !is AppError.Conflict)
        assertEquals(1, server.count("/tables/1/1"))
    }

    private suspend fun DefaultTournamentRepository.getRoundTablesWithHandsOrNull() = Unit

    // ---- all tables for the rankings

    @Test
    fun rankingLoadMakesOneRequestForAllTables() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)

        val tables = assertIs<AppResult.Success<List<Pair<TableState, *>>>>(repository.getAllTablesWithHands("t1")).value

        assertEquals(1, tables.size)
        assertEquals(1, server.count("/tables-with-hands"))
        assertEquals(0, server.count("/tables/1/1"))
    }

    @Test
    fun secondRankingLoadMakesNoRequest() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")
        val before = server.requests.size

        repository.getAllTablesWithHands("t1", RefreshMode.CACHED)

        assertEquals(before, server.requests.size)
    }

    @Test
    fun rankingSeesTheUsersOwnSaveWithoutAnyRequest() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")
        repository.save(expectedVersion = 1, east = "9")
        val before = server.requests.size

        val tables = assertIs<AppResult.Success<List<Pair<TableState, *>>>>(
            repository.getAllTablesWithHands("t1", RefreshMode.CACHED),
        ).value

        assertEquals(2L, tables.single().first.version)
        assertEquals(before, server.requests.size)
    }

    @Test
    fun rankingUsesTablesTheEditorAlreadyLoaded() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.table()
        server.tablesRevision = 2
        server.tableVersions["1_1"] = 2
        server.tableVersion = 2
        server.eastPlayer = "5"
        clock += 11.seconds

        // The editor holds version 1. The store is not complete, so the first ranking load fetches all tables.
        val tables = assertIs<AppResult.Success<List<Pair<TableState, *>>>>(repository.getAllTablesWithHands("t1")).value

        assertEquals("5", tables.single().first.playerEastId)
    }

    @Test
    fun rankingNeverAsksTheServerAfterTheFirstLoadEvenMuchLater() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.getAllTablesWithHands("t1")
        val before = server.requests.size

        clock += 10.minutes
        repeat(3) { repository.getAllTablesWithHands("t1", RefreshMode.CACHED) }

        assertEquals(before, server.requests.size)
    }

    @Test
    fun aForcedRankingLoadAsksAgain() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")

        repository.getAllTablesWithHands("t1", RefreshMode.FORCE)

        assertEquals(2, server.count("/tables-with-hands"))
    }

    @Test
    fun tablesThatTheEditorLoadsLaterReachTheRanking() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")
        server.tableVersion = 3
        server.eastPlayer = "6"
        repository.getTableWithHands("t1", 1, 1, RefreshMode.FORCE)

        val tables = assertIs<AppResult.Success<List<Pair<TableState, *>>>>(
            repository.getAllTablesWithHands("t1", RefreshMode.CACHED),
        ).value

        assertEquals("6", tables.single().first.playerEastId)
    }

    @Test
    fun anIfChangedLoadChecksTheManifestAndKeepsTablesThatDidNotChange() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")

        repository.getAllTablesWithHands("t1", RefreshMode.IF_CHANGED)

        assertEquals(1, server.count("/tables-with-hands"))
    }

    @Test
    fun anIfChangedLoadFetchesAgainWhenSomeoneElseSavedATable() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.getAllTablesWithHands("t1")
        server.tableVersions["1_1"] = 5
        server.tableVersion = 5
        server.eastPlayer = "7"
        clock += 3.seconds

        val tables = assertIs<AppResult.Success<List<Pair<TableState, *>>>>(
            repository.getAllTablesWithHands("t1", RefreshMode.IF_CHANGED),
        ).value

        assertEquals("7", tables.single().first.playerEastId)
        assertEquals(2, server.count("/tables-with-hands"))
    }

    @Test
    fun aCachedLoadNeverChecksTheManifest() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")
        val before = server.count("/sync/manifest")

        repeat(3) { repository.getAllTablesWithHands("t1", RefreshMode.CACHED) }

        assertEquals(before, server.count("/sync/manifest"))
    }

    // ---- polling tells the cached lists that they are old

    private suspend fun DefaultTournamentRepository.players(mode: RefreshMode = RefreshMode.CACHED) =
        listTournamentPlayers("t1", mode)

    @Test
    fun cachedListsStayWhenPollingSeesNoChange() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.players()
        repository.getTournamentManifest("t1")
        repository.getTournamentManifest("t1")

        repository.players()

        assertEquals(1, server.count("/players"))
    }

    @Test
    fun aHigherPlayersRevisionMakesTheCachedPlayersOld() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.players()
        repository.getTournamentManifest("t1") // first poll: only records the revisions

        server.playersRevision = 2
        repository.getTournamentManifest("t1")
        repository.players()

        assertEquals(2, server.count("/players"))
    }

    @Test
    fun theFirstPollOnlyRecordsAndNeverMarksAnythingOld() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.players()

        server.playersRevision = 5
        repository.getTournamentManifest("t1")
        repository.players()

        assertEquals(1, server.count("/players"))
    }

    @Test
    fun aChangeOfThePlayersDoesNotMakeTheTeamsOldAndTheOtherWayAround() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.listTournamentTeams("t1", RefreshMode.CACHED)
        repository.getTournamentManifest("t1")

        server.playersRevision = 2
        repository.getTournamentManifest("t1")
        repository.listTournamentTeams("t1", RefreshMode.CACHED)

        assertEquals(1, server.count("/teams"))

        server.teamsRevision = 2
        repository.getTournamentManifest("t1")
        repository.listTournamentTeams("t1", RefreshMode.CACHED)
        assertEquals(2, server.count("/teams"))
    }

    @Test
    fun theRankingTablesStayInTheStoreWhileTheOldPlayersReload() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.getAllTablesWithHands("t1")
        repository.players()
        repository.getTournamentManifest("t1")

        server.playersRevision = 2
        repository.getTournamentManifest("t1")
        repository.players()
        repository.getAllTablesWithHands("t1")

        assertEquals(1, server.count("/tables-with-hands"))
        assertEquals(2, server.count("/players"))
    }

    @Test
    fun aLowerOrEqualRevisionNeverMarksAnythingOld() = runTest {
        val server = FakeTournamentServer().apply { playersRevision = 5 }
        val repository = repository(server)
        repository.players()
        repository.getTournamentManifest("t1")

        server.playersRevision = 4
        repository.getTournamentManifest("t1")
        repository.players()

        assertEquals(1, server.count("/players"))
    }

    // ---- everything that can change is polled

    private suspend fun DefaultTournamentRepository.idList() = generateTournamentIdList("t1")

    @Test
    fun aGeneratedFileIsReusedForeverWhileNothingChanged() = runTest {
        val clock = TestTimeSource()
        val server = FakeTournamentServer()
        val repository = repository(server, clock)
        repository.idList()
        repository.getTournamentManifest("t1")
        val before = server.requests.size

        clock += 10.minutes
        repeat(3) { repository.idList() }
        repository.getTournamentManifest("t1")
        repository.idList()

        assertEquals(1, server.count("/id-list"))
        assertEquals(before + 1, server.requests.size - 0, "only the second poll made a request")
    }

    @Test
    fun aGeneratedFileIsMadeAgainAfterPollingSeesNewPlayers() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.idList()
        repository.getTournamentManifest("t1")

        server.playersRevision = 2
        repository.getTournamentManifest("t1")
        repository.idList()

        assertEquals(2, server.count("/id-list"))
    }

    @Test
    fun aGeneratedFileIsMadeAgainAfterTheEmaPlayersChanged() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.idList()
        repository.getTournamentManifest("t1")

        server.emaPlayersRevision = 2
        repository.getTournamentManifest("t1")
        repository.idList()

        assertEquals(2, server.count("/id-list"))
    }

    @Test
    fun aGeneratedFileIsMadeAgainAfterTheTournamentSettingsChanged() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.idList()
        repository.getTournamentManifest("t1")

        server.tournamentsRevision = 2
        repository.getTournamentManifest("t1")
        repository.idList()

        assertEquals(2, server.count("/id-list"))
    }

    @Test
    fun theEmaReportFileDependsOnTheRankingsAndOnPolling() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        val rankings = emptyList<com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking>()
        repository.generateEmaReport("t1", rankings)
        repository.generateEmaReport("t1", rankings)
        assertEquals(1, server.count("/ema-report"), "same rankings, same file, no request")

        repository.getTournamentManifest("t1")
        server.emaPlayersRevision = 2
        repository.getTournamentManifest("t1")
        repository.generateEmaReport("t1", rankings)

        assertEquals(2, server.count("/ema-report"))
    }

    @Test
    fun theTournamentListIsMarkedOldWhenTheTournamentsChanged() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.CACHED)
        repository.getTournamentManifest("t1")

        repository.listTournaments(RefreshMode.CACHED)
        assertEquals(1, server.requests.count { it == "GET /tournaments" })

        server.tournamentsRevision = 2
        repository.getTournamentManifest("t1")
        repository.listTournaments(RefreshMode.CACHED)
        assertEquals(2, server.requests.count { it == "GET /tournaments" })
    }

    @Test
    fun theCountriesAreMarkedOldWhenTheyChanged() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.listCountries(RefreshMode.CACHED)
        repository.getTournamentManifest("t1")

        server.countriesRevision = 2
        repository.getTournamentManifest("t1")
        repository.listCountries(RefreshMode.CACHED)

        assertEquals(2, server.requests.count { it == "GET /countries" })
    }

    @Test
    fun theFirstPollOfTheSharedRevisionsOnlyRecordsThem() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.CACHED)

        server.tournamentsRevision = 9
        repository.getTournamentManifest("t1")
        repository.listTournaments(RefreshMode.CACHED)

        assertEquals(1, server.requests.count { it == "GET /tournaments" })
    }

    @Test
    fun theSharedRevisionsAreRememberedForAllTournaments() = runTest {
        val server = FakeTournamentServer()
        val repository = repository(server)
        repository.listTournaments(RefreshMode.CACHED)
        repository.getTournamentManifest("t1") // records the shared revisions

        server.tournamentsRevision = 2
        repository.getTournamentManifest("t2") // another tournament sees the change

        repository.listTournaments(RefreshMode.CACHED)
        assertEquals(2, server.requests.count { it == "GET /tournaments" })
    }
}
