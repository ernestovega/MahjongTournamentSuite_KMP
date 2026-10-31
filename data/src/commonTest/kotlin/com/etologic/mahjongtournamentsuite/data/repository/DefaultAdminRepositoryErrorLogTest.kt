package com.etologic.mahjongtournamentsuite.data.repository

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.etologic.mahjongtournamentsuite.data.backend.jsonResponse
import com.etologic.mahjongtournamentsuite.data.backend.mockBackendApi
import com.etologic.mahjongtournamentsuite.data.cache.RepositoryCache
import com.etologic.mahjongtournamentsuite.data.telemetry.RecordingTelemetry
import com.etologic.mahjongtournamentsuite.data.telemetry.TelemetryLogWriter
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupDetail
import com.etologic.mahjongtournamentsuite.domain.model.ErrorGroupsPage
import com.etologic.mahjongtournamentsuite.domain.model.ErrorLogCursor
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryEvent
import com.etologic.mahjongtournamentsuite.domain.telemetry.TelemetryParam
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The admin app reads the error log one page at a time. These tests check the requests and the mapping. */
class DefaultAdminRepositoryErrorLogTest {
    private class NoCache : RepositoryCache {
        override suspend fun <T : Any> getOrLoad(
            ownerUid: String,
            key: String,
            refreshMode: RefreshMode,
            revision: suspend () -> Long,
            load: suspend () -> T,
        ): T = load()

        override suspend fun <T : Any> shared(key: String, maxAge: kotlin.time.Duration, fetch: suspend () -> T): T = fetch()
        override suspend fun markStale(vararg keyPrefixes: String) = Unit
        override suspend fun clear() = Unit
    }

    private val telemetry = RecordingTelemetry()
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, body: String = "{}") = DefaultAdminRepository(
        backendApi = mockBackendApi { request ->
            requests += request
            if (status == HttpStatusCode.OK) jsonResponse(body) else respond("failed", status)
        },
        authRepository = FakeAuthRepository(),
        logger = Logger(StaticConfig(logWriterList = listOf(TelemetryLogWriter(telemetry))), "DefaultAdminRepositoryErrorLogTest"),
        cache = NoCache(),
    )

    private val group = """
        {"id":"g1","errorType":"IllegalStateException","area":"Saving table","message":"boom","count":7,
         "firstSeen":1000,"lastSeen":5000,"lastKind":"crash","lastApp":"admin","lastPlatform":"web","lastVersion":"1.2.3",
         "lastEnv":"prod","platforms":{"web":5,"android":2},"apps":{"admin":7}}
    """

    @Test
    fun theFirstPageHasNoPeriodAndNoCursorAndIsMapped() = runTest {
        val repository = repository(body = """{"groups":[$group],"nextCursor":{"at":5000,"id":"g1"}}""")

        val page = assertIs<AppResult.Success<ErrorGroupsPage>>(repository.loadErrorGroups(null, null)).value

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/functions/admin/error-groups", request.url.encodedPath)
        assertEquals("200", request.url.parameters["limit"])
        assertNull(request.url.parameters["since"])
        assertNull(request.url.parameters["cursorAt"])
        assertEquals(ErrorLogCursor(5000, "g1"), page.nextCursor)
        val mapped = page.groups.single()
        assertEquals("IllegalStateException", mapped.errorType)
        assertEquals("crash", mapped.lastKind)
        assertEquals(mapOf("web" to 5, "android" to 2), mapped.platforms)
        assertEquals(mapOf("admin" to 7), mapped.apps)
        assertEquals("prod", mapped.lastEnv)
        assertEquals(7, mapped.count)
    }

    @Test
    fun theLastPageHasNoCursor() = runTest {
        val page = assertIs<AppResult.Success<ErrorGroupsPage>>(repository(body = """{"groups":[$group]}""").loadErrorGroups(null, null)).value
        assertNull(page.nextCursor)
    }

    @Test
    fun theNextPageSendsThePeriodAndTheCursorAndEncodesTheId() = runTest {
        repository(body = """{"groups":[]}""").loadErrorGroups(sinceMillis = 123456, cursor = ErrorLogCursor(4000, "a b&c"))

        val parameters = requests.single().url.parameters
        assertEquals("123456", parameters["since"])
        assertEquals("4000", parameters["cursorAt"])
        assertEquals("a b&c", parameters["cursorId"])
    }

    @Test
    fun missingFieldsFallBackToEmptyValues() = runTest {
        val page = assertIs<AppResult.Success<ErrorGroupsPage>>(repository(body = """{"groups":[{"id":"g9"}]}""").loadErrorGroups(null, null)).value
        val mapped = page.groups.single()
        assertEquals("g9", mapped.id)
        assertEquals(0, mapped.count)
        assertEquals(emptyMap(), mapped.platforms)
        assertEquals("", mapped.lastEnv)
    }

    @Test
    fun aDetailHasTheStackTraceTheReportsAndTheNextCursor() = runTest {
        val repository = repository(
            body = """
                {"id":"g1","errorType":"IllegalStateException","stackTrace":"at a.b.C.run(C.kt:1)","count":7,"lastSeen":5000,
                 "reports":[{"reportId":"r1","createdAt":4900,"app":"admin","platform":"web","kind":"crash","env":"prod",
                             "role":"admin","appVersion":"1.2.3","reason":"server_error","httpStatus":500,"message":"boom","language":"en"},
                            {"reportId":"r2","createdAt":4800,"app":"player","platform":"android","kind":"handled","env":"dev"}],
                 "nextReportCursor":{"at":4800,"id":"doc2"}}
            """,
        )

        val detail = assertIs<AppResult.Success<ErrorGroupDetail>>(repository.loadErrorGroup("g1", null)).value

        val request = requests.single()
        assertEquals("/functions/admin/error-groups/g1", request.url.encodedPath)
        assertEquals("50", request.url.parameters["limit"])
        assertNull(request.url.parameters["cursorAt"])
        assertEquals("at a.b.C.run(C.kt:1)", detail.stackTrace)
        assertEquals(ErrorLogCursor(4800, "doc2"), detail.nextReportCursor)
        assertEquals(listOf("r1", "r2"), detail.reports.map { it.reportId })
        val first = detail.reports.first()
        assertEquals(500, first.httpStatus)
        assertEquals("admin", first.role)
        val second = detail.reports.last()
        assertNull(second.role)
        assertNull(second.httpStatus)
        assertEquals("", second.reason)
    }

    @Test
    fun theNextPageOfReportsSendsItsCursor() = runTest {
        repository(body = """{"id":"g1","reports":[]}""").loadErrorGroup("g1", ErrorLogCursor(4800, "doc2"))

        val parameters = requests.single().url.parameters
        assertEquals("4800", parameters["cursorAt"])
        assertEquals("doc2", parameters["cursorId"])
    }

    @Test
    fun aFailedListIsAFailureAndCountsAsAnActionFailedWithTheReason() = runTest {
        val result = repository(status = HttpStatusCode.InternalServerError).loadErrorGroups(null, null)

        assertIs<AppResult.Failure>(result)
        val event = telemetry.events.single()
        assertEquals(TelemetryEvent.ACTION_FAILED, event.first)
        assertEquals("server_error", event.second[TelemetryParam.REASON])
        assertTrue(event.second[TelemetryParam.ACTION].toString().contains("error log"), event.second.toString())
    }

    @Test
    fun aFailedDetailIsAFailureAndIsLogged() = runTest {
        val result = repository(status = HttpStatusCode.NotFound).loadErrorGroup("gone", null)

        assertIs<AppResult.Failure>(result)
        assertEquals("client_error", telemetry.events.single().second[TelemetryParam.REASON])
    }
}
