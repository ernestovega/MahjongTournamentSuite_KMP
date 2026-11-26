package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.SessionRepositoryCache
import com.etologic.mahjongtournamentsuite.data.cache.TournamentTableStore
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.IdCardSheetLayout
import com.etologic.mahjongtournamentsuite.domain.model.PlayerRanking
import com.etologic.mahjongtournamentsuite.domain.model.PlayerSubstitution
import com.etologic.mahjongtournamentsuite.domain.model.SubstitutionReason
import com.etologic.mahjongtournamentsuite.domain.model.TableHand
import com.etologic.mahjongtournamentsuite.domain.model.TableState
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
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
 * The files of a tournament (ID cards and EMA report) come once and stay until their data change. The tables of a
 * round are read with their hands and kept in the table store, and a table save sends its patches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTournamentRepositoryFilesAndTablesTest {
    private val requests = mutableListOf<String>()
    private var refuse = false
    private val tableStore = TournamentTableStore()

    private fun TestScope.repository() = DefaultTournamentRepository(
        backendApi = mockBackendApi { request ->
            val path = request.url.encodedPath.removePrefix("/functions")
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val query = request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
            requests += "${request.method.value} $path$query $body".trim()
            when {
                path.endsWith("/sync/manifest") -> jsonResponse("""{"resources":{"tables":{"revision":2}},"tableVersions":{}}""")
                refuse -> respond(ByteReadChannel("""{"error":"refused"}"""), HttpStatusCode.InternalServerError)
                path.endsWith("/id-cards") || path.endsWith("/ema-report") ->
                    respond(ByteReadChannel("%PDF"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
                path.endsWith("/tables-with-hands") -> jsonResponse("""{"tables":[$TABLE_WITH_HANDS]}""")
                path.endsWith("/tables") -> jsonResponse(
                    """{"tables":[{"roundId":1,"tableId":2,"playerIds":[1,2,3,4],"isCompleted":true,"hasProgress":true,""" +
                        """"completionStatus":"completed","bestHandScores":[24,8],"chickenHandCount":1,"version":5}]}""",
                )
                path.endsWith("/state") || path.endsWith("/reset") -> jsonResponse(TABLE_WITH_HANDS)
                else -> jsonResponse("""{"ok":true}""")
            }
        },
        authRepository = FakeAuthRepository(),
        logger = Logger.withTag("DefaultTournamentRepositoryFilesAndTablesTest"),
        cache = SessionRepositoryCache(
            logger = Logger.withTag("cache"),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            timeSource = TestTimeSource(),
            freshFor = 10.seconds,
        ),
        json = Json { ignoreUnknownKeys = true },
        tableStore = tableStore,
    )

    private fun count(prefix: String) = requests.count { it.startsWith(prefix) }

    @Test
    fun theIdCardsComeOnceForEachLayout() = runTest {
        val repository = repository()
        val sheet = IdCardSheetLayout(cardsPerSheet = 8, landscape = true)

        assertEquals("%PDF", assertIs<AppResult.Success<ByteArray>>(repository.generateTournamentIdCards("t1", sheet)).value.decodeToString())
        repository.generateTournamentIdCards("t1", sheet)
        assertEquals(1, count("GET /tournaments/t1/id-cards"), "the same layout comes from the cache")
        assertTrue(requests.first { it.startsWith("GET /tournaments/t1/id-cards") }.contains("cardsPerSheet=8"))

        repository.generateTournamentIdCards("t1", sheet.copy(cardsPerSheet = 10))
        repository.generateTournamentIdCards("t1", null)
        assertEquals(3, count("GET /tournaments/t1/id-cards"), "another layout is another file")
    }

    @Test
    fun theEmaReportLeavesOutTheExcludedPlayers() = runTest {
        val rankings = listOf(
            PlayerRanking(position = 1, playerId = 7, teamId = 0, points = 4.0, score = 120),
            PlayerRanking(
                position = 2,
                playerId = 8,
                teamId = 0,
                points = 2.0,
                score = 10,
                substitution = PlayerSubstitution(SubstitutionReason.entries.first(), 1, excludedFromEma = true),
            ),
        )

        assertIs<AppResult.Success<ByteArray>>(repository().generateEmaReport("t1", rankings))

        val sent = requests.single { it.startsWith("POST /tournaments/t1/ema-report") }
        assertTrue(""""playerId":7""" in sent && """"playerId":8""" !in sent, sent)
    }

    @Test
    fun aFileThatFailsGivesAFailure() = runTest {
        val repository = repository()
        refuse = true

        assertIs<AppResult.Failure>(repository.generateTournamentIdCards("t1", null))
        assertIs<AppResult.Failure>(repository.generateEmaReport("t1", emptyList()))
    }

    @Test
    fun theTablesOfARoundKeepTheirProgress() = runTest {
        val tables = assertIs<AppResult.Success<List<TournamentTable>>>(repository().listTournamentTables("t1", roundId = 1, RefreshMode.FORCE)).value

        val table = tables.single()
        assertTrue(table.isCompleted && table.hasProgress)
        assertEquals("completed", table.completionStatus)
        assertEquals(listOf(24, 8), table.bestHandScores)
        assertEquals(1, table.chickenHandCount)
        assertEquals(5L, table.version)
    }

    @Test
    fun theTablesWithHandsOfARoundGoToTheTableStore() = runTest {
        val round = assertIs<AppResult.Success<List<Pair<TableState, List<TableHand>>>>>(
            repository().getRoundTablesWithHands("t1", roundId = 1, RefreshMode.FORCE),
        ).value

        assertEquals(2, round.single().first.tableId)
        assertEquals("8", round.single().second.single().handScore)
        assertTrue(requests.any { it.startsWith("GET /tournaments/t1/rounds/1/tables-with-hands") })
    }

    @Test
    fun aTableSaveSendsTheTableAndHandPatches() = runTest {
        val saved = assertIs<AppResult.Success<Pair<TableState, List<TableHand>>>>(
            repository().saveTableState(
                tournamentId = "t1",
                roundId = 1,
                tableId = 2,
                expectedVersion = 3,
                tablePatch = mapOf("isCompleted" to true),
                handPatches = mapOf(1 to mapOf("handScore" to "8", "isDone" to true)),
            ),
        ).value

        assertEquals(4L, saved.first.version)
        val sent = requests.single { it.startsWith("PUT /tournaments/t1/tables/1/2/state") || it.startsWith("POST /tournaments/t1/tables/1/2/state") }
        assertTrue(""""expectedVersion":3""" in sent && """"handId":1""" in sent && """"handScore":"8"""" in sent, sent)
    }

    @Test
    fun aRefusedResetGivesAFailure() = runTest {
        val repository = repository()
        assertIs<AppResult.Success<*>>(repository.resetTable("t1", 1, 2))
        refuse = true

        assertIs<AppResult.Failure>(repository.resetTable("t1", 1, 2))
        assertIs<AppResult.Failure>(repository.getRoundTablesWithHands("t1", roundId = 1, RefreshMode.FORCE))
    }

    private companion object {
        const val TABLE_WITH_HANDS = """{"table":{"version":4,"roundId":1,"tableId":2,"playerIds":[1,2,3,4],"isCompleted":false,""" +
            """"useTotalsOnly":false},"hands":[{"handId":1,"handScore":"8","isDone":true}]}"""
    }
}
