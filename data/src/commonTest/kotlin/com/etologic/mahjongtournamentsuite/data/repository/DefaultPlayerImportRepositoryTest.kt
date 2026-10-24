package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowPlan
import com.etologic.mahjongtournamentsuite.domain.model.ImportRowStatus
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultPlayerImportRepositoryTest {
    private val resultJson = """{"assigned":0,"emailsSaved":0,"teamsRenamed":0,"skipped":0,"failures":[]}"""

    private fun row(line: Int, status: ImportRowStatus) = ImportRowPlan(
        row = line, slotId = line, emaId = "0000000$line", email = null, team = null,
        status = status, issues = emptyList(), current = null,
    )

    private fun preview(rows: List<ImportRowPlan>) = PlayerImportPreview(
        rows = rows, untouchedSlotIds = emptyList(), isTeams = false,
        tournamentStarted = false, hasEmailColumn = false, hasTeamColumn = false,
    )

    private fun repository(cache: RepositoryCache, bodies: MutableList<String>, paths: MutableList<String>) =
        DefaultPlayerImportRepository(
            backendApi = mockBackendApi { request ->
                paths += request.url.encodedPath
                bodies += (request.body as io.ktor.http.content.TextContent).text
                jsonResponse(if (request.url.encodedPath.endsWith("/preview")) """{"rows":[]}""" else resultJson)
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("DefaultPlayerImportRepositoryTest"),
            cache = cache,
        )

    @Test
    fun previewMapsRowsIssuesAndUnknownCodes() = runTest {
        val repository = DefaultPlayerImportRepository(
            backendApi = mockBackendApi {
                jsonResponse(
                    """
                    {"rows":[
                      {"row":2,"slotId":1,"emaId":"00000001","status":"REPLACE",
                       "issues":[{"code":"EMAIL_CHANGES","severity":"WARNING","args":["a@b.es","c@b.es"]}],
                       "current":{"emaId":"00000002","email":"a@b.es"}},
                      {"row":3,"status":"ERROR","issues":[{"code":"FROM_THE_FUTURE","severity":"ERROR"}]}
                    ],"untouchedSlotIds":[4],"isTeams":true,"tournamentStarted":true}
                    """.trimIndent(),
                )
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("test"),
            cache = RecordingCache(),
        )

        val preview = assertIs<AppResult.Success<PlayerImportPreview>>(repository.preview("t1", "players.csv", byteArrayOf(1))).value

        assertEquals(listOf(ImportRowStatus.REPLACE, ImportRowStatus.ERROR), preview.rows.map { it.status })
        assertEquals(ImportIssueCode.EMAIL_CHANGES, preview.rows[0].issues[0].code)
        assertEquals(ImportIssueSeverity.WARNING, preview.rows[0].issues[0].severity)
        assertEquals(listOf("a@b.es", "c@b.es"), preview.rows[0].issues[0].args)
        assertEquals("00000002", preview.rows[0].current?.emaId)
        assertEquals(ImportIssueCode.UNKNOWN, preview.rows[1].issues[0].code)
        assertEquals(listOf(4), preview.untouchedSlotIds)
        assertTrue(preview.isTeams && preview.tournamentStarted)
    }

    @Test
    fun applyRunsClearThenBatchesOf25ThenFinish() = runTest {
        val bodies = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val cache = RecordingCache()
        val rows = (1..60).map { row(it, ImportRowStatus.NEW) } + row(61, ImportRowStatus.ERROR)
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = repository(cache, bodies, paths)
            .apply("t1", "players.csv", byteArrayOf(1), preview(rows), replaceExisting = false) { done, total ->
                progress += done to total
            }

        assertIs<AppResult.Success<PlayerImportResult>>(result)
        assertTrue(paths.all { it.endsWith("/tournaments/t1/player-import/apply") })
        val phases = bodies.map { Regex("\"phase\":\"(\\w+)\"").find(it)!!.groupValues[1] }
        assertEquals(listOf("CLEAR", "ASSIGN", "ASSIGN", "ASSIGN", "FINISH"), phases)
        assertTrue(bodies[1].contains("\"rows\":[1,2,3"))
        assertEquals(0 to 60, progress.first())
        assertEquals(60 to 60, progress.last())
        assertEquals(listOf("tournament:t1:players", "tournament:t1:teams", "global:tournaments"), cache.stale)
    }

    @Test
    fun applySkipsReplaceRowsUntilTheEditorAcceptsThem() = runTest {
        val bodies = mutableListOf<String>()
        val rows = listOf(row(1, ImportRowStatus.NEW), row(2, ImportRowStatus.REPLACE), row(3, ImportRowStatus.UNCHANGED))

        repository(RecordingCache(), bodies, mutableListOf())
            .apply("t1", "p.csv", byteArrayOf(1), preview(rows), replaceExisting = false)
        assertTrue(bodies[1].contains("\"rows\":[1]"))

        bodies.clear()
        repository(RecordingCache(), bodies, mutableListOf())
            .apply("t1", "p.csv", byteArrayOf(1), preview(rows), replaceExisting = true)
        assertTrue(bodies[1].contains("\"rows\":[1,2]"))
    }

    @Test
    fun applySumsAssignedAndKeepsFailuresOfEveryStep() = runTest {
        var call = 0
        val repository = DefaultPlayerImportRepository(
            backendApi = mockBackendApi {
                call++
                jsonResponse(
                    when (call) {
                        2 -> """{"assigned":2,"emailsSaved":0,"teamsRenamed":0,"skipped":0,"failures":[{"row":2,"slotId":1,"message":"boom"}]}"""
                        3 -> """{"assigned":1,"emailsSaved":0,"teamsRenamed":0,"skipped":0,"failures":[]}"""
                        4 -> """{"assigned":0,"emailsSaved":3,"teamsRenamed":2,"skipped":4,"failures":[]}"""
                        else -> resultJson
                    },
                )
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("test"),
            cache = RecordingCache(),
        )
        val rows = (1..30).map { row(it, ImportRowStatus.NEW) }

        val value = assertIs<AppResult.Success<PlayerImportResult>>(
            repository.apply("t1", "p.csv", byteArrayOf(1), preview(rows), replaceExisting = false),
        ).value

        assertEquals(3, value.assigned)
        assertEquals(3, value.emailsSaved)
        assertEquals(2, value.teamsRenamed)
        assertEquals(4, value.skipped)
        assertEquals("boom", value.failures.single().message)
    }

    @Test
    fun applyReturnsFailureAndStillInvalidatesTheCacheWhenAStepFails() = runTest {
        var call = 0
        val cache = RecordingCache()
        val repository = DefaultPlayerImportRepository(
            backendApi = mockBackendApi {
                call++
                if (call == 2) respond("{}", HttpStatusCode.InternalServerError) else jsonResponse(resultJson)
            },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("test"),
            cache = cache,
        )

        val result = repository.apply("t1", "p.csv", byteArrayOf(1), preview(listOf(row(1, ImportRowStatus.NEW))), false)

        assertIs<AppResult.Failure>(result)
        assertEquals(3, cache.stale.size)
    }
}

private class RecordingCache : RepositoryCache {
    val stale = mutableListOf<String>()

    override suspend fun <T : Any> getOrLoad(
        ownerUid: String,
        key: String,
        refreshMode: RefreshMode,
        revision: suspend () -> Long,
        load: suspend () -> T,
    ): T = load()

    override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()

    override suspend fun markStale(vararg keyPrefixes: String) {
        stale += keyPrefixes
    }

    override suspend fun clear() = Unit
}
