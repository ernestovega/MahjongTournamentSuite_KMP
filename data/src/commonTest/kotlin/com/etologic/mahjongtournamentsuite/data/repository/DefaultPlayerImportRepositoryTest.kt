package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ImportFileErrorCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportField
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueCode
import com.etologic.mahjongtournamentsuite.domain.model.ImportRemovedPlayer
import com.etologic.mahjongtournamentsuite.domain.model.field
import com.etologic.mahjongtournamentsuite.domain.model.ImportIssueSeverity
import com.etologic.mahjongtournamentsuite.domain.model.PlayerExportFile
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportPreview
import com.etologic.mahjongtournamentsuite.domain.model.PlayerImportResult
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultPlayerImportRepositoryTest {
    private fun repository(
        cache: RepositoryCache = RecordingCache(),
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) =
        DefaultPlayerImportRepository(
            backendApi = mockBackendApi { handler(it) },
            authRepository = FakeAuthRepository(),
            logger = Logger.withTag("DefaultPlayerImportRepositoryTest"),
            cache = cache,
        )

    @Test
    fun previewMapsRowsIssuesCountsAndUnknownCodes() = runTest {
        val repository = repository {
            jsonResponse(
                """
                {"rows":[
                  {"row":2,"slotId":1,"emaId":"00000001","email":"a@b.es",
                   "issues":[{"code":"DUPLICATE_EMAIL","severity":"ERROR","args":["a@b.es"]}]},
                  {"row":3,"slotId":2,"emaId":"00000002","issues":[{"code":"TEAM_IGNORED","severity":"WARNING"}]},
                  {"row":4,"issues":[{"code":"FROM_THE_FUTURE","severity":"ERROR"}]},
                  {"row":5,"slotId":3,"emaId":"00000003"}
                ],"valid":false,"isTeams":true,"tournamentStarted":false,"existingPlayers":7,"importedPlayers":3,
                  "hasEmailColumn":true,"hasTeamColumn":true}
                """.trimIndent(),
            )
        }

        val preview = assertIs<AppResult.Success<PlayerImportPreview>>(repository.preview("t1", "players.csv", byteArrayOf(1))).value

        assertEquals(ImportIssueCode.DUPLICATE_EMAIL, preview.rows[0].issues[0].code)
        assertEquals(ImportIssueSeverity.ERROR, preview.rows[0].issues[0].severity)
        assertEquals(listOf("a@b.es"), preview.rows[0].issues[0].args)
        assertEquals(ImportIssueSeverity.WARNING, preview.rows[1].issues[0].severity)
        assertEquals(ImportIssueCode.UNKNOWN, preview.rows[2].issues[0].code)
        assertEquals(listOf(true, false, true, false), preview.rows.map { it.hasError })
        assertEquals(2, preview.errorCount)
        assertEquals(7, preview.existingPlayers)
        assertEquals(3, preview.importedPlayers)
        assertTrue(preview.isTeams && preview.hasEmailColumn && preview.hasTeamColumn)
        assertFalse(preview.canApply)
    }

    @Test
    fun previewMapsTheChangedDataAndTheRemovedPlayers() = runTest {
        val repository = repository {
            jsonResponse(
                """
                {"rows":[
                  {"row":2,"slotId":1,"emaId":"00000001","changes":["PLAYER","EMAIL"]},
                  {"row":3,"slotId":2,"emaId":"00000002","changes":["TEAM","FROM_THE_FUTURE"]},
                  {"row":4,"slotId":3,"emaId":"00000003"}
                ],"removed":[{"slotId":5,"emaId":"00000009"}],"valid":true}
                """.trimIndent(),
            )
        }

        val preview = assertIs<AppResult.Success<PlayerImportPreview>>(repository.preview("t1", "p.csv", byteArrayOf(1))).value

        assertEquals(setOf(ImportField.PLAYER, ImportField.EMAIL), preview.rows[0].changes)
        assertEquals(setOf(ImportField.TEAM), preview.rows[1].changes)
        assertEquals(emptySet(), preview.rows[2].changes)
        assertEquals(listOf(ImportRemovedPlayer(5, "00000009")), preview.removed)
    }

    @Test
    fun everyIssueCodeBelongsToOneCell() {
        val byField = ImportIssueCode.entries.groupBy { it.field }
        assertEquals(
            setOf(ImportIssueCode.INVALID_ID, ImportIssueCode.UNKNOWN_SLOT, ImportIssueCode.DUPLICATE_SLOT),
            byField.getValue(ImportField.ID).toSet(),
        )
        assertEquals(true, ImportIssueCode.DUPLICATE_EMAIL.field == ImportField.EMAIL && ImportIssueCode.EMAIL_OWNED_BY_OTHER_ACCOUNT.field == ImportField.EMAIL)
        assertEquals(ImportField.TEAM, ImportIssueCode.TEAM_IGNORED.field)
        assertEquals(ImportField.PLAYER, ImportIssueCode.UNKNOWN_EMA_ID.field)
    }

    @Test
    fun aValidFileOfAnotherTournamentCanApplyOnlyWhenNoTableHasData() = runTest {
        val valid = repository { jsonResponse("""{"rows":[],"valid":true,"tournamentStarted":false}""") }
        val started = repository { jsonResponse("""{"rows":[],"valid":true,"tournamentStarted":true}""") }

        assertTrue(assertIs<AppResult.Success<PlayerImportPreview>>(valid.preview("t1", "p.csv", byteArrayOf(1))).value.canApply)
        assertFalse(assertIs<AppResult.Success<PlayerImportPreview>>(started.preview("t1", "p.csv", byteArrayOf(1))).value.canApply)
    }

    @Test
    fun previewSendsTheFileAndChangesNothingInTheCache() = runTest {
        var request: HttpRequestData? = null
        val cache = RecordingCache()
        val repository = repository(cache) {
            request = it
            jsonResponse("""{"rows":[]}""")
        }

        repository.preview("t1", "players.csv", byteArrayOf(1, 2, 3))

        assertTrue(request?.url?.encodedPath?.endsWith("/tournaments/t1/player-import/preview") == true)
        val body = (request?.body as TextContent).text
        assertTrue(body.contains("\"fileName\":\"players.csv\"") && body.contains("\"dataBase64\":\"AQID\""))
        assertFalse(body.contains("phase") || body.contains("replaceExisting"))
        assertTrue(cache.stale.isEmpty())
    }

    @Test
    fun applyMakesOneRequestMapsTheResultAndMarksPlayersTeamsAndTournamentsStale() = runTest {
        val paths = mutableListOf<String>()
        val cache = RecordingCache()
        val repository = repository(cache) {
            paths += it.url.encodedPath
            jsonResponse("""{"players":12,"changedSlots":9,"emptiedSlots":3}""")
        }

        val result = assertIs<AppResult.Success<PlayerImportResult>>(repository.apply("t1", "players.csv", byteArrayOf(1))).value

        assertEquals(1, paths.size)
        assertTrue(paths.single().endsWith("/tournaments/t1/player-import/apply"))
        assertEquals(PlayerImportResult(players = 12, changedSlots = 9, emptiedSlots = 3), result)
        assertEquals(listOf("tournament:t1:players", "tournament:t1:teams", "global:tournaments"), cache.stale)
    }

    @Test
    fun applyReturnsFailureAndStillMarksTheCacheStaleWhenTheServerFails() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache) { respond("{}", HttpStatusCode.InternalServerError) }

        assertIs<AppResult.Failure>(repository.apply("t1", "players.csv", byteArrayOf(1)))

        assertEquals(listOf("tournament:t1:players", "tournament:t1:teams", "global:tournaments"), cache.stale)
    }

    @Test
    fun theServerRefusalOfAStartedTournamentAndOfAFileWithErrorsBecomeFileErrors() = runTest {
        suspend fun codeOf(body: String): ImportFileErrorCode {
            val repository = repository { respond(body, HttpStatusCode.Conflict, io.ktor.http.headersOf("Content-Type", "application/json")) }
            val failure = assertIs<AppResult.Failure>(repository.apply("t1", "p.csv", byteArrayOf(1)))
            return assertIs<AppError.ImportFile>(failure.error).code
        }

        assertEquals(
            ImportFileErrorCode.TOURNAMENT_STARTED,
            codeOf("""{"error":{"message":"x","details":{"code":"TOURNAMENT_STARTED"}},"details":{"code":"TOURNAMENT_STARTED"}}"""),
        )
        assertEquals(
            ImportFileErrorCode.FILE_HAS_ERRORS,
            codeOf("""{"error":{"message":"x","details":{"code":"FILE_HAS_ERRORS"}},"details":{"code":"FILE_HAS_ERRORS"}}"""),
        )
    }

    @Test
    fun aMissingColumnsErrorKeepsTheColumnNames() = runTest {
        val repository = repository {
            respond(
                """{"details":{"code":"MISSING_COLUMNS","missing":["id","ema id"]}}""",
                HttpStatusCode.BadRequest,
                io.ktor.http.headersOf("Content-Type", "application/json"),
            )
        }

        val failure = assertIs<AppResult.Failure>(repository.preview("t1", "p.csv", byteArrayOf(1)))

        val error = assertIs<AppError.ImportFile>(failure.error)
        assertEquals(ImportFileErrorCode.MISSING_COLUMNS, error.code)
        assertEquals(listOf("id, ema id"), error.args)
    }

    @Test
    fun exportReadsTheFileFromTheExportRoute() = runTest {
        var method = ""
        var path = ""
        val repository = repository {
            method = it.method.value
            path = it.url.encodedPath
            jsonResponse("""{"fileName":"players.xlsx","dataBase64":"AQID"}""")
        }

        val result = repository.export("t1")

        assertIs<AppResult.Success<PlayerExportFile>>(result)
        assertEquals("GET", method)
        assertTrue(path.endsWith("/tournaments/t1/player-import/export"))
        assertEquals("players.xlsx", result.value.fileName)
        assertEquals(listOf<Byte>(1, 2, 3), result.value.bytes.toList())
    }

    @Test
    fun exportReturnsFailureWhenTheServerFails() = runTest {
        val repository = repository { respond("{}", HttpStatusCode.InternalServerError) }

        assertIs<AppResult.Failure>(repository.export("t1"))
    }

    @Test
    fun exportDoesNotChangeTheCache() = runTest {
        val cache = RecordingCache()
        val repository = repository(cache) { jsonResponse("""{"fileName":"p.xlsx","dataBase64":""}""") }

        repository.export("t1")

        assertTrue(cache.stale.isEmpty())
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
